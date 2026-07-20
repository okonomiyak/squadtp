package uk.iwaservice.squadtp.compat.superbwarfare;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.squadtp.squad.ReviveSystem;

/**
 * Blocks SuperbWarfare projectiles (bullets, shells, rockets, ...) from
 * spawning when fired by a downed player.
 *
 * Unlike TACZ, SuperbWarfare has no cancelable "about to fire" event - its
 * {@code ShootEvent.Pre}/{@code Post} are notification-only and the firing
 * method never checks their cancellation state. Instead, this cancels the
 * projectile's spawn via the standard (vanilla) {@link EntityJoinLevelEvent}:
 * the gun still animates/plays its sound and consumes ammo, but the bullet
 * itself never appears, so it deals no damage. This only needs the vanilla
 * {@link Projectile} type (every SuperbWarfare projectile extends it) and
 * the mod's namespace string, so - unlike the TACZ guard - it has no
 * compile-time dependency on SuperbWarfare's own classes at all.
 */
public final class SuperbWarfareReviveGuard {

    private static final String NAMESPACE = "superbwarfare";

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if (typeId == null || !NAMESPACE.equals(typeId.getNamespace())) {
            return;
        }
        if (projectile.getOwner() instanceof ServerPlayer owner && ReviveSystem.isDowned(owner.getUUID())) {
            event.setCanceled(true);
        }
    }
}
