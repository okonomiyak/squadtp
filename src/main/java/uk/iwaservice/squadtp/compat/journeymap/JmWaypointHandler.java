package uk.iwaservice.squadtp.compat.journeymap;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtp.SquadTp;
import uk.iwaservice.squadtp.client.SquadClientData;

import java.util.Map;
import java.util.UUID;

/** Renders squad member positions and the rally point as JourneyMap waypoints. */
public final class JmWaypointHandler {

    public static void refresh() {
        IClientAPI api = SquadJmPlugin.api();
        if (api == null) {
            return;
        }
        api.removeAll(SquadTp.MODID);

        if (!SquadClientData.isInSquad() || !api.playerAccepts(SquadTp.MODID, DisplayType.Waypoint)) {
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
            show(api, waypoint("member_" + member, pos.name(), pos.dimension(), pos.pos(), color));
        }

        ResourceLocation rallyDim = SquadClientData.getRallyDimension();
        BlockPos rallyPos = SquadClientData.getRallyPos();
        if (rallyDim != null && rallyPos != null) {
            show(api, waypoint("rally", "Rally", rallyDim, rallyPos,
                    uk.iwaservice.squadtp.client.SquadColors.RALLY_COLOR));
        }
    }

    private static Waypoint waypoint(String id, String name, ResourceLocation dimension, BlockPos pos, int color) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return new Waypoint(SquadTp.MODID, id, name, dimKey, pos)
                .setColor(color)
                .setPersistent(false)
                .setEditable(false);
    }

    private static void show(IClientAPI api, Waypoint waypoint) {
        try {
            api.show(waypoint);
        } catch (Exception e) {
            SquadTp.LOGGER.warn("Failed to show squad waypoint {}", waypoint.getName(), e);
        }
    }

    private JmWaypointHandler() {}
}
