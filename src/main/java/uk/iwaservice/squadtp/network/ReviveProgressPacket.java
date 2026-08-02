package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Revive channel progress for the HUD gauge; progressTicks &lt; 0 clears it. */
public record ReviveProgressPacket(int progressTicks, int totalTicks) implements CustomPacketPayload {

    public static final Type<ReviveProgressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "revive_progress"));

    public static final StreamCodec<FriendlyByteBuf, ReviveProgressPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), ReviveProgressPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ReviveProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.progressTicks);
        buf.writeVarInt(msg.totalTicks);
    }

    public static ReviveProgressPacket decode(FriendlyByteBuf buf) {
        return new ReviveProgressPacket(buf.readVarInt(), buf.readVarInt());
    }
}
