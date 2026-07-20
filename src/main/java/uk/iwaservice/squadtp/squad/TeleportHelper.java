package uk.iwaservice.squadtp.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.squadtp.Config;

import javax.annotation.Nullable;

/**
 * Cooldown, cost and safe-landing logic for squad teleports.
 * All checks run server-side; the client never requests a teleport directly.
 */
public final class TeleportHelper {

    /**
     * Validates cooldown/cost/dimension rules and, if everything passes,
     * charges the cost and teleports the player to a safe spot near the target.
     *
     * @return null on success, otherwise a failure message to show the player.
     */
    @Nullable
    public static Component attempt(SquadManager manager, ServerPlayer player, ServerLevel targetLevel, BlockPos targetPos) {
        if (ReviveSystem.isDowned(player.getUUID())) {
            return Component.translatable("squadtp.msg.you_are_downed");
        }
        if (!Config.ALLOW_CROSS_DIMENSION_TP.get() && player.level().dimension() != targetLevel.dimension()) {
            return Component.translatable("squadtp.msg.tp_cross_dim_disabled");
        }

        long remaining = manager.cooldownRemaining(player.getUUID());
        if (remaining > 0) {
            return Component.translatable("squadtp.msg.tp_cooldown", remaining);
        }

        Component costFailure = chargeCost(player);
        if (costFailure != null) {
            return costFailure;
        }

        BlockPos safe = findSafeSpot(targetLevel, targetPos);
        player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        manager.markTeleported(player.getUUID());
        return null;
    }

    /** Charges the configured teleport cost. Returns null if charged (or free), else a failure message. */
    @Nullable
    private static Component chargeCost(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return null; // creative players teleport for free
        }
        switch (Config.TP_COST_MODE.get()) {
            case NONE -> {
                return null;
            }
            case XP -> {
                int levels = Config.TP_COST_XP_LEVELS.get();
                if (player.experienceLevel < levels) {
                    return Component.translatable("squadtp.msg.tp_cost_xp", levels);
                }
                player.giveExperienceLevels(-levels);
                return null;
            }
            case ITEM -> {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Config.TP_COST_ITEM.get()));
                int count = Config.TP_COST_ITEM_COUNT.get();
                if (item == null) {
                    return null; // misconfigured item id: fail open rather than blocking teleports
                }
                ItemStack probe = new ItemStack(item, count);
                int have = 0;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(item)) {
                        have += stack.getCount();
                    }
                }
                if (have < count) {
                    return Component.translatable("squadtp.msg.tp_cost_item", probe.getHoverName(), count);
                }
                int toRemove = count;
                for (int slot = 0; slot < player.getInventory().getContainerSize() && toRemove > 0; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(item)) {
                        int take = Math.min(toRemove, stack.getCount());
                        stack.shrink(take);
                        toRemove -= take;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * True when a hostile mob or a player from another scoreboard team is
     * within the configured danger radius of the destination. Used to keep
     * respawn-choice spawns out of active combat.
     */
    public static boolean isDestinationDangerous(ServerLevel level, BlockPos pos, ServerPlayer player) {
        int radius = Config.SPAWN_DANGER_RADIUS.get();
        if (radius <= 0) {
            return false;
        }
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center, center).inflate(radius);

        if (!level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                mob -> mob.isAlive() && mob instanceof net.minecraft.world.entity.monster.Enemy).isEmpty()) {
            return true;
        }
        var scoreboard = level.getServer().getScoreboard();
        var ownTeam = scoreboard.getPlayersTeam(player.getGameProfile().getName());
        for (var other : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box)) {
            if (other == player || !other.isAlive()) {
                continue;
            }
            var otherTeam = scoreboard.getPlayersTeam(other.getGameProfile().getName());
            if (!java.util.Objects.equals(ownTeam, otherTeam)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a position at or near {@code target} where a player fits without
     * suffocating or standing in lava. Falls back to the surface if the area
     * around the target is fully obstructed.
     */
    public static BlockPos findSafeSpot(ServerLevel level, BlockPos target) {
        level.getChunk(target); // force-load the target chunk before inspecting blocks

        for (int dy = 0; dy <= 4; dy++) {
            BlockPos candidate = target.above(dy);
            if (isSafe(level, candidate)) {
                return candidate;
            }
        }
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos candidate = target.below(dy);
            if (isSafe(level, candidate)) {
                return candidate;
            }
        }
        // Fully obstructed (e.g. inside solid rock): use the surface above.
        if (level.dimensionType().hasCeiling()) {
            return target; // no meaningful surface in nether-like dimensions
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
    }

    /** Feet and head are passable, feet are not in lava, and there is ground within 3 blocks below. */
    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (!isPassable(level, feet) || !isPassable(level, feet.above())) {
            return false;
        }
        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA)) {
            return false;
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos below = feet.below(dy);
            if (level.getFluidState(below).is(FluidTags.LAVA)) {
                return false;
            }
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    || level.getFluidState(below).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private TeleportHelper() {}
}
