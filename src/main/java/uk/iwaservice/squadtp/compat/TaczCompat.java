package uk.iwaservice.squadtp.compat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtp.SquadTp;

/**
 * Sole gateway into the TACZ (gun mod) integration. The guard class under
 * {@code compat.tacz} references TACZ API types and is only classloaded
 * behind the {@code isLoaded} check below, so the mod works unchanged when
 * TACZ is not installed.
 */
public final class TaczCompat {

    /** Call once during common setup, after all mods have been discovered. */
    public static void init() {
        if (!ModList.get().isLoaded("tacz")) {
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.register(new uk.iwaservice.squadtp.compat.tacz.TaczReviveGuard());
            SquadTp.LOGGER.info("TACZ integration initialized: guns are disabled while downed");
        } catch (Throwable t) {
            SquadTp.LOGGER.error("TACZ integration failed to initialize", t);
        }
    }

    private TaczCompat() {}
}
