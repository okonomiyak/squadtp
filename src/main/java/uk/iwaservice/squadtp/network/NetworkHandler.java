package uk.iwaservice.squadtp.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import uk.iwaservice.squadtp.client.ClientPacketHandler;
import uk.iwaservice.squadtp.squad.Squad;

import java.util.UUID;

/**
 * Server-to-client only channel. Clients never send squad packets; all squad
 * actions go through commands, which are validated server-side.
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(SquadSyncPacket.TYPE, SquadSyncPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleSync(msg));
        registrar.playToClient(SquadMemberPosPacket.TYPE, SquadMemberPosPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handlePositions(msg));
        registrar.playToClient(RespawnChoicePacket.TYPE, RespawnChoicePacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleRespawnChoice(msg));
        registrar.playToClient(DownedStatePacket.TYPE, DownedStatePacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleDownedState(msg));
        registrar.playToClient(ReviveProgressPacket.TYPE, ReviveProgressPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleReviveProgress(msg));
        registrar.playToClient(SquadListPacket.TYPE, SquadListPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleSquadList(msg));
    }

    public static void sendDownedState(ServerPlayer player, boolean downed, int remainingTicks) {
        PacketDistributor.sendToPlayer(player, new DownedStatePacket(downed, remainingTicks));
    }

    public static void sendReviveProgress(ServerPlayer player, int progressTicks, int totalTicks) {
        PacketDistributor.sendToPlayer(player, new ReviveProgressPacket(progressTicks, totalTicks));
    }

    public static void sendRespawnChoice(ServerPlayer player, RespawnChoicePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendSquadSync(ServerPlayer player, Squad squad) {
        PacketDistributor.sendToPlayer(player, SquadSyncPacket.of(squad));
    }

    /** Tells a (possibly offline) player's client that they are no longer in a squad. */
    public static void sendEmptySync(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            PacketDistributor.sendToPlayer(online, SquadSyncPacket.empty());
        }
    }

    public static void sendPositions(ServerPlayer player, SquadMemberPosPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    /** Surfaces a pending invite in a non-member's GUI. */
    public static void sendInvited(ServerPlayer player, String inviterName) {
        PacketDistributor.sendToPlayer(player, SquadSyncPacket.invited(inviterName));
    }

    public static void sendSquadList(ServerPlayer player, SquadListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    private NetworkHandler() {}
}
