package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.ClientPacketHandler;

/** Tells the receiving client that it is (or no longer is) downed. */
public record DownedStatePacket(boolean downed, int remainingTicks) {

    public static void encode(DownedStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.downed);
        buf.writeVarInt(msg.remainingTicks);
    }

    public static DownedStatePacket decode(FriendlyByteBuf buf) {
        return new DownedStatePacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(DownedStatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleDownedState(msg));
    }
}
