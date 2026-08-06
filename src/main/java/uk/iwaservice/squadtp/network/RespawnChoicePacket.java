package uk.iwaservice.squadtp.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
                                  int beaconUsesRemaining,
                                  List<ExternalEntry> external) implements CustomPacketPayload {

    public static final Type<RespawnChoicePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "respawn_choice"));

    public static final StreamCodec<FriendlyByteBuf, RespawnChoicePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), RespawnChoicePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(UUID uuid, String name, ResourceLocation dimension, BlockPos pos) {}

    /** A third-party {@link uk.iwaservice.squadtp.api.RespawnChoiceProvider}'s option. */
    public record ExternalEntry(String providerId, String choiceId, net.minecraft.network.chat.Component label,
                                 ResourceLocation dimension, BlockPos pos) {}

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
        buf.writeVarInt(msg.external.size());
        for (ExternalEntry e : msg.external) {
            buf.writeUtf(e.providerId());
            buf.writeUtf(e.choiceId());
            buf.writeComponent(e.label());
            buf.writeResourceLocation(e.dimension());
            buf.writeBlockPos(e.pos());
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
        int externalCount = buf.readVarInt();
        List<ExternalEntry> external = new ArrayList<>(externalCount);
        for (int i = 0; i < externalCount; i++) {
            external.add(new ExternalEntry(buf.readUtf(), buf.readUtf(), buf.readComponent(),
                    buf.readResourceLocation(), buf.readBlockPos()));
        }
        return new RespawnChoicePacket(rallyDim, rallyPos, members, windowSeconds, beaconDim, beaconPos,
                beaconUsesRemaining, external);
    }
}
