package uk.iwaservice.squadtp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import uk.iwaservice.squadtp.api.RespawnChoiceEntry;
import uk.iwaservice.squadtp.api.RespawnChoiceProvider;
import uk.iwaservice.squadtp.api.RespawnChoiceRegistry;
import uk.iwaservice.squadtp.block.DummyRegistry;
import uk.iwaservice.squadtp.command.SquadCommand;
import uk.iwaservice.squadtp.network.NetworkHandler;
import uk.iwaservice.squadtp.network.RespawnChoicePacket;
import uk.iwaservice.squadtp.network.SquadListPacket;
import uk.iwaservice.squadtp.network.SquadMemberPosPacket;
import uk.iwaservice.squadtp.squad.ReviveSystem;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadFeature;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtp.squad.TeleportHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NeoForge game-bus event handlers: commands, periodic position sync, login sync, rally respawn. */
public final class ServerEvents {

    private static int tickCounter;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SquadCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ReviveSystem.tick(server);
        if (++tickCounter % Config.POS_UPDATE_INTERVAL_TICKS.get() == 0) {
            broadcastPositions(server);
            broadcastSquadList(server);
        }
    }

    /**
     * Sends every online player the other squads they could request to join (recruit tab), grouped
     * one entry per squad rather than one per player. Sent to everyone, not just squadless players
     * — a squad member can still browse and request a different squad, which switches them over on
     * approval (see {@code SquadCommand.accept}/{@code approve}); their own squad is excluded from
     * their own list. Same interval as position updates — this doesn't need its own config, both
     * are "keep the recruit-adjacent GUI state fresh" ticks.
     */
    private static void broadcastSquadList(MinecraftServer server) {
        SquadManager manager = SquadManager.get(server);
        Map<UUID, Squad> squadsBySeenMember = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Squad squad = manager.getSquadOf(player.getUUID());
            if (squad != null) {
                squadsBySeenMember.putIfAbsent(squad.getId(), squad);
            }
        }
        if (squadsBySeenMember.isEmpty()) {
            return;
        }
        List<Squad> allSquads = new ArrayList<>(squadsBySeenMember.values());

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            String viewerName = viewer.getGameProfile().getName();
            Squad ownSquad = manager.getSquadOf(viewer.getUUID());
            List<SquadListPacket.Entry> entries = new ArrayList<>();
            for (Squad squad : allSquads) {
                if (ownSquad != null && squad.getId().equals(ownSquad.getId())) {
                    continue;
                }
                String leaderName = squad.getMemberName(squad.getLeader());
                if (leaderName == null || !SquadCommand.sameTeam(server, viewerName, leaderName)) {
                    continue;
                }
                entries.add(new SquadListPacket.Entry(leaderName, List.copyOf(squad.getMembers().values())));
            }
            NetworkHandler.sendSquadList(viewer, new SquadListPacket(entries));
        }
    }

    /** Groups online players by squad and sends each squad's member positions to its members. */
    private static void broadcastPositions(MinecraftServer server) {
        SquadManager manager = SquadManager.get(server);

        Map<UUID, List<ServerPlayer>> onlineBySquad = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Squad squad = manager.getSquadOf(player.getUUID());
            if (squad != null && squad.size() >= 2) {
                onlineBySquad.computeIfAbsent(squad.getId(), id -> new ArrayList<>()).add(player);
            }
        }

        // Feature switched off: keep clients cleared instead of leaving stale positions.
        if (!manager.isEnabled(SquadFeature.POSITION_SHARING)) {
            SquadMemberPosPacket emptyPacket = new SquadMemberPosPacket(List.of());
            for (List<ServerPlayer> members : onlineBySquad.values()) {
                for (ServerPlayer member : members) {
                    NetworkHandler.sendPositions(member, emptyPacket);
                }
            }
            return;
        }

        for (Map.Entry<UUID, List<ServerPlayer>> squadEntry : onlineBySquad.entrySet()) {
            List<ServerPlayer> members = squadEntry.getValue();
            List<SquadMemberPosPacket.Entry> entries = new ArrayList<>(members.size());
            for (ServerPlayer member : members) {
                entries.add(new SquadMemberPosPacket.Entry(
                        member.getUUID(),
                        member.getGameProfile().getName(),
                        member.level().dimension().location(),
                        member.blockPosition()));
            }
            // Loaded test dummy blocks report their block position like an online member.
            Squad squad = manager.getSquad(squadEntry.getKey());
            if (squad != null) {
                for (UUID memberId : squad.getMembers().keySet()) {
                    if (manager.isDummy(memberId)) {
                        DummyRegistry.Entry dummy = DummyRegistry.get(memberId);
                        if (dummy != null) {
                            entries.add(new SquadMemberPosPacket.Entry(
                                    memberId, dummy.name(), dummy.dimension().location(), dummy.pos().above()));
                        }
                    }
                }
            }
            SquadMemberPosPacket packet = new SquadMemberPosPacket(entries);
            for (ServerPlayer member : members) {
                NetworkHandler.sendPositions(member, packet);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DummyRegistry.clear();
        ReviveSystem.clear();
    }

    @SubscribeEvent
    public static void onLivingDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player) {
            SquadManager.get(player.server).markDamaged(player.getUUID());
        }
    }

    /** Lethal hit on any player -> downed state instead of death (squad or not; see AED). */
    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Kill-type sources (timeout kill, /kill, void) always go through; clean up state.
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || ReviveSystem.isDowned(player.getUUID())) {
            ReviveSystem.onPlayerDeath(player);
            return;
        }
        SquadManager manager = SquadManager.get(player.server);
        if (!manager.isEnabled(SquadFeature.REVIVE)) {
            return;
        }
        event.setCanceled(true);
        ReviveSystem.enterDowned(player);
    }

    /** No jumping while downed (the event is not cancelable, so zero the upward motion). */
    @SubscribeEvent
    public static void onLivingJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(0.0, motion.y), motion.z);
        }
    }

    /** Health stays pinned at 1 while downed (except kill-type sources). */
    @SubscribeEvent
    public static void onLivingHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Right-click(-hold) on a downed player channels a revive. */
    @SubscribeEvent
    public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer reviver)) {
            return;
        }
        // Downed players cannot interact with entities at all.
        if (ReviveSystem.isDowned(reviver.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target) || !ReviveSystem.isDowned(target.getUUID())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        ReviveSystem.handleInteract(reviver, target);
    }

    /** Downed players cannot attack (server-authoritative left-click block). */
    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot break blocks. */
    @SubscribeEvent
    public static void onLeftClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot use blocks (chests, buttons, ...). */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot use items (food, pearls, ...). */
    @SubscribeEvent
    public static void onRightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot drop items (the stack is returned to their inventory). */
    @SubscribeEvent
    public static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
            player.getInventory().add(event.getEntity().getItem());
        }
    }

    /** Downed players cannot swap hands. */
    @SubscribeEvent
    public static void onSwapHands(net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ReviveSystem.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SquadManager manager = SquadManager.get(player.server);
        manager.updateName(player);
        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad != null) {
            NetworkHandler.sendSquadSync(player, squad);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SquadManager manager = SquadManager.get(player.server);
        Squad squad = manager.getSquadOf(player.getUUID());

        // Automatic rally respawn takes precedence over the chooser. Gated by RESPAWN_CHOICE (the
        // spawn-time switch) rather than RALLY (the anytime /squad rally switch), so admins can
        // disable "teleport on respawn" without also disabling walking up to the rally point anytime.
        if (squad != null && Config.RALLY_RESPAWN_ENABLED.get() && squad.hasRally()
                && manager.isEnabled(SquadFeature.RESPAWN_CHOICE)) {
            ServerLevel targetLevel = player.server.getLevel(squad.getRallyDimension());
            if (targetLevel != null) {
                BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, squad.getRallyPos());
                player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                        java.util.Set.of(), player.getYRot(), player.getXRot());
            }
            return;
        }

        boolean choiceEnabled = Config.RESPAWN_CHOICE_ENABLED.get() && manager.isEnabled(SquadFeature.RESPAWN_CHOICE);
        List<RespawnChoicePacket.Entry> targets = new ArrayList<>();
        boolean hasRally = false;
        boolean hasBeacon = false;
        if (squad != null && choiceEnabled) {
            hasRally = squad.hasRally();
            hasBeacon = squad.hasBeacon();
            for (UUID member : squad.getMembers().keySet()) {
                if (member.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer online = player.server.getPlayerList().getPlayer(member);
                if (online != null) {
                    if (ReviveSystem.isDowned(member)) {
                        continue; // downed members are not valid spawn targets
                    }
                    targets.add(new RespawnChoicePacket.Entry(member, online.getGameProfile().getName(),
                            online.level().dimension().location(), online.blockPosition()));
                } else if (manager.isDummy(member)) {
                    DummyRegistry.Entry dummy = DummyRegistry.get(member);
                    if (dummy != null) {
                        targets.add(new RespawnChoicePacket.Entry(member, dummy.name(),
                                dummy.dimension().location(), dummy.pos().above()));
                    }
                }
            }
        }

        // Third-party choices (see RespawnChoiceProvider) are independent of squadtp's own
        // RESPAWN_CHOICE feature toggle and don't require the player to be in a squad.
        List<RespawnChoicePacket.ExternalEntry> external = new ArrayList<>();
        for (RespawnChoiceProvider provider : RespawnChoiceRegistry.providers()) {
            for (RespawnChoiceEntry entry : provider.getChoices(player)) {
                external.add(new RespawnChoicePacket.ExternalEntry(provider.id(), entry.choiceId(), entry.label(),
                        entry.dimension(), entry.pos()));
            }
        }

        if (!hasRally && !hasBeacon && targets.isEmpty() && external.isEmpty()) {
            return;
        }
        manager.markRespawnChoice(player.getUUID());
        NetworkHandler.sendRespawnChoice(player, new RespawnChoicePacket(
                hasRally ? squad.getRallyDimension().location() : null,
                hasRally ? squad.getRallyPos() : null,
                targets, Config.RESPAWN_CHOICE_WINDOW_SECONDS.get(),
                hasBeacon ? squad.getBeaconDimension().location() : null,
                hasBeacon ? squad.getBeaconPos() : null,
                hasBeacon ? squad.getBeaconUsesRemaining() : 0,
                external));
    }

    private ServerEvents() {}
}
