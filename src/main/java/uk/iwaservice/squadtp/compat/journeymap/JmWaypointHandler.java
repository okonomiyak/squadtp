package uk.iwaservice.squadtp.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtp.SquadTp;
import uk.iwaservice.squadtp.client.SquadClientData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Renders squad member positions and the rally point as JourneyMap waypoints. */
public final class JmWaypointHandler {

    private record Wp(String name, ResourceLocation dimension, BlockPos pos, int color) {}

    /** What JourneyMap currently shows, so an unchanged sync doesn't rebuild every waypoint. */
    private static List<Wp> shown = List.of();

    public static void refresh() {
        IClientAPI api = SquadJmPlugin.api();
        if (api == null) {
            return;
        }
        List<Wp> desired = new ArrayList<>();
        if (!SquadClientData.isInSquad()) {
            update(api, desired);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player != null ? mc.player.getUUID() : null;

        Map<UUID, String> members = SquadClientData.getMembers();
        Map<UUID, SquadClientData.MemberPos> positions = SquadClientData.getPositions();

        int slot = 0;
        for (UUID member : members.keySet()) {
            int color = uk.iwaservice.squadtp.client.SquadColors.memberColor(slot);
            slot++;
            if (member.equals(self)) {
                continue; // JourneyMap already shows the local player
            }
            SquadClientData.MemberPos pos = positions.get(member);
            if (pos == null) {
                continue; // offline or no position received yet
            }
            desired.add(new Wp(pos.name(), pos.dimension(), pos.pos(), color));
        }

        ResourceLocation rallyDim = SquadClientData.getRallyDimension();
        BlockPos rallyPos = SquadClientData.getRallyPos();
        if (rallyDim != null && rallyPos != null) {
            desired.add(new Wp("Rally", rallyDim, rallyPos,
                    uk.iwaservice.squadtp.client.SquadColors.RALLY_COLOR));
        }

        if (SquadClientData.hasBeacon()) {
            desired.add(new Wp("Beacon", SquadClientData.getBeaconDimension(), SquadClientData.getBeaconPos(),
                    uk.iwaservice.squadtp.client.SquadColors.BEACON_COLOR));
        }
        update(api, desired);
    }

    private static void update(IClientAPI api, List<Wp> desired) {
        if (desired.equals(shown)) {
            return;
        }
        api.removeAllWaypoints(SquadTp.MODID);
        for (Wp w : desired) {
            show(api, waypoint(w.name(), w.dimension(), w.pos(), w.color()));
        }
        shown = desired;
    }

    private static Waypoint waypoint(String name, ResourceLocation dimension, BlockPos pos, int color) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        Waypoint waypoint = WaypointFactory.createWaypoint(SquadTp.MODID, pos, name, dimKey, false);
        waypoint.setColor(color);
        return waypoint;
    }

    private static void show(IClientAPI api, Waypoint waypoint) {
        try {
            api.addWaypoint(SquadTp.MODID, waypoint);
        } catch (Exception e) {
            SquadTp.LOGGER.warn("Failed to show squad waypoint {}", waypoint.getName(), e);
        }
    }

    private JmWaypointHandler() {}
}
