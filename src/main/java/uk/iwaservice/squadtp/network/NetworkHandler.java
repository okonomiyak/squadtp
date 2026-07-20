package uk.iwaservice.squadtp.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.iwaservice.squadtp.SquadTp;
import uk.iwaservice.squadtp.squad.Squad;

import java.util.UUID;

/**
 * Server-to-client only channel. Clients never send squad packets; all squad
 * actions go through commands, which are validated server-side.
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SquadTp.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(SquadSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SquadSyncPacket::encode)
                .decoder(SquadSyncPacket::decode)
                .consumerMainThread(SquadSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(SquadMemberPosPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SquadMemberPosPacket::encode)
                .decoder(SquadMemberPosPacket::decode)
                .consumerMainThread(SquadMemberPosPacket::handle)
                .add();
        CHANNEL.messageBuilder(RespawnChoicePacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RespawnChoicePacket::encode)
                .decoder(RespawnChoicePacket::decode)
                .consumerMainThread(RespawnChoicePacket::handle)
                .add();
        CHANNEL.messageBuilder(DownedStatePacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownedStatePacket::encode)
                .decoder(DownedStatePacket::decode)
                .consumerMainThread(DownedStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(ReviveProgressPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReviveProgressPacket::encode)
                .decoder(ReviveProgressPacket::decode)
                .consumerMainThread(ReviveProgressPacket::handle)
                .add();
    }

    public static void sendDownedState(ServerPlayer player, boolean downed, int remainingTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DownedStatePacket(downed, remainingTicks));
    }

    public static void sendReviveProgress(ServerPlayer player, int progressTicks, int totalTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ReviveProgressPacket(progressTicks, totalTicks));
    }

    public static void sendRespawnChoice(ServerPlayer player, RespawnChoicePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendSquadSync(ServerPlayer player, Squad squad) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SquadSyncPacket.of(squad));
    }

    /** Tells a (possibly offline) player's client that they are no longer in a squad. */
    public static void sendEmptySync(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> online), SquadSyncPacket.empty());
        }
    }

    public static void sendPositions(ServerPlayer player, SquadMemberPosPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Surfaces a pending invite in a non-member's GUI. */
    public static void sendInvited(ServerPlayer player, String inviterName) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SquadSyncPacket.invited(inviterName));
    }

    private NetworkHandler() {}
}
