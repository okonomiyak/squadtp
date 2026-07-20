package uk.iwaservice.squadtp.compat.tacz;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import uk.iwaservice.squadtp.client.ClientReviveData;
import uk.iwaservice.squadtp.squad.ReviveSystem;

/**
 * Blocks TACZ gun firing while the shooter is downed. Instantiated (and its
 * compile-time reference to TACZ's classes resolved) only from
 * {@link uk.iwaservice.squadtp.compat.TaczCompat}, after confirming TACZ is
 * loaded - so this class is never touched when TACZ is absent.
 */
public final class TaczReviveGuard {

    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        if (isDowned(event.getLogicalSide(), event.getShooter())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGunShoot(GunShootEvent event) {
        if (isDowned(event.getLogicalSide(), event.getShooter())) {
            event.setCanceled(true);
        }
    }

    /**
     * Server side is authoritative via {@link ReviveSystem}; the client side
     * has no visibility into other entities' downed state, so it only ever
     * suppresses its own local player's shot (mirrors the existing
     * client-side input suppression used elsewhere for the downed state).
     */
    private static boolean isDowned(LogicalSide side, LivingEntity shooter) {
        if (side == LogicalSide.SERVER) {
            return shooter instanceof ServerPlayer player && ReviveSystem.isDowned(player.getUUID());
        }
        return ClientReviveData.getDownedRemainingTicks() >= 0;
    }
}
