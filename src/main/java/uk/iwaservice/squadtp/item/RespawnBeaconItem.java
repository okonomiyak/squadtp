package uk.iwaservice.squadtp.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import uk.iwaservice.squadtp.Config;
import uk.iwaservice.squadtp.ModRegistry;
import uk.iwaservice.squadtp.entity.RespawnBeaconEntity;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadFeature;
import uk.iwaservice.squadtp.squad.SquadManager;

/**
 * Places a {@link RespawnBeaconEntity} for the user's squad. Any squad
 * member may place one; doing so replaces the squad's previous beacon, if
 * any (only one active beacon per squad).
 */
public class RespawnBeaconItem extends Item {

    public RespawnBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        MinecraftServer server = player.server;
        SquadManager manager = SquadManager.get(server);

        if (!manager.isEnabled(SquadFeature.BEACON)) {
            player.sendSystemMessage(Component.translatable("squadtp.msg.feature_disabled",
                    Component.translatable(SquadFeature.BEACON.translationKey())));
            return InteractionResult.FAIL;
        }
        Squad squad = manager.getSquadOf(player.getUUID());
        if (squad == null) {
            player.sendSystemMessage(Component.translatable("squadtp.msg.not_in_squad"));
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        EntityType<RespawnBeaconEntity> type = ModRegistry.RESPAWN_BEACON.get();
        RespawnBeaconEntity beacon = type.create(level);
        if (beacon == null) {
            return InteractionResult.FAIL;
        }
        beacon.setSquadId(squad.getId());
        beacon.setCustomName(Component.translatable("entity.squadtp.respawn_beacon"));
        beacon.setCustomNameVisible(true);
        beacon.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        if (!level.addFreshEntity(beacon)) {
            return InteractionResult.FAIL;
        }

        manager.placeBeacon(server, squad, beacon, Config.BEACON_USES.get());
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
