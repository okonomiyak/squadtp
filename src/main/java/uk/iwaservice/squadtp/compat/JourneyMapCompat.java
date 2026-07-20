package uk.iwaservice.squadtp.compat;

import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtp.SquadTp;

/**
 * Sole gateway into the JourneyMap integration. Classes under
 * {@code compat.journeymap} reference JourneyMap API types and are only
 * classloaded behind the {@code isLoaded} check below, so the mod works
 * unchanged when JourneyMap is not installed.
 */
public final class JourneyMapCompat {

    private static Boolean loaded;
    private static boolean broken;

    private static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("journeymap");
        }
        return loaded && !broken;
    }

    /** Re-renders all squad waypoints from the current client data. Safe to call anywhere on the client. */
    public static void refresh() {
        if (!isLoaded()) {
            return;
        }
        try {
            uk.iwaservice.squadtp.compat.journeymap.JmWaypointHandler.refresh();
        } catch (Throwable t) {
            broken = true;
            SquadTp.LOGGER.error("JourneyMap integration failed, disabling it for this session", t);
        }
    }

    /**
     * Requests a map tile image around {@code center} from JourneyMap.
     * Returns false (and never fires the callback) when JourneyMap is absent,
     * letting the caller fall back to its own rendering.
     */
    public static boolean requestMapTile(net.minecraft.resources.ResourceLocation dim,
                                         net.minecraft.core.BlockPos center, int radiusBlocks, int zoom,
                                         java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> callback) {
        if (!isLoaded()) {
            return false;
        }
        try {
            return uk.iwaservice.squadtp.compat.journeymap.JmMapTile.request(dim, center, radiusBlocks, zoom, callback);
        } catch (Throwable t) {
            broken = true;
            SquadTp.LOGGER.error("JourneyMap integration failed, disabling it for this session", t);
            return false;
        }
    }

    private JourneyMapCompat() {}
}
