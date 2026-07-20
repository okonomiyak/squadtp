package uk.iwaservice.squadtp.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtp.client.ClientPacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Periodic broadcast of the current positions of all online squad members.
 * Sent only to members of the same squad.
 */
public record SquadMemberPosPacket(List<Entry> positions) {

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

    public static void handle(SquadMemberPosPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePositions(msg));
    }
}
