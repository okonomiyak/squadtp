package uk.iwaservice.squadtp.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.gui.RespawnChoiceScreen;

import java.util.function.Supplier;

public record RespawnChoiceResultPacket(boolean success) {

    public static void encode(RespawnChoiceResultPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.success);
    }

    public static RespawnChoiceResultPacket decode(FriendlyByteBuf buf) {
        return new RespawnChoiceResultPacket(buf.readBoolean());
    }

    public static void handle(RespawnChoiceResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (packet.success() && minecraft.screen instanceof RespawnChoiceScreen) {
                minecraft.screen.onClose();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
