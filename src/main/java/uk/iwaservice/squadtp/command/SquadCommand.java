package uk.iwaservice.squadtp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import uk.iwaservice.squadtp.Config;
import uk.iwaservice.squadtp.block.DummyRegistry;
import uk.iwaservice.squadtp.network.NetworkHandler;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadFeature;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtp.squad.TeleportHelper;

import java.util.UUID;

/**
 * /squad command tree. All squad operations enter the server exclusively
 * through here, so permission checks below are the single line of defense.
 */
public final class SquadCommand {

    private static final SuggestionProvider<CommandSourceStack> SQUAD_MEMBER_NAMES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            Squad squad = SquadManager.get(ctx.getSource().getServer()).getSquadOf(player.getUUID());
            if (squad != null) {
                return SharedSuggestionProvider.suggest(squad.getMembers().values(), builder);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> JOIN_REQUEST_NAMES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            Squad squad = SquadManager.get(ctx.getSource().getServer()).getSquadOf(player.getUUID());
            if (squad != null) {
                return SharedSuggestionProvider.suggest(squad.getJoinRequestNames(), builder);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("squad")
                .then(Commands.literal("create").executes(ctx -> create(ctx)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> invite(ctx))))
                .then(Commands.literal("accept").executes(ctx -> accept(ctx)))
                .then(Commands.literal("deny").executes(ctx -> deny(ctx)))
                .then(Commands.literal("join")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> requestJoin(ctx))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(JOIN_REQUEST_NAMES).executes(ctx -> approve(ctx))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(JOIN_REQUEST_NAMES).executes(ctx -> reject(ctx))))
                .then(Commands.literal("setjoin")
                        .then(Commands.literal("open").executes(ctx -> setJoinPolicy(ctx, true)))
                        .then(Commands.literal("invite").executes(ctx -> setJoinPolicy(ctx, false))))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("member", StringArgumentType.word())
                                .suggests(SQUAD_MEMBER_NAMES).executes(ctx -> kick(ctx))))
                .then(Commands.literal("promote")
                        .then(Commands.argument("member", StringArgumentType.word())
                                .suggests(SQUAD_MEMBER_NAMES).executes(ctx -> promote(ctx))))
                .then(Commands.literal("disband").executes(ctx -> disband(ctx)))
                .then(Commands.literal("info").executes(ctx -> info(ctx)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("member", StringArgumentType.word())
                                .suggests(SQUAD_MEMBER_NAMES).executes(ctx -> teleport(ctx))))
                .then(Commands.literal("setrally").executes(ctx -> setRally(ctx)))
                .then(Commands.literal("rally").executes(ctx -> rally(ctx)))
                .then(Commands.literal("beacon").executes(ctx -> beacon(ctx)))
                .then(Commands.literal("respawn")
                        .then(Commands.literal("rally").executes(ctx -> respawnRally(ctx)))
                        .then(Commands.literal("beacon").executes(ctx -> respawnBeacon(ctx)))
                        .then(Commands.literal("member")
                                .then(Commands.argument("member", StringArgumentType.word())
                                        .suggests(SQUAD_MEMBER_NAMES).executes(ctx -> respawnMember(ctx)))))
                .then(Commands.literal("giveup").executes(ctx -> giveUp(ctx)))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> adminList(ctx))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("feature", StringArgumentType.word())
                                        .suggests(FEATURE_KEYS).executes(ctx -> adminSet(ctx, true))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("feature", StringArgumentType.word())
                                        .suggests(FEATURE_KEYS).executes(ctx -> adminSet(ctx, false))))
                        .then(Commands.literal("revivetime")
                                .then(Commands.literal("reset").executes(ctx -> adminResetReviveTime(ctx)))
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.5, 60.0))
                                        .executes(ctx -> adminSetReviveTime(ctx))))));
    }

    /** Downed player gives up waiting and dies immediately. Only valid while downed. */
    private static int giveUp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(player.getUUID())) {
            return fail(ctx, "squadtp.msg.not_downed");
        }
        uk.iwaservice.squadtp.squad.ReviveSystem.forceDeath(player);
        return 1;
    }

    // --- admin feature switches ---

    private static final SuggestionProvider<CommandSourceStack> FEATURE_KEYS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(SquadFeature.keys(), builder);

    private static int adminList(CommandContext<CommandSourceStack> ctx) {
        SquadManager manager = manager(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.admin_header"), false);
        for (SquadFeature feature : SquadFeature.values()) {
            boolean enabled = manager.isEnabled(feature);
            MutableComponent line = Component.literal(" - " + feature.key() + " ")
                    .append(Component.translatable(feature.translationKey()).withStyle(ChatFormatting.GRAY))
                    .append(" : ")
                    .append(Component.translatable(enabled ? "squadtp.msg.admin_on" : "squadtp.msg.admin_off")
                            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        boolean overridden = manager.getReviveCastSecondsOverride() != null;
        MutableComponent reviveLine = Component.literal(" - squadtp.revivetime ")
                .append(Component.translatable("squadtp.feature.revivetime").withStyle(ChatFormatting.GRAY))
                .append(" : ")
                .append(Component.literal(manager.getReviveCastSeconds() + "s")
                        .withStyle(overridden ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        ctx.getSource().sendSuccess(() -> reviveLine, false);
        return 1;
    }

    /** Sets an admin-overridden revive cast duration; persists until reset back to the config default. */
    private static int adminSetReviveTime(CommandContext<CommandSourceStack> ctx) {
        double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
        manager(ctx).setReviveCastSecondsOverride(seconds);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.admin_revivetime_set", seconds), true);
        return 1;
    }

    private static int adminResetReviveTime(CommandContext<CommandSourceStack> ctx) {
        manager(ctx).setReviveCastSecondsOverride(null);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.admin_revivetime_reset",
                Config.REVIVE_CAST_SECONDS.get()), true);
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String key = StringArgumentType.getString(ctx, "feature");
        SquadFeature feature = SquadFeature.byKey(key);
        if (feature == null) {
            return fail(ctx, "squadtp.msg.admin_unknown_feature", key);
        }
        manager(ctx).setFeatureEnabled(feature, enabled);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.admin_set",
                Component.translatable(feature.translationKey()),
                Component.translatable(enabled ? "squadtp.msg.admin_on" : "squadtp.msg.admin_off")), true);
        return 1;
    }

    /**
     * Same-vanilla-team rule: with requireSameTeam on, a player may only join a
     * squad whose leader is on the same scoreboard team (both teamless is fine).
     */
    private static boolean sameTeam(MinecraftServer server, String nameA, String nameB) {
        if (!Config.REQUIRE_SAME_TEAM.get()) {
            return true;
        }
        var scoreboard = server.getScoreboard();
        return java.util.Objects.equals(scoreboard.getPlayersTeam(nameA), scoreboard.getPlayersTeam(nameB));
    }

    /** True (and a failure message was sent) when the feature is switched off server-wide. */
    private static boolean featureBlocked(CommandContext<CommandSourceStack> ctx, SquadFeature feature) {
        if (manager(ctx).isEnabled(feature)) {
            return false;
        }
        ctx.getSource().sendFailure(Component.translatable("squadtp.msg.feature_disabled",
                Component.translatable(feature.translationKey())));
        return true;
    }

    // --- respawn choice (only valid in the server-tracked window after death) ---

    private static int respawnRally(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.RESPAWN_CHOICE)) {
            return 0;
        }
        if (uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(player.getUUID())) {
            return fail(ctx, "squadtp.msg.you_are_downed");
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.hasRally()) {
            return fail(ctx, "squadtp.msg.no_rally");
        }
        ServerLevel targetLevel = server.getLevel(squad.getRallyDimension());
        if (targetLevel == null) {
            return fail(ctx, "squadtp.msg.no_rally");
        }
        if (TeleportHelper.isDestinationDangerous(targetLevel, squad.getRallyPos(), player)) {
            return fail(ctx, "squadtp.msg.spawn_danger");
        }
        if (!manager.consumeRespawnChoice(player.getUUID())) {
            return fail(ctx, "squadtp.msg.respawn_expired");
        }

        BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, squad.getRallyPos());
        player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.rally_tp"), false);
        return 1;
    }

    private static int respawnBeacon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        // Gated by RESPAWN_CHOICE (the spawn-time switch), not BEACON (the anytime /squad beacon switch),
        // so admins can toggle "teleport on respawn" independently of "teleport anytime".
        if (featureBlocked(ctx, SquadFeature.RESPAWN_CHOICE)) {
            return 0;
        }
        if (uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(player.getUUID())) {
            return fail(ctx, "squadtp.msg.you_are_downed");
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.hasBeacon()) {
            return fail(ctx, "squadtp.msg.no_beacon");
        }
        ServerLevel targetLevel = server.getLevel(squad.getBeaconDimension());
        if (targetLevel == null) {
            return fail(ctx, "squadtp.msg.no_beacon");
        }
        if (TeleportHelper.isDestinationDangerous(targetLevel, squad.getBeaconPos(), player)) {
            return fail(ctx, "squadtp.msg.spawn_danger");
        }
        if (!manager.consumeRespawnChoice(player.getUUID())) {
            return fail(ctx, "squadtp.msg.respawn_expired");
        }

        BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, squad.getBeaconPos());
        player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        manager.consumeBeaconUse(server, squad);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.beacon_tp"), false);
        return 1;
    }

    private static int respawnMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "member");
        if (featureBlocked(ctx, SquadFeature.RESPAWN_CHOICE)) {
            return 0;
        }
        if (uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(player.getUUID())) {
            return fail(ctx, "squadtp.msg.you_are_downed");
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        UUID target = squad.findMemberByName(name);
        if (target == null) {
            return fail(ctx, "squadtp.msg.target_not_member", name);
        }
        if (target.equals(player.getUUID())) {
            return fail(ctx, "squadtp.msg.cannot_self");
        }

        ServerLevel targetLevel;
        BlockPos targetPos;
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            if (uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(target)) {
                return fail(ctx, "squadtp.msg.target_downed", name);
            }
            long combat = manager.combatRemaining(target);
            if (combat > 0) {
                return fail(ctx, "squadtp.msg.target_in_combat", name, combat);
            }
            targetLevel = online.serverLevel();
            targetPos = online.blockPosition();
        } else {
            DummyRegistry.Entry dummy = manager.isDummy(target) ? DummyRegistry.get(target) : null;
            if (dummy == null) {
                return fail(ctx, "squadtp.msg.member_offline", name);
            }
            targetLevel = server.getLevel(dummy.dimension());
            if (targetLevel == null) {
                return fail(ctx, "squadtp.msg.member_offline", name);
            }
            targetPos = dummy.pos().above();
        }
        if (TeleportHelper.isDestinationDangerous(targetLevel, targetPos, player)) {
            return fail(ctx, "squadtp.msg.spawn_danger");
        }
        if (!manager.consumeRespawnChoice(player.getUUID())) {
            return fail(ctx, "squadtp.msg.respawn_expired");
        }

        BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, targetPos);
        player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.tp_success", name), false);
        return 1;
    }

    // --- subcommands ---

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.CREATE)) {
            return 0;
        }
        if (manager.getSquadOf(player.getUUID()) != null) {
            return fail(ctx, "squadtp.msg.already_in_squad");
        }
        manager.create(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.created"), false);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.INVITE)) {
            return 0;
        }

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        if (target.getUUID().equals(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.cannot_self");
        }
        if (squad.isMember(target.getUUID())) {
            return fail(ctx, "squadtp.msg.already_in_this_squad", target.getDisplayName());
        }
        if (squad.size() >= Config.MAX_SQUAD_SIZE.get()) {
            return fail(ctx, "squadtp.msg.squad_full", Config.MAX_SQUAD_SIZE.get());
        }
        if (!sameTeam(ctx.getSource().getServer(), leader.getGameProfile().getName(), target.getGameProfile().getName())) {
            return fail(ctx, "squadtp.msg.team_mismatch", target.getDisplayName());
        }

        manager.invite(squad, target.getUUID());

        MutableComponent notice = Component.translatable("squadtp.msg.invited_you", leader.getDisplayName())
                .append(" ")
                .append(button("squadtp.msg.accept_button", "/squad accept", ChatFormatting.GREEN))
                .append(" ")
                .append(button("squadtp.msg.deny_button", "/squad deny", ChatFormatting.RED));
        target.sendSystemMessage(notice);
        NetworkHandler.sendInvited(target, leader.getGameProfile().getName());
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.invite_sent", target.getDisplayName()), false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.INVITE)) {
            return 0;
        }

        Squad squad = manager.findInviteFor(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.no_invite");
        }
        Squad oldSquad = manager.getSquadOf(player.getUUID());
        if (oldSquad != null && oldSquad.getId().equals(squad.getId())) {
            return fail(ctx, "squadtp.msg.already_in_this_squad", squad.getMemberName(squad.getLeader()));
        }
        if (squad.size() >= Config.MAX_SQUAD_SIZE.get()) {
            return fail(ctx, "squadtp.msg.squad_full", Config.MAX_SQUAD_SIZE.get());
        }
        if (!sameTeam(server, player.getGameProfile().getName(),
                squad.getMemberName(squad.getLeader()))) {
            return fail(ctx, "squadtp.msg.team_mismatch", squad.getMemberName(squad.getLeader()));
        }

        // Switch squads: leave the current one (if any) only after every check above has passed,
        // so a failed join never leaves the player without their old squad.
        if (oldSquad != null) {
            UUID newOldLeader = manager.removeMember(server, oldSquad, player.getUUID());
            broadcast(server, oldSquad, "squadtp.msg.member_left", player.getDisplayName());
            if (newOldLeader != null) {
                broadcast(server, oldSquad, "squadtp.msg.leader_now", oldSquad.getMemberName(newOldLeader));
            }
        }

        broadcast(server, squad, "squadtp.msg.member_joined", player.getDisplayName());
        manager.join(server, squad, player);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.joined"), false);
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SquadManager manager = manager(ctx);

        Squad squad = manager.findInviteFor(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.no_invite");
        }
        manager.removeInvite(squad, player.getUUID());
        NetworkHandler.sendEmptySync(ctx.getSource().getServer(), player.getUUID());

        ServerPlayer leader = ctx.getSource().getServer().getPlayerList().getPlayer(squad.getLeader());
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable("squadtp.msg.invite_denied", player.getDisplayName()));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.denied"), false);
        return 1;
    }

    private static int requestJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer applicant = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);

        if (featureBlocked(ctx, SquadFeature.JOIN_REQUEST)) {
            return 0;
        }
        Squad squad = manager.getSquadOf(target.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.target_no_squad", target.getDisplayName());
        }
        Squad currentSquad = manager.getSquadOf(applicant.getUUID());
        if (currentSquad != null && currentSquad.getId().equals(squad.getId())) {
            return fail(ctx, "squadtp.msg.already_in_this_squad", squad.getMemberName(squad.getLeader()));
        }
        if (squad.size() >= Config.MAX_SQUAD_SIZE.get()) {
            return fail(ctx, "squadtp.msg.squad_full", Config.MAX_SQUAD_SIZE.get());
        }
        if (!sameTeam(server, applicant.getGameProfile().getName(), squad.getMemberName(squad.getLeader()))) {
            return fail(ctx, "squadtp.msg.team_mismatch", squad.getMemberName(squad.getLeader()));
        }

        String applicantName = applicant.getGameProfile().getName();

        // Open-join squads admit immediately; invite-only squads still require leader approval.
        // finalizeJoin already messages the applicant directly, so no extra sendSuccess here.
        if (squad.isOpenJoin()) {
            finalizeJoin(server, manager, squad, applicant.getUUID(), applicantName);
            return 1;
        }

        manager.requestJoin(server, squad, applicant);

        ServerPlayer leader = server.getPlayerList().getPlayer(squad.getLeader());
        if (leader != null) {
            MutableComponent notice = Component.translatable("squadtp.msg.requested_join", applicantName)
                    .append(" ")
                    .append(button("squadtp.msg.approve_button", "/squad approve " + applicantName, ChatFormatting.GREEN))
                    .append(" ")
                    .append(button("squadtp.msg.reject_button", "/squad reject " + applicantName, ChatFormatting.RED));
            leader.sendSystemMessage(notice);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.request_sent",
                squad.getMemberName(squad.getLeader())), false);
        return 1;
    }

    /** Leader-only: switches the squad's join policy between open (auto-admit) and invite-only (needs approval). */
    private static int setJoinPolicy(CommandContext<CommandSourceStack> ctx, boolean open) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        manager.setOpenJoin(server, squad, open);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                open ? "squadtp.msg.join_policy_open" : "squadtp.msg.join_policy_invite"), true);
        return 1;
    }

    private static int approve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "player");

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        if (featureBlocked(ctx, SquadFeature.JOIN_REQUEST)) {
            return 0;
        }
        UUID applicant = squad.findJoinRequestByName(name);
        if (applicant == null) {
            return fail(ctx, "squadtp.msg.no_request", name);
        }
        Squad oldSquad = manager.getSquadOf(applicant);
        if (oldSquad != null && oldSquad.getId().equals(squad.getId())) {
            manager.removeJoinRequest(server, squad, applicant);
            return fail(ctx, "squadtp.msg.already_in_this_squad", name);
        }
        if (squad.size() >= Config.MAX_SQUAD_SIZE.get()) {
            return fail(ctx, "squadtp.msg.squad_full", Config.MAX_SQUAD_SIZE.get());
        }
        if (!sameTeam(server, name, leader.getGameProfile().getName())) {
            return fail(ctx, "squadtp.msg.team_mismatch", name);
        }

        finalizeJoin(server, manager, squad, applicant, name);
        return 1;
    }

    /** Finalizes a join after all validation has passed: switches squads if necessary, then joins. */
    private static void finalizeJoin(MinecraftServer server, SquadManager manager, Squad squad,
                                     UUID applicantId, String applicantName) {
        Squad oldSquad = manager.getSquadOf(applicantId);
        if (oldSquad != null && !oldSquad.getId().equals(squad.getId())) {
            UUID newOldLeader = manager.removeMember(server, oldSquad, applicantId);
            broadcast(server, oldSquad, "squadtp.msg.member_left", applicantName);
            if (newOldLeader != null) {
                broadcast(server, oldSquad, "squadtp.msg.leader_now", oldSquad.getMemberName(newOldLeader));
            }
        }
        broadcast(server, squad, "squadtp.msg.member_joined", applicantName);
        manager.joinMember(server, squad, applicantId, applicantName);
        ServerPlayer joined = server.getPlayerList().getPlayer(applicantId);
        if (joined != null) {
            joined.sendSystemMessage(Component.translatable("squadtp.msg.joined"));
        }
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "player");

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        UUID applicant = squad.findJoinRequestByName(name);
        if (applicant == null) {
            return fail(ctx, "squadtp.msg.no_request", name);
        }

        manager.removeJoinRequest(server, squad, applicant);
        ServerPlayer online = server.getPlayerList().getPlayer(applicant);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("squadtp.msg.request_rejected"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.request_rejected_leader", name), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }

        UUID newLeader = manager.removeMember(server, squad, player.getUUID());
        broadcast(server, squad, "squadtp.msg.member_left", player.getDisplayName());
        if (newLeader != null) {
            broadcast(server, squad, "squadtp.msg.leader_now", squad.getMemberName(newLeader));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.left"), false);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "member");

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        UUID target = squad.findMemberByName(name);
        if (target == null) {
            return fail(ctx, "squadtp.msg.target_not_member", name);
        }
        if (target.equals(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.cannot_self");
        }

        manager.removeMember(server, squad, target);
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("squadtp.msg.kicked_you"));
        }
        broadcast(server, squad, "squadtp.msg.kicked", name);
        return 1;
    }

    private static int promote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "member");

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }
        UUID target = squad.findMemberByName(name);
        if (target == null) {
            return fail(ctx, "squadtp.msg.target_not_member", name);
        }
        if (target.equals(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.cannot_self");
        }
        if (manager.isDummy(target)) {
            return fail(ctx, "squadtp.msg.cannot_promote_dummy");
        }

        manager.promote(server, squad, target);
        broadcast(server, squad, "squadtp.msg.leader_now", squad.getMemberName(target));
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }

        broadcast(server, squad, "squadtp.msg.disbanded");
        manager.disband(server, squad);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.info_header",
                squad.size(), Config.MAX_SQUAD_SIZE.get()), false);
        for (var entry : squad.getMembers().entrySet()) {
            boolean isLeader = squad.isLeader(entry.getKey());
            boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null
                    || DummyRegistry.isLoaded(entry.getKey());
            MutableComponent line = Component.literal(" - " + entry.getValue());
            if (isLeader) {
                line.append(" ").append(Component.translatable("squadtp.msg.info_leader")
                        .withStyle(ChatFormatting.GOLD));
            }
            line.append(" ").append(Component.translatable(online ? "squadtp.msg.info_online" : "squadtp.msg.info_offline")
                    .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        if (squad.hasRally()) {
            BlockPos rally = squad.getRallyPos();
            ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.info_rally",
                    rally.getX(), rally.getY(), rally.getZ(), squad.getRallyDimension().location().toString()), false);
        }
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        String name = StringArgumentType.getString(ctx, "member");
        if (featureBlocked(ctx, SquadFeature.TELEPORT)) {
            return 0;
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        UUID target = squad.findMemberByName(name);
        if (target == null) {
            return fail(ctx, "squadtp.msg.target_not_member", name);
        }
        if (target.equals(player.getUUID())) {
            return fail(ctx, "squadtp.msg.cannot_self");
        }

        // Resolve the destination: an online player, or a loaded dummy block.
        ServerLevel targetLevel;
        BlockPos targetPos;
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            if (uk.iwaservice.squadtp.squad.ReviveSystem.isDowned(target)) {
                return fail(ctx, "squadtp.msg.target_downed", name);
            }
            long combat = manager.combatRemaining(target);
            if (combat > 0) {
                return fail(ctx, "squadtp.msg.target_in_combat", name, combat);
            }
            targetLevel = online.serverLevel();
            targetPos = online.blockPosition();
        } else {
            DummyRegistry.Entry dummy = manager.isDummy(target) ? DummyRegistry.get(target) : null;
            if (dummy == null) {
                return fail(ctx, "squadtp.msg.member_offline", name);
            }
            targetLevel = server.getLevel(dummy.dimension());
            if (targetLevel == null) {
                return fail(ctx, "squadtp.msg.member_offline", name);
            }
            targetPos = dummy.pos().above();
        }

        Component failure = TeleportHelper.attempt(manager, player, targetLevel, targetPos);
        if (failure != null) {
            ctx.getSource().sendFailure(failure);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.tp_success", name), false);
        return 1;
    }

    private static int setRally(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.RALLY)) {
            return 0;
        }

        Squad squad = manager.getSquadOf(leader.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.isLeader(leader.getUUID())) {
            return fail(ctx, "squadtp.msg.not_leader");
        }

        BlockPos pos = leader.blockPosition();
        manager.setRally(server, squad, leader.level().dimension(), pos);
        broadcast(server, squad, "squadtp.msg.rally_set",
                pos.getX(), pos.getY(), pos.getZ(), leader.level().dimension().location().toString());
        return 1;
    }

    private static int rally(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.RALLY)) {
            return 0;
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.hasRally()) {
            return fail(ctx, "squadtp.msg.no_rally");
        }
        ServerLevel targetLevel = server.getLevel(squad.getRallyDimension());
        if (targetLevel == null) {
            return fail(ctx, "squadtp.msg.no_rally");
        }

        Component failure = TeleportHelper.attempt(manager, player, targetLevel, squad.getRallyPos());
        if (failure != null) {
            ctx.getSource().sendFailure(failure);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.rally_tp"), false);
        return 1;
    }

    /** Anytime (not just after death) teleport to the squad's beacon; subject to the normal tp cooldown/cost. */
    private static int beacon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        SquadManager manager = manager(ctx);
        if (featureBlocked(ctx, SquadFeature.BEACON)) {
            return 0;
        }

        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            return fail(ctx, "squadtp.msg.not_in_squad");
        }
        if (!squad.hasBeacon()) {
            return fail(ctx, "squadtp.msg.no_beacon");
        }
        ServerLevel targetLevel = server.getLevel(squad.getBeaconDimension());
        if (targetLevel == null) {
            return fail(ctx, "squadtp.msg.no_beacon");
        }

        Component failure = TeleportHelper.attempt(manager, player, targetLevel, squad.getBeaconPos());
        if (failure != null) {
            ctx.getSource().sendFailure(failure);
            return 0;
        }
        manager.consumeBeaconUse(server, squad);
        ctx.getSource().sendSuccess(() -> Component.translatable("squadtp.msg.beacon_tp"), false);
        return 1;
    }

    // --- helpers ---

    private static SquadManager manager(CommandContext<CommandSourceStack> ctx) {
        return SquadManager.get(ctx.getSource().getServer());
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static Component button(String key, String command, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** Sends a translatable message to every online member of the squad. */
    private static void broadcast(MinecraftServer server, Squad squad, String key, Object... args) {
        for (UUID member : squad.getMembers().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(Component.translatable(key, args));
            }
        }
    }

    private SquadCommand() {}
}
