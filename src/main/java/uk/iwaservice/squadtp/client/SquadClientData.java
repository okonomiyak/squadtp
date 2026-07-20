package uk.iwaservice.squadtp.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side mirror of the player's squad, fed exclusively by S2C packets.
 * Read by the (optional) JourneyMap integration.
 */
public final class SquadClientData {

    public record MemberPos(UUID uuid, String name, ResourceLocation dimension, BlockPos pos) {}

    private static boolean inSquad;
    @Nullable
    private static UUID leader;
    /** Member UUID -> name (join order). */
    private static final Map<UUID, String> members = new LinkedHashMap<>();
    /** Latest known positions of online members. */
    private static final Map<UUID, MemberPos> positions = new HashMap<>();
    @Nullable
    private static ResourceLocation rallyDimension;
    @Nullable
    private static BlockPos rallyPos;
    /** Pending join-request applicant names (shown to the leader). */
    private static java.util.List<String> joinRequests = java.util.List.of();
    /** Who invited us, when we are not in a squad and an invite is pending. */
    @Nullable
    private static String invitedBy;
    /** Incremented on every data change; lets the GUI detect updates cheaply. */
    private static int revision;

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized void applySync(boolean nowInSquad, @Nullable UUID newLeader,
                                              Map<UUID, String> newMembers,
                                              @Nullable ResourceLocation newRallyDim, @Nullable BlockPos newRallyPos,
                                              java.util.List<String> newJoinRequests, @Nullable String newInvitedBy) {
        inSquad = nowInSquad;
        leader = newLeader;
        members.clear();
        members.putAll(newMembers);
        positions.keySet().retainAll(members.keySet());
        rallyDimension = newRallyDim;
        rallyPos = newRallyPos;
        joinRequests = java.util.List.copyOf(newJoinRequests);
        invitedBy = newInvitedBy;
        revision++;
    }

    public static synchronized void applyPositions(Map<UUID, MemberPos> newPositions) {
        positions.clear();
        for (Map.Entry<UUID, MemberPos> e : newPositions.entrySet()) {
            if (members.containsKey(e.getKey())) {
                positions.put(e.getKey(), e.getValue());
            }
        }
        revision++;
    }

    public static synchronized void clear() {
        inSquad = false;
        leader = null;
        members.clear();
        positions.clear();
        rallyDimension = null;
        rallyPos = null;
        joinRequests = java.util.List.of();
        invitedBy = null;
        revision++;
    }

    public static synchronized java.util.List<String> getJoinRequests() {
        return joinRequests;
    }

    @Nullable
    public static synchronized String getInvitedBy() {
        return invitedBy;
    }

    public static synchronized boolean isInSquad() {
        return inSquad;
    }

    @Nullable
    public static synchronized UUID getLeader() {
        return leader;
    }

    public static synchronized Map<UUID, String> getMembers() {
        return new LinkedHashMap<>(members);
    }

    public static synchronized Map<UUID, MemberPos> getPositions() {
        return new HashMap<>(positions);
    }

    @Nullable
    public static synchronized ResourceLocation getRallyDimension() {
        return rallyDimension;
    }

    @Nullable
    public static synchronized BlockPos getRallyPos() {
        return rallyPos;
    }

    private SquadClientData() {}
}
