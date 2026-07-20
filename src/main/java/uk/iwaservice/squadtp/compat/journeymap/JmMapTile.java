package uk.iwaservice.squadtp.compat.journeymap;

import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtp.SquadTp;

import java.util.function.Consumer;

/** Fetches a day-map tile image around a point from JourneyMap's stored map data. */
public final class JmMapTile {

    /**
     * Requests the tile covering {@code center ± radiusBlocks} (chunk-aligned,
     * same alignment the caller must use to project markers). The callback may
     * fire on a background thread. Returns false if the request was not made.
     */
    public static boolean request(ResourceLocation dim, BlockPos center, int radiusBlocks, int zoom,
                                  Consumer<NativeImage> callback) {
        IClientAPI api = SquadJmPlugin.api();
        if (api == null) {
            return false;
        }
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            ChunkPos start = new ChunkPos((center.getX() - radiusBlocks) >> 4, (center.getZ() - radiusBlocks) >> 4);
            ChunkPos end = new ChunkPos((center.getX() + radiusBlocks) >> 4, (center.getZ() + radiusBlocks) >> 4);
            api.requestMapTile(SquadTp.MODID, key, Context.MapType.Day, start, end, null, zoom, false, callback);
            return true;
        } catch (Throwable t) {
            SquadTp.LOGGER.warn("JourneyMap map tile request failed", t);
            return false;
        }
    }

    private JmMapTile() {}
}
