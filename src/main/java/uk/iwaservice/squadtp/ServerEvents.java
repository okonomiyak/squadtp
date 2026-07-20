package uk.iwaservice.squadtp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import uk.iwaservice.squadtp.block.DummyRegistry;
import uk.iwaservice.squadtp.command.SquadCommand;
import uk.iwaservice.squadtp.network.NetworkHandler;
import uk.iwaservice.squadtp.network.RespawnChoicePacket;
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

/** Forge-bus event handlers: commands, periodic position sync, login sync, rally respawn. */
public final class ServerEvents {

    private static int tickCounter;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SquadCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ReviveSystem.tick(server);
        if (++tickCounter % Config.POS_UPDATE_INTERVAL_TICKS.get() == 0) {
            broadcastPositions(server);
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
    public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof ServerPlayer player) {
            SquadManager.get(player.server).markDamaged(player.getUUID());
        }
    }

    /** Lethal hit on a squad member -> downed state instead of death. */
    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
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
        if (manager.getSquadOf(player.getUUID()) == null) {
            return; // solo players die normally
        }
        event.setCanceled(true);
        ReviveSystem.enterDowned(player);
    }

    /** No jumping while downed (the event is not cancelable, so zero the upward motion). */
    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(0.0, motion.y), motion.z);
        }
    }

    /** Health stays pinned at 1 while downed (except kill-type sources). */
    @SubscribeEvent
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Right-click(-hold) on a downed player channels a revive. */
    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
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
    public static void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot break blocks. */
    @SubscribeEvent
    public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot use blocks (chests, buttons, ...). */
    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot use items (food, pearls, ...). */
    @SubscribeEvent
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player
                && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Downed players cannot drop items (the stack is returned to their inventory). */
    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID())) {
            event.setCanceled(true);
            player.getInventory().add(event.getEntity().getItem());
        }
    }

    /** Downed players cannot swap hands. */
    @SubscribeEvent
    public static void onSwapHands(net.minecraftforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
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
        if (squad == null) {
            return;
        }

        // Automatic rally respawn takes precedence over the chooser.
        if (Config.RALLY_RESPAWN_ENABLED.get() && squad.hasRally() && manager.isEnabled(SquadFeature.RALLY)) {
            ServerLevel targetLevel = player.server.getLevel(squad.getRallyDimension());
            if (targetLevel != null) {
                BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, squad.getRallyPos());
                player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                        java.util.Set.of(), player.getYRot(), player.getXRot());
            }
            return;
        }

        if (!Config.RESPAWN_CHOICE_ENABLED.get() || !manager.isEnabled(SquadFeature.RESPAWN_CHOICE)) {
            return;
        }
        List<RespawnChoicePacket.Entry> targets = new ArrayList<>();
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
        if (!squad.hasRally() && targets.isEmpty()) {
            return;
        }
        manager.markRespawnChoice(player.getUUID());
        NetworkHandler.sendRespawnChoice(player, new RespawnChoicePacket(
                squad.hasRally() ? squad.getRallyDimension().location() : null,
                squad.hasRally() ? squad.getRallyPos() : null,
                targets, Config.RESPAWN_CHOICE_WINDOW_SECONDS.get()));
    }

    private ServerEvents() {}
}
