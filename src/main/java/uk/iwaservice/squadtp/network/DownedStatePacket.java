package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tells the receiving client that it is (or no longer is) downed. */
public record DownedStatePacket(boolean downed, int remainingTicks) implements CustomPacketPayload {

    public static final Type<DownedStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "downed_state"));

    public static final StreamCodec<FriendlyByteBuf, DownedStatePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DownedStatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(DownedStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.downed);
        buf.writeVarInt(msg.remainingTicks);
    }

    public static DownedStatePacket decode(FriendlyByteBuf buf) {
        return new DownedStatePacket(buf.readBoolean(), buf.readVarInt());
    }
}
