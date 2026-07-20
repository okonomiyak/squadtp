package uk.iwaservice.squadtp.compat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtp.SquadTp;

/**
 * Sole gateway into the SuperbWarfare (gun mod) integration. Kept separate
 * from {@link uk.iwaservice.squadtp.compat.superbwarfare.SuperbWarfareReviveGuard}
 * only for consistency with the other compat gates in this package - the
 * guard itself has no compile-time reference to SuperbWarfare, so the
 * {@code isLoaded} check here exists purely so the feature stays off (and
 * never intercepts unrelated mods' projectiles) when SuperbWarfare isn't installed.
 */
public final class SuperbWarfareCompat {

    public static void init() {
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new uk.iwaservice.squadtp.compat.superbwarfare.SuperbWarfareReviveGuard());
        SquadTp.LOGGER.info("SuperbWarfare integration initialized: guns are disabled while downed");
    }

    private SuperbWarfareCompat() {}
}
