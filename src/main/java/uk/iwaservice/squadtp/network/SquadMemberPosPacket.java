package uk.iwaservice.squadtp.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Periodic broadcast of the current positions of all online squad members.
 * Sent only to members of the same squad.
 */
public record SquadMemberPosPacket(List<Entry> positions) implements CustomPacketPayload {

    public static final Type<SquadMemberPosPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "squad_member_pos"));

    public static final StreamCodec<FriendlyByteBuf, SquadMemberPosPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SquadMemberPosPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(UUID uuid, String name, ResourceLocation dimension, BlockPos pos) {}

    public static void encode(SquadMemberPosPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.positions.size());
        for (Entry e : msg.positions) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.name());
            buf.writeResourceLocation(e.dimension());
            buf.writeBlockPos(e.pos());
        }
    }

    public static SquadMemberPosPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(), buf.readBlockPos()));
        }
        return new SquadMemberPosPacket(positions);
    }
}
