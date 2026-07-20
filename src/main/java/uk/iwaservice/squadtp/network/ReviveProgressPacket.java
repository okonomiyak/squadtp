package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.ClientPacketHandler;

/** Revive channel progress for the HUD gauge; progressTicks &lt; 0 clears it. */
public record ReviveProgressPacket(int progressTicks, int totalTicks) {

    public static void encode(ReviveProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.progressTicks);
        buf.writeVarInt(msg.totalTicks);
    }

    public static ReviveProgressPacket decode(FriendlyByteBuf buf) {
        return new ReviveProgressPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ReviveProgressPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleReviveProgress(msg));
    }
}
