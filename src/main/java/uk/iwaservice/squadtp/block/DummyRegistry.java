package uk.iwaservice.squadtp.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live (non-persistent) registry of currently loaded dummy player blocks.
 * A dummy that is not registered here counts as "offline", exactly like a
 * logged-out player; its squad membership is kept in {@code SquadManager}.
 */
public final class DummyRegistry {

    public record Entry(String name, ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, Entry> LOADED = new ConcurrentHashMap<>();

    public static void register(UUID id, Entry entry) {
        LOADED.put(id, entry);
    }

    public static void unregister(UUID id) {
        LOADED.remove(id);
    }

    @Nullable
    public static Entry get(UUID id) {
        return LOADED.get(id);
    }

    public static boolean isLoaded(UUID id) {
        return LOADED.containsKey(id);
    }

    public static void clear() {
        LOADED.clear();
    }

    private DummyRegistry() {}
}
