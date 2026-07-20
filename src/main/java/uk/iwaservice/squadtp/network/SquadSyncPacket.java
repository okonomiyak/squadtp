package uk.iwaservice.squadtp.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.ClientPacketHandler;
import uk.iwaservice.squadtp.squad.Squad;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full squad state pushed to a member whenever membership, leadership,
 * rally point or pending join requests change. An "empty" packet tells the
 * client it has no squad; the {@code invitedBy} variant additionally tells a
 * non-member that a squad invite is waiting for them.
 */
public record SquadSyncPacket(boolean inSquad,
                              @Nullable UUID squadId,
                              @Nullable UUID leader,
                              List<Entry> members,
                              @Nullable ResourceLocation rallyDim,
                              @Nullable BlockPos rallyPos,
                              List<String> joinRequests,
                              @Nullable String invitedBy,
                              @Nullable ResourceLocation beaconDim,
                              @Nullable BlockPos beaconPos,
                              int beaconUsesRemaining) {

    public record Entry(UUID uuid, String name) {}

    public static SquadSyncPacket of(Squad squad) {
        List<Entry> members = new ArrayList<>();
        for (Map.Entry<UUID, String> e : squad.getMembers().entrySet()) {
            members.add(new Entry(e.getKey(), e.getValue()));
        }
        ResourceLocation rallyDim = squad.hasRally() ? squad.getRallyDimension().location() : null;
        ResourceLocation beaconDim = squad.hasBeacon() ? squad.getBeaconDimension().location() : null;
        return new SquadSyncPacket(true, squad.getId(), squad.getLeader(), members,
                rallyDim, squad.getRallyPos(), squad.getJoinRequestNames(), null,
                beaconDim, squad.getBeaconPos(), squad.getBeaconUsesRemaining());
    }

    public static SquadSyncPacket empty() {
        return new SquadSyncPacket(false, null, null, List.of(), null, null, List.of(), null, null, null, 0);
    }

    /** Sent to a non-member to surface a pending invite in their GUI. */
    public static SquadSyncPacket invited(String inviterName) {
        return new SquadSyncPacket(false, null, null, List.of(), null, null, List.of(), inviterName, null, null, 0);
    }

    public static void encode(SquadSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.inSquad);
        if (msg.inSquad) {
            buf.writeUUID(msg.squadId);
            buf.writeUUID(msg.leader);
            buf.writeVarInt(msg.members.size());
            for (Entry e : msg.members) {
                buf.writeUUID(e.uuid());
                buf.writeUtf(e.name());
            }
            buf.writeBoolean(msg.rallyDim != null && msg.rallyPos != null);
            if (msg.rallyDim != null && msg.rallyPos != null) {
                buf.writeResourceLocation(msg.rallyDim);
                buf.writeBlockPos(msg.rallyPos);
            }
            buf.writeVarInt(msg.joinRequests.size());
            for (String name : msg.joinRequests) {
                buf.writeUtf(name);
            }
            buf.writeBoolean(msg.beaconDim != null && msg.beaconPos != null);
            if (msg.beaconDim != null && msg.beaconPos != null) {
                buf.writeResourceLocation(msg.beaconDim);
                buf.writeBlockPos(msg.beaconPos);
                buf.writeVarInt(msg.beaconUsesRemaining);
            }
        } else {
            buf.writeBoolean(msg.invitedBy != null);
            if (msg.invitedBy != null) {
                buf.writeUtf(msg.invitedBy);
            }
        }
    }

    public static SquadSyncPacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            String invitedBy = buf.readBoolean() ? buf.readUtf() : null;
            return new SquadSyncPacket(false, null, null, List.of(), null, null, List.of(), invitedBy, null, null, 0);
        }
        UUID squadId = buf.readUUID();
        UUID leader = buf.readUUID();
        int count = buf.readVarInt();
        List<Entry> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Entry(buf.readUUID(), buf.readUtf()));
        }
        ResourceLocation rallyDim = null;
        BlockPos rallyPos = null;
        if (buf.readBoolean()) {
            rallyDim = buf.readResourceLocation();
            rallyPos = buf.readBlockPos();
        }
        int requestCount = buf.readVarInt();
        List<String> joinRequests = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            joinRequests.add(buf.readUtf());
        }
        ResourceLocation beaconDim = null;
        BlockPos beaconPos = null;
        int beaconUsesRemaining = 0;
        if (buf.readBoolean()) {
            beaconDim = buf.readResourceLocation();
            beaconPos = buf.readBlockPos();
            beaconUsesRemaining = buf.readVarInt();
        }
        return new SquadSyncPacket(true, squadId, leader, members, rallyDim, rallyPos, joinRequests, null,
                beaconDim, beaconPos, beaconUsesRemaining);
    }

    public static void handle(SquadSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
    }
}
