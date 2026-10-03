package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RespawnChoiceResultPacket(boolean success) implements CustomPacketPayload {

    public static final Type<RespawnChoiceResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "respawn_choice_result"));

    public static final StreamCodec<FriendlyByteBuf, RespawnChoiceResultPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBoolean(msg.success), buf -> new RespawnChoiceResultPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
