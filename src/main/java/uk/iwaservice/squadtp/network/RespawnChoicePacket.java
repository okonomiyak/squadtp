package uk.iwaservice.squadtp.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.ClientPacketHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sent right after a squad member respawns: opens the client-side respawn
 * chooser (with a map preview). The actual teleport is requested back via
 * /squad respawn and only honored inside the server-tracked respawn window.
 */
public record RespawnChoicePacket(@Nullable ResourceLocation rallyDim,
                                  @Nullable BlockPos rallyPos,
                                  List<Entry> members,
                                  int windowSeconds,
                                  @Nullable ResourceLocation beaconDim,
                                  @Nullable BlockPos beaconPos,
                                  int beaconUsesRemaining) {

    public record Entry(UUID uuid, String name, ResourceLocation dimension, BlockPos pos) {}

    public boolean hasRally() {
        return rallyDim != null && rallyPos != null;
    }

    public boolean hasBeacon() {
        return beaconDim != null && beaconPos != null;
    }

    public static void encode(RespawnChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasRally());
        if (msg.hasRally()) {
            buf.writeResourceLocation(msg.rallyDim);
            buf.writeBlockPos(msg.rallyPos);
        }
        buf.writeVarInt(msg.members.size());
        for (Entry e : msg.members) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.name());
            buf.writeResourceLocation(e.dimension());
            buf.writeBlockPos(e.pos());
        }
        buf.writeVarInt(msg.windowSeconds);
        buf.writeBoolean(msg.hasBeacon());
        if (msg.hasBeacon()) {
            buf.writeResourceLocation(msg.beaconDim);
            buf.writeBlockPos(msg.beaconPos);
            buf.writeVarInt(msg.beaconUsesRemaining);
        }
    }

    public static RespawnChoicePacket decode(FriendlyByteBuf buf) {
        ResourceLocation rallyDim = null;
        BlockPos rallyPos = null;
        if (buf.readBoolean()) {
            rallyDim = buf.readResourceLocation();
            rallyPos = buf.readBlockPos();
        }
        int count = buf.readVarInt();
        List<Entry> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(), buf.readBlockPos()));
        }
        int windowSeconds = buf.readVarInt();
        ResourceLocation beaconDim = null;
        BlockPos beaconPos = null;
        int beaconUsesRemaining = 0;
        if (buf.readBoolean()) {
            beaconDim = buf.readResourceLocation();
            beaconPos = buf.readBlockPos();
            beaconUsesRemaining = buf.readVarInt();
        }
        return new RespawnChoicePacket(rallyDim, rallyPos, members, windowSeconds, beaconDim, beaconPos, beaconUsesRemaining);
    }

    public static void handle(RespawnChoicePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleRespawnChoice(msg));
    }
}
