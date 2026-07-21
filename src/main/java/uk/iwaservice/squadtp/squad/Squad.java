package uk.iwaservice.squadtp.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative squad state. All mutation goes through {@link SquadManager}.
 */
public class Squad {
    private final UUID id;
    private UUID leader;
    /** Member UUID -> last known player name (insertion order = join order). */
    private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
    /** Invited UUID -> expiry (epoch millis). */
    private final Map<UUID, Long> pendingInvites = new LinkedHashMap<>();
    /** Join-request applicant UUID -> (name, expiry). */
    private final Map<UUID, JoinRequest> joinRequests = new LinkedHashMap<>();

    public record JoinRequest(String name, long expiry) {}

    @Nullable
    private ResourceKey<Level> rallyDimension;
    @Nullable
    private BlockPos rallyPos;

    /** Active respawn beacon, if any. At most one per squad; placing a new one replaces it. */
    @Nullable
    private UUID beaconEntityId;
    @Nullable
    private ResourceKey<Level> beaconDimension;
    @Nullable
    private BlockPos beaconPos;
    private int beaconUsesRemaining;

    /** If true, /squad join immediately admits the applicant instead of requiring leader approval. */
    private boolean openJoin;

    public Squad(UUID id, UUID leader, String leaderName, boolean openJoin) {
        this.id = id;
        this.leader = leader;
        this.members.put(leader, leaderName);
        this.openJoin = openJoin;
    }

    private Squad(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public int size() {
        return members.size();
    }

    /** Member UUID -> last known name, join order. */
    public Map<UUID, String> getMembers() {
        return members;
    }

    public String getMemberName(UUID player) {
        return members.getOrDefault(player, player.toString());
    }

    void addMember(UUID player, String name) {
        members.put(player, name);
    }

    void removeMember(UUID player) {
        members.remove(player);
    }

    void setLeader(UUID player) {
        this.leader = player;
    }

    public void updateName(UUID player, String name) {
        if (members.containsKey(player)) {
            members.put(player, name);
        }
    }

    @Nullable
    public UUID findMemberByName(String name) {
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Oldest eligible member other than the current leader, used for automatic
     * leadership transfer. Dummy members are filtered out via {@code eligible}.
     */
    @Nullable
    public UUID successor(java.util.function.Predicate<UUID> eligible) {
        for (UUID member : members.keySet()) {
            if (!member.equals(leader) && eligible.test(member)) {
                return member;
            }
        }
        return null;
    }

    // --- invites ---

    void putInvite(UUID player, long expiryMillis) {
        pendingInvites.put(player, expiryMillis);
    }

    boolean removeInvite(UUID player) {
        return pendingInvites.remove(player) != null;
    }

    /** True if the player has a non-expired invite. Expired entries are pruned. */
    boolean hasValidInvite(UUID player, long nowMillis) {
        pruneInvites(nowMillis);
        return pendingInvites.containsKey(player);
    }

    void pruneInvites(long nowMillis) {
        Iterator<Map.Entry<UUID, Long>> it = pendingInvites.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < nowMillis) {
                it.remove();
            }
        }
    }

    // --- join requests ---

    void putJoinRequest(UUID player, String name, long expiryMillis) {
        joinRequests.put(player, new JoinRequest(name, expiryMillis));
    }

    boolean removeJoinRequest(UUID player) {
        return joinRequests.remove(player) != null;
    }

    @Nullable
    public UUID findJoinRequestByName(String name) {
        pruneJoinRequests(System.currentTimeMillis());
        for (Map.Entry<UUID, JoinRequest> e : joinRequests.entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Names of players with a pending (non-expired) join request, oldest first. */
    public java.util.List<String> getJoinRequestNames() {
        pruneJoinRequests(System.currentTimeMillis());
        return joinRequests.values().stream().map(JoinRequest::name).toList();
    }

    void pruneJoinRequests(long nowMillis) {
        Iterator<Map.Entry<UUID, JoinRequest>> it = joinRequests.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiry() < nowMillis) {
                it.remove();
            }
        }
    }

    // --- rally point ---

    @Nullable
    public ResourceKey<Level> getRallyDimension() {
        return rallyDimension;
    }

    @Nullable
    public BlockPos getRallyPos() {
        return rallyPos;
    }

    public boolean hasRally() {
        return rallyDimension != null && rallyPos != null;
    }

    void setRally(ResourceKey<Level> dimension, BlockPos pos) {
        this.rallyDimension = dimension;
        this.rallyPos = pos.immutable();
    }

    // --- join policy ---

    public boolean isOpenJoin() {
        return openJoin;
    }

    void setOpenJoin(boolean openJoin) {
        this.openJoin = openJoin;
    }

    // --- respawn beacon ---

    @Nullable
    public UUID getBeaconEntityId() {
        return beaconEntityId;
    }

    @Nullable
    public ResourceKey<Level> getBeaconDimension() {
        return beaconDimension;
    }

    @Nullable
    public BlockPos getBeaconPos() {
        return beaconPos;
    }

    public boolean hasBeacon() {
        return beaconEntityId != null;
    }

    public int getBeaconUsesRemaining() {
        return beaconUsesRemaining;
    }

    void setBeacon(UUID entityId, ResourceKey<Level> dimension, BlockPos pos, int usesRemaining) {
        this.beaconEntityId = entityId;
        this.beaconDimension = dimension;
        this.beaconPos = pos.immutable();
        this.beaconUsesRemaining = usesRemaining;
    }

    /** Returns the remaining uses after decrementing. */
    int decrementBeaconUses() {
        return --beaconUsesRemaining;
    }

    void clearBeacon() {
        this.beaconEntityId = null;
        this.beaconDimension = null;
        this.beaconPos = null;
        this.beaconUsesRemaining = 0;
    }

    // --- persistence ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Leader", leader);
        tag.putBoolean("OpenJoin", openJoin);

        ListTag memberList = new ListTag();
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("Uuid", e.getKey());
            m.putString("Name", e.getValue());
            memberList.add(m);
        }
        tag.put("Members", memberList);

        ListTag inviteList = new ListTag();
        for (Map.Entry<UUID, Long> e : pendingInvites.entrySet()) {
            CompoundTag i = new CompoundTag();
            i.putUUID("Uuid", e.getKey());
            i.putLong("Expiry", e.getValue());
            inviteList.add(i);
        }
        tag.put("Invites", inviteList);

        ListTag requestList = new ListTag();
        for (Map.Entry<UUID, JoinRequest> e : joinRequests.entrySet()) {
            CompoundTag r = new CompoundTag();
            r.putUUID("Uuid", e.getKey());
            r.putString("Name", e.getValue().name());
            r.putLong("Expiry", e.getValue().expiry());
            requestList.add(r);
        }
        tag.put("JoinRequests", requestList);

        if (hasRally()) {
            tag.putString("RallyDim", rallyDimension.location().toString());
            tag.put("RallyPos", NbtUtils.writeBlockPos(rallyPos));
        }

        if (hasBeacon()) {
            tag.putUUID("BeaconEntityId", beaconEntityId);
            tag.putString("BeaconDim", beaconDimension.location().toString());
            tag.put("BeaconPos", NbtUtils.writeBlockPos(beaconPos));
            tag.putInt("BeaconUses", beaconUsesRemaining);
        }
        return tag;
    }

    public static Squad load(CompoundTag tag) {
        Squad squad = new Squad(tag.getUUID("Id"));
        squad.leader = tag.getUUID("Leader");
        // Older saves predate this field; default matches the pre-feature behavior (approval required).
        squad.openJoin = tag.contains("OpenJoin") && tag.getBoolean("OpenJoin");

        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag m = memberList.getCompound(i);
            squad.members.put(m.getUUID("Uuid"), m.getString("Name"));
        }

        ListTag inviteList = tag.getList("Invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < inviteList.size(); i++) {
            CompoundTag inv = inviteList.getCompound(i);
            squad.pendingInvites.put(inv.getUUID("Uuid"), inv.getLong("Expiry"));
        }

        ListTag requestList = tag.getList("JoinRequests", Tag.TAG_COMPOUND);
        for (int i = 0; i < requestList.size(); i++) {
            CompoundTag r = requestList.getCompound(i);
            squad.joinRequests.put(r.getUUID("Uuid"), new JoinRequest(r.getString("Name"), r.getLong("Expiry")));
        }

        if (tag.contains("RallyDim")) {
            squad.rallyDimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("RallyDim")));
            squad.rallyPos = NbtUtils.readBlockPos(tag.getCompound("RallyPos"));
        }

        if (tag.contains("BeaconEntityId")) {
            squad.beaconEntityId = tag.getUUID("BeaconEntityId");
            squad.beaconDimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("BeaconDim")));
            squad.beaconPos = NbtUtils.readBlockPos(tag.getCompound("BeaconPos"));
            squad.beaconUsesRemaining = tag.getInt("BeaconUses");
        }
        return squad;
    }
}
