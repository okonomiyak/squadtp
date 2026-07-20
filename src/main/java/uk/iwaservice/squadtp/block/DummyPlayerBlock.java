package uk.iwaservice.squadtp.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.iwaservice.squadtp.Config;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Test helper: a block that stands in for a player. The squad leader
 * right-clicks it to toggle its membership in their squad, which exercises
 * position sync, JourneyMap waypoints and /squad tp without a second account.
 */
public class DummyPlayerBlock extends BaseEntityBlock {

    public DummyPlayerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DummyPlayerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof DummyPlayerBlockEntity dummy)) {
            return InteractionResult.PASS;
        }

        MinecraftServer server = serverPlayer.server;
        SquadManager manager = SquadManager.get(server);
        if (!manager.isEnabled(uk.iwaservice.squadtp.squad.SquadFeature.DUMMY)) {
            serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.feature_disabled",
                    Component.translatable(uk.iwaservice.squadtp.squad.SquadFeature.DUMMY.translationKey())));
            return InteractionResult.CONSUME;
        }
        Squad squad = manager.getSquadOf(serverPlayer.getUUID());
        if (squad == null) {
            serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.not_in_squad"));
            return InteractionResult.CONSUME;
        }
        if (!squad.isLeader(serverPlayer.getUUID())) {
            serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.not_leader"));
            return InteractionResult.CONSUME;
        }

        UUID dummyId = dummy.getDummyId();
        String dummyName = dummy.getDummyName();
        if (squad.isMember(dummyId)) {
            manager.removeMember(server, squad, dummyId);
            serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.dummy_left", dummyName));
        } else {
            Squad other = manager.getSquadOf(dummyId);
            if (other != null) {
                manager.removeMember(server, other, dummyId);
            }
            if (squad.size() >= Config.MAX_SQUAD_SIZE.get()) {
                serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.squad_full", Config.MAX_SQUAD_SIZE.get()));
                return InteractionResult.CONSUME;
            }
            manager.joinDummy(server, squad, dummyId, dummyName);
            serverPlayer.sendSystemMessage(Component.translatable("squadtp.msg.dummy_joined", dummyName));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof DummyPlayerBlockEntity dummy
                && level.getServer() != null) {
            SquadManager manager = SquadManager.get(level.getServer());
            UUID dummyId = dummy.getDummyId();
            Squad squad = manager.getSquadOf(dummyId);
            if (squad != null) {
                manager.removeMember(level.getServer(), squad, dummyId);
            }
            DummyRegistry.unregister(dummyId);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
