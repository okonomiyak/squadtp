package uk.iwaservice.squadtp.client;

import net.minecraft.client.Minecraft;
import uk.iwaservice.squadtp.client.gui.RespawnChoiceScreen;
import uk.iwaservice.squadtp.compat.JourneyMapCompat;
import uk.iwaservice.squadtp.network.RespawnChoicePacket;
import uk.iwaservice.squadtp.network.SquadMemberPosPacket;
import uk.iwaservice.squadtp.network.SquadSyncPacket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only entry points for the S2C packets. Never classloaded on a dedicated server. */
public final class ClientPacketHandler {

    public static void handleSync(SquadSyncPacket msg) {
        Map<UUID, String> members = new LinkedHashMap<>();
        for (SquadSyncPacket.Entry e : msg.members()) {
            members.put(e.uuid(), e.name());
        }
        SquadClientData.applySync(msg.inSquad(), msg.leader(), members, msg.rallyDim(), msg.rallyPos(),
                msg.joinRequests(), msg.invitedBy(), msg.beaconDim(), msg.beaconPos(), msg.beaconUsesRemaining());
        JourneyMapCompat.refresh();
    }

    public static void handlePositions(SquadMemberPosPacket msg) {
        Map<UUID, SquadClientData.MemberPos> positions = new LinkedHashMap<>();
        for (SquadMemberPosPacket.Entry e : msg.positions()) {
            positions.put(e.uuid(), new SquadClientData.MemberPos(e.uuid(), e.name(), e.dimension(), e.pos()));
        }
        SquadClientData.applyPositions(positions);
        JourneyMapCompat.refresh();
    }

    public static void handleDownedState(uk.iwaservice.squadtp.network.DownedStatePacket msg) {
        ClientReviveData.setDowned(msg.downed(), msg.remainingTicks());
    }

    public static void handleReviveProgress(uk.iwaservice.squadtp.network.ReviveProgressPacket msg) {
        ClientReviveData.setReviveProgress(msg.progressTicks(), msg.totalTicks());
    }

    public static void handleRespawnChoice(RespawnChoicePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            mc.setScreen(new RespawnChoiceScreen(msg));
        }
    }

    private ClientPacketHandler() {}
}
