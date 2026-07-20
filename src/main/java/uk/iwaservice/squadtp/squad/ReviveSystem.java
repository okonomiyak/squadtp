package uk.iwaservice.squadtp.squad;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import uk.iwaservice.squadtp.Config;
import uk.iwaservice.squadtp.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative down/revive state machine.
 *
 * A lethal hit puts a squad member into a "downed" state (health pinned at 1,
 * heavy slowness) instead of killing them. Squad mates channel a revive by
 * holding right-click on them; letting go or stepping away cancels the cast.
 * The downed player dies for real when the timeout expires. All transitions
 * happen here on the server; clients only render state received via packets.
 */
public final class ReviveSystem {

    /** Ticks a revive session survives without a fresh right-click (vanilla re-fires every ~4). */
    private static final int SESSION_GRACE_TICKS = 6;
    private static final double MAX_REVIVE_DISTANCE_SQR = 16.0; // 4 blocks

    private static final class DownedData {
        int remainingTicks;

        DownedData(int remainingTicks) {
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class ReviveSession {
        final UUID target;
        int progressTicks;
        int lastRefreshTick;

        ReviveSession(UUID target) {
            this.target = target;
        }
    }

    /** Downed player UUID -> countdown state. */
    private static final Map<UUID, DownedData> DOWNED = new HashMap<>();
    /** Reviver UUID -> active channel session. */
    private static final Map<UUID, ReviveSession> SESSIONS = new HashMap<>();

    public static boolean isDowned(UUID player) {
        return DOWNED.containsKey(player);
    }

    /** Puts the player into the downed state instead of dying. */
    public static void enterDowned(ServerPlayer player) {
        DownedData data = new DownedData(Config.DOWNED_TIMEOUT_SECONDS.get() * 20);
        DOWNED.put(player.getUUID(), data);
        player.setHealth(1.0f);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, data.remainingTicks + 40, 5, false, false));
        // Visible-to-everyone downed cues: prone pose + glowing outline.
        player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, data.remainingTicks + 40, 0, false, false));
        NetworkHandler.sendDownedState(player, true, data.remainingTicks);
        broadcastSquad(player, "squadtp.msg.downed_broadcast", player.getGameProfile().getName());
    }

    /** Downed player chose to give up: die immediately (kill source bypasses the down guard). */
    public static void forceDeath(ServerPlayer player) {
        if (isDowned(player.getUUID())) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /** Called for every right-click of a (potential) reviver on a downed player. */
    public static void handleInteract(ServerPlayer reviver, ServerPlayer target) {
        MinecraftServer server = reviver.server;
        SquadManager manager = SquadManager.get(server);
        if (!manager.isEnabled(SquadFeature.REVIVE) || isDowned(reviver.getUUID())) {
            return;
        }
        if (!Config.ALLOW_NON_SQUAD_REVIVE.get()) {
            Squad squad = manager.getSquadOf(target.getUUID());
            if (squad == null || !squad.isMember(reviver.getUUID())) {
                reviver.displayClientMessage(Component.translatable("squadtp.msg.revive_not_allowed"), true);
                return;
            }
        }
        ReviveSession session = SESSIONS.get(reviver.getUUID());
        if (session == null || !session.target.equals(target.getUUID())) {
            session = new ReviveSession(target.getUUID());
            SESSIONS.put(reviver.getUUID(), session);
        }
        session.lastRefreshTick = server.getTickCount();
    }

    /** Runs every server tick: downed countdowns and revive channel progress. */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, DownedData>> downed = DOWNED.entrySet().iterator();
        while (downed.hasNext()) {
            Map.Entry<UUID, DownedData> entry = downed.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                downed.remove(); // logout is handled in onLogout; this is just a safety net
                continue;
            }
            if (--entry.getValue().remainingTicks <= 0) {
                downed.remove();
                // Entry is already removed, so onPlayerDeath won't send the clear packet - do it here.
                player.setForcedPose(null);
                NetworkHandler.sendDownedState(player, false, 0);
                // genericKill bypasses invulnerability, so the death handler lets it through.
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
        }

        int now = server.getTickCount();
        int castTicks = Math.max(1, (int) Math.round(SquadManager.get(server).getReviveCastSeconds() * 20));
        Iterator<Map.Entry<UUID, ReviveSession>> sessions = SESSIONS.entrySet().iterator();
        while (sessions.hasNext()) {
            Map.Entry<UUID, ReviveSession> entry = sessions.next();
            ReviveSession session = entry.getValue();
            ServerPlayer reviver = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer target = server.getPlayerList().getPlayer(session.target);
            boolean valid = reviver != null && target != null
                    && isDowned(session.target)
                    && !isDowned(entry.getKey())
                    && now - session.lastRefreshTick <= SESSION_GRACE_TICKS
                    && reviver.level() == target.level()
                    && reviver.distanceToSqr(target) <= MAX_REVIVE_DISTANCE_SQR;
            if (!valid) {
                sessions.remove();
                clearProgress(reviver, target);
                continue;
            }
            session.progressTicks++;
            NetworkHandler.sendReviveProgress(reviver, session.progressTicks, castTicks);
            NetworkHandler.sendReviveProgress(target, session.progressTicks, castTicks);
            if (session.progressTicks >= castTicks) {
                sessions.remove();
                complete(reviver, target);
            }
        }
    }

    private static void complete(ServerPlayer reviver, ServerPlayer target) {
        DOWNED.remove(target.getUUID());
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.GLOWING);
        target.setForcedPose(null);
        float health = Math.max(1.0f, target.getMaxHealth() * Config.REVIVE_HEAL_PERCENT.get() / 100.0f);
        target.setHealth(health);
        int invuln = Config.REVIVE_INVULN_SECONDS.get();
        if (invuln > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, invuln * 20, 4, false, true));
        }
        NetworkHandler.sendDownedState(target, false, 0);
        clearProgress(reviver, target);
        broadcastSquad(target, "squadtp.msg.revived",
                reviver.getGameProfile().getName(), target.getGameProfile().getName());
        SquadManager manager = SquadManager.get(reviver.server);
        Squad squad = manager.getSquadOf(target.getUUID());
        if (squad == null || !squad.isMember(reviver.getUUID())) {
            reviver.sendSystemMessage(Component.translatable("squadtp.msg.revived",
                    reviver.getGameProfile().getName(), target.getGameProfile().getName()));
        }
    }

    /** Cleanup when a player actually dies (timeout kill, give-up, /kill, void, ...). */
    public static void onPlayerDeath(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (DOWNED.remove(uuid) != null) {
            player.setForcedPose(null);
            NetworkHandler.sendDownedState(player, false, 0);
        }
        cancelSessionsFor(player.server, uuid);
    }

    /** Combat-log guard: a downed player who disconnects dies for real. */
    public static void onLogout(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ReviveSession session = SESSIONS.remove(uuid);
        if (session != null) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(session.target);
            clearProgress(null, target);
        }
        if (DOWNED.remove(uuid) != null) {
            player.setForcedPose(null);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
        cancelSessionsFor(player.server, uuid);
    }

    public static void clear() {
        DOWNED.clear();
        SESSIONS.clear();
    }

    private static void cancelSessionsFor(MinecraftServer server, UUID target) {
        SESSIONS.entrySet().removeIf(entry -> {
            if (entry.getValue().target.equals(target)) {
                clearProgress(server.getPlayerList().getPlayer(entry.getKey()), null);
                return true;
            }
            return false;
        });
    }

    private static void clearProgress(@Nullable ServerPlayer reviver, @Nullable ServerPlayer target) {
        if (reviver != null) {
            NetworkHandler.sendReviveProgress(reviver, -1, 0);
        }
        if (target != null) {
            NetworkHandler.sendReviveProgress(target, -1, 0);
        }
    }

    private static void broadcastSquad(ServerPlayer about, String key, Object... args) {
        SquadManager manager = SquadManager.get(about.server);
        Squad squad = manager.getSquadOf(about.getUUID());
        if (squad == null) {
            return;
        }
        for (UUID member : squad.getMembers().keySet()) {
            ServerPlayer online = about.server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(Component.translatable(key, args));
            }
        }
    }

    private ReviveSystem() {}
}
