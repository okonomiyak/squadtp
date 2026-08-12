package uk.iwaservice.squadtp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Other squads a squadless player could request to join, for the recruit tab's "request to join"
 * list. Sent only to players not currently in a squad (see {@code ServerEvents.broadcastSquadList}),
 * one entry per squad rather than one per player, so the GUI can group by squad instead of listing
 * every member as a separate (and mostly redundant) candidate.
 */
public record SquadListPacket(List<Entry> squads) implements CustomPacketPayload {

    /** One joinable squad: {@code leaderName} is the {@code /squad join <player>} target. */
    public record Entry(String leaderName, List<String> memberNames) {}

    public static final Type<SquadListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtp.SquadTp.MODID, "squad_list"));

    public static final StreamCodec<FriendlyByteBuf, SquadListPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SquadListPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(SquadListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.squads.size());
        for (Entry e : msg.squads) {
            buf.writeUtf(e.leaderName());
            buf.writeVarInt(e.memberNames().size());
            for (String name : e.memberNames()) {
                buf.writeUtf(name);
            }
        }
    }

    public static SquadListPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> squads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String leaderName = buf.readUtf();
            int memberCount = buf.readVarInt();
            List<String> memberNames = new ArrayList<>(memberCount);
            for (int j = 0; j < memberCount; j++) {
                memberNames.add(buf.readUtf());
            }
            squads.add(new Entry(leaderName, memberNames));
        }
        return new SquadListPacket(squads);
    }
}
