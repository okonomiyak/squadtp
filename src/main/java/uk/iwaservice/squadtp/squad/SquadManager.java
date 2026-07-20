package uk.iwaservice.squadtp.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import uk.iwaservice.squadtp.Config;
import uk.iwaservice.squadtp.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative registry of all squads, persisted with the overworld.
 * Every operation validates the acting player's permission here; clients can
 * only reach these methods through commands, so there is no packet to spoof.
 */
public class SquadManager extends SavedData {
    private static final String DATA_NAME = "squadtp_squads";

    private final Map<UUID, Squad> squads = new HashMap<>();
    private final Map<UUID, UUID> playerSquad = new HashMap<>();
    /** UUIDs that belong to test dummy blocks rather than real players (persisted). */
    private final java.util.Set<UUID> dummies = new java.util.HashSet<>();
    /** Server-wide feature switches; disabled entries are persisted. */
    private final java.util.Set<SquadFeature> disabledFeatures = java.util.EnumSet.noneOf(SquadFeature.class);
    /** Runtime override for the revive cast duration in seconds; null = use the config default. */
    @Nullable
    private Double reviveCastSecondsOverride;
    /** Transient teleport cooldowns: player UUID -> epoch millis of last squad teleport. */
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    /** Transient respawn-choice windows: player UUID -> epoch millis of respawn. */
    private final Map<UUID, Long> respawnWindows = new HashMap<>();
    /** Transient combat tags: player UUID -> epoch millis of last damage taken. */
    private final Map<UUID, Long> lastDamaged = new HashMap<>();

    public static SquadManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SquadManager::load, SquadManager::new, DATA_NAME);
    }

    public SquadManager() {
    }

    @Nullable
    public Squad getSquadOf(UUID player) {
        UUID squadId = playerSquad.get(player);
        return squadId == null ? null : squads.get(squadId);
    }

    @Nullable
    public Squad getSquad(UUID squadId) {
        return squads.get(squadId);
    }

    /** Squad that currently has a valid (non-expired) invite for the player. */
    @Nullable
    public Squad findInviteFor(UUID player) {
        long now = System.currentTimeMillis();
        for (Squad squad : squads.values()) {
            if (squad.hasValidInvite(player, now)) {
                return squad;
            }
        }
        return null;
    }

    // --- membership operations (permission checks live here) ---

    public Squad create(ServerPlayer creator) {
        Squad squad = new Squad(UUID.randomUUID(), creator.getUUID(), creator.getGameProfile().getName());
        squads.put(squad.getId(), squad);
        playerSquad.put(creator.getUUID(), squad.getId());
        setDirty();
        sync(creator.server, squad);
        return squad;
    }

    public void invite(Squad squad, UUID target) {
        long expiry = System.currentTimeMillis() + Config.INVITE_EXPIRY_SECONDS.get() * 1000L;
        squad.putInvite(target, expiry);
        setDirty();
    }

    public void join(MinecraftServer server, Squad squad, ServerPlayer player) {
        joinMember(server, squad, player.getUUID(), player.getGameProfile().getName());
    }

    /** Adds a member by UUID (works for offline players approved via join request). */
    public void joinMember(MinecraftServer server, Squad squad, UUID player, String name) {
        clearPendingFor(player);
        squad.addMember(player, name);
        playerSquad.put(player, squad.getId());
        setDirty();
        sync(server, squad);
    }

    /** Drops the player's invites and join requests across all squads (e.g. once they joined one). */
    public void clearPendingFor(UUID player) {
        for (Squad squad : squads.values()) {
            squad.removeInvite(player);
            squad.removeJoinRequest(player);
        }
        setDirty();
    }

    public void requestJoin(MinecraftServer server, Squad squad, ServerPlayer applicant) {
        long expiry = System.currentTimeMillis() + Config.INVITE_EXPIRY_SECONDS.get() * 1000L;
        squad.putJoinRequest(applicant.getUUID(), applicant.getGameProfile().getName(), expiry);
        setDirty();
        sync(server, squad);
    }

    public void removeJoinRequest(MinecraftServer server, Squad squad, UUID applicant) {
        if (squad.removeJoinRequest(applicant)) {
            setDirty();
            sync(server, squad);
        }
    }

    // --- feature switches ---

    public boolean isEnabled(SquadFeature feature) {
        return !disabledFeatures.contains(feature);
    }

    public void setFeatureEnabled(SquadFeature feature, boolean enabled) {
        if (enabled) {
            disabledFeatures.remove(feature);
        } else {
            disabledFeatures.add(feature);
        }
        setDirty();
    }

    // --- revive cast time override ---

    /** Effective revive cast duration in seconds: the admin override if set, else the config default. */
    public double getReviveCastSeconds() {
        return reviveCastSecondsOverride != null ? reviveCastSecondsOverride : Config.REVIVE_CAST_SECONDS.get();
    }

    @Nullable
    public Double getReviveCastSecondsOverride() {
        return reviveCastSecondsOverride;
    }

    /** Pass null to clear the override and fall back to the config default. */
    public void setReviveCastSecondsOverride(@Nullable Double seconds) {
        this.reviveCastSecondsOverride = seconds;
        setDirty();
    }

    /** True if this UUID belongs to a test dummy block, not a real player. */
    public boolean isDummy(UUID id) {
        return dummies.contains(id);
    }

    /** Adds a test dummy block to the squad as a regular member. */
    public void joinDummy(MinecraftServer server, Squad squad, UUID dummyId, String dummyName) {
        dummies.add(dummyId);
        squad.addMember(dummyId, dummyName);
        playerSquad.put(dummyId, squad.getId());
        setDirty();
        sync(server, squad);
    }

    public boolean removeInvite(Squad squad, UUID player) {
        boolean removed = squad.removeInvite(player);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /**
     * Removes the player from their squad. If they were leader, leadership is
     * transferred to the oldest remaining member; if the squad becomes empty it
     * is disbanded. Returns the new leader UUID if leadership changed, else null.
     */
    @Nullable
    public UUID removeMember(MinecraftServer server, Squad squad, UUID player) {
        squad.removeMember(player);
        playerSquad.remove(player);
        NetworkHandler.sendEmptySync(server, player);

        UUID newLeader = null;
        if (squad.size() == 0) {
            squads.remove(squad.getId());
        } else if (squad.isLeader(player)) {
            newLeader = squad.successor(member -> !isDummy(member));
            if (newLeader != null) {
                squad.setLeader(newLeader);
            } else {
                // Only dummies remain; a dummy cannot lead, so dissolve the squad.
                for (UUID member : squad.getMembers().keySet()) {
                    playerSquad.remove(member);
                }
                squads.remove(squad.getId());
            }
        }
        setDirty();
        if (squads.containsKey(squad.getId())) {
            sync(server, squad);
        }
        return newLeader;
    }

    public void promote(MinecraftServer server, Squad squad, UUID newLeader) {
        squad.setLeader(newLeader);
        setDirty();
        sync(server, squad);
    }

    public void disband(MinecraftServer server, Squad squad) {
        for (UUID member : squad.getMembers().keySet()) {
            playerSquad.remove(member);
            NetworkHandler.sendEmptySync(server, member);
        }
        squads.remove(squad.getId());
        setDirty();
    }

    public void setRally(MinecraftServer server, Squad squad, ResourceKey<Level> dimension, BlockPos pos) {
        squad.setRally(dimension, pos);
        setDirty();
        sync(server, squad);
    }

    // --- respawn beacon ---

    /**
     * Registers a newly placed beacon entity as the squad's active one,
     * discarding any previous beacon entity that's still loaded (at most one
     * beacon per squad at a time).
     */
    public void placeBeacon(MinecraftServer server, Squad squad, net.minecraft.world.entity.Entity beaconEntity,
                            int startingUses) {
        discardExistingBeacon(server, squad);
        squad.setBeacon(beaconEntity.getUUID(), beaconEntity.level().dimension(), beaconEntity.blockPosition(), startingUses);
        setDirty();
        sync(server, squad);
        broadcast(server, squad, "squadtp.msg.beacon_placed", startingUses);
    }

    private void discardExistingBeacon(MinecraftServer server, Squad squad) {
        if (!squad.hasBeacon()) {
            return;
        }
        ServerLevel oldLevel = server.getLevel(squad.getBeaconDimension());
        if (oldLevel != null) {
            net.minecraft.world.entity.Entity old = oldLevel.getEntity(squad.getBeaconEntityId());
            if (old != null) {
                old.discard();
            }
        }
    }

    /**
     * Consumes one use of the squad's beacon after a successful teleport to
     * it. Once uses reach zero the beacon entity is removed and the squad's
     * beacon is cleared.
     */
    public void consumeBeaconUse(MinecraftServer server, Squad squad) {
        int remaining = squad.decrementBeaconUses();
        if (remaining <= 0) {
            discardExistingBeacon(server, squad);
            squad.clearBeacon();
            setDirty();
            sync(server, squad);
            broadcast(server, squad, "squadtp.msg.beacon_depleted");
        } else {
            setDirty();
            sync(server, squad);
            broadcast(server, squad, "squadtp.msg.beacon_uses_left", remaining);
        }
    }

    /** Called by the beacon entity itself when it dies from combat damage. */
    public void onBeaconDestroyed(MinecraftServer server, Squad squad) {
        squad.clearBeacon();
        setDirty();
        sync(server, squad);
        broadcast(server, squad, "squadtp.msg.beacon_destroyed");
    }

    /** Sends a translatable message to every online member of the squad. */
    private void broadcast(MinecraftServer server, Squad squad, String key, Object... args) {
        for (UUID member : squad.getMembers().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, args));
            }
        }
    }

    public void updateName(ServerPlayer player) {
        Squad squad = getSquadOf(player.getUUID());
        if (squad != null) {
            squad.updateName(player.getUUID(), player.getGameProfile().getName());
            setDirty();
        }
    }

    // --- teleport cooldown (transient) ---

    /** Remaining cooldown in seconds, 0 if ready. */
    public long cooldownRemaining(UUID player) {
        int cooldown = Config.TP_COOLDOWN_SECONDS.get();
        if (cooldown <= 0) {
            return 0;
        }
        Long last = lastTeleport.get(player);
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldown - elapsed);
    }

    public void markTeleported(UUID player) {
        lastTeleport.put(player, System.currentTimeMillis());
    }

    // --- combat tag (transient) ---

    public void markDamaged(UUID player) {
        lastDamaged.put(player, System.currentTimeMillis());
    }

    /** Seconds left in which the player counts as "in combat", 0 if none. */
    public long combatRemaining(UUID player) {
        int block = Config.COMBAT_BLOCK_SECONDS.get();
        if (block <= 0) {
            return 0;
        }
        Long last = lastDamaged.get(player);
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, block - elapsed);
    }

    // --- respawn choice window (transient) ---

    /** Opens the free-teleport window a player gets right after respawning. */
    public void markRespawnChoice(UUID player) {
        respawnWindows.put(player, System.currentTimeMillis());
    }

    /**
     * Consumes the player's respawn-choice window. Returns false if it was
     * never opened or has expired — the guard that keeps /squad respawn from
     * being a free teleport at arbitrary times.
     */
    public boolean consumeRespawnChoice(UUID player) {
        Long openedAt = respawnWindows.remove(player);
        if (openedAt == null) {
            return false;
        }
        long window = Config.RESPAWN_CHOICE_WINDOW_SECONDS.get() * 1000L;
        return System.currentTimeMillis() - openedAt <= window;
    }

    // --- sync ---

    /** Pushes the squad's current state to every online member. */
    public void sync(MinecraftServer server, Squad squad) {
        for (UUID member : squad.getMembers().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                NetworkHandler.sendSquadSync(online, squad);
            }
        }
    }

    // --- persistence ---

    public static SquadManager load(CompoundTag tag) {
        SquadManager manager = new SquadManager();
        ListTag squadList = tag.getList("Squads", Tag.TAG_COMPOUND);
        for (int i = 0; i < squadList.size(); i++) {
            Squad squad = Squad.load(squadList.getCompound(i));
            manager.squads.put(squad.getId(), squad);
            for (UUID member : squad.getMembers().keySet()) {
                manager.playerSquad.put(member, squad.getId());
            }
        }
        ListTag dummyList = tag.getList("Dummies", Tag.TAG_INT_ARRAY);
        for (Tag t : dummyList) {
            manager.dummies.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        ListTag featureList = tag.getList("DisabledFeatures", Tag.TAG_STRING);
        for (int i = 0; i < featureList.size(); i++) {
            SquadFeature feature = SquadFeature.byKey(featureList.getString(i));
            if (feature != null) {
                manager.disabledFeatures.add(feature);
            }
        }
        if (tag.contains("ReviveCastSecondsOverride")) {
            manager.reviveCastSecondsOverride = tag.getDouble("ReviveCastSecondsOverride");
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag squadList = new ListTag();
        for (Squad squad : squads.values()) {
            squadList.add(squad.save());
        }
        tag.put("Squads", squadList);
        ListTag dummyList = new ListTag();
        for (UUID dummy : dummies) {
            dummyList.add(net.minecraft.nbt.NbtUtils.createUUID(dummy));
        }
        tag.put("Dummies", dummyList);
        ListTag featureList = new ListTag();
        for (SquadFeature feature : disabledFeatures) {
            featureList.add(net.minecraft.nbt.StringTag.valueOf(feature.key()));
        }
        tag.put("DisabledFeatures", featureList);
        if (reviveCastSecondsOverride != null) {
            tag.putDouble("ReviveCastSecondsOverride", reviveCastSecondsOverride);
        }
        return tag;
    }
}
