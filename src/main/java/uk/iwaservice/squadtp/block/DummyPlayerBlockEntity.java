package uk.iwaservice.squadtp.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.squadtp.ModRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/** Holds the persistent identity (UUID + name) of a placed test dummy. */
public class DummyPlayerBlockEntity extends BlockEntity {

    @Nullable
    private UUID dummyId;
    private String dummyName = "";

    public DummyPlayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DUMMY_PLAYER_BE.get(), pos, state);
    }

    public UUID getDummyId() {
        ensureIdentity();
        return dummyId;
    }

    public String getDummyName() {
        ensureIdentity();
        return dummyName;
    }

    private void ensureIdentity() {
        if (dummyId == null) {
            dummyId = UUID.randomUUID();
            dummyName = "Dummy_" + dummyId.toString().substring(0, 4);
            setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ensureIdentity();
            DummyRegistry.register(dummyId, new DummyRegistry.Entry(dummyName, serverLevel.dimension(), worldPosition));
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && dummyId != null) {
            DummyRegistry.unregister(dummyId);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide && dummyId != null) {
            DummyRegistry.unregister(dummyId);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("DummyId")) {
            dummyId = tag.getUUID("DummyId");
            dummyName = tag.getString("DummyName");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ensureIdentity();
        tag.putUUID("DummyId", dummyId);
        tag.putString("DummyName", dummyName);
    }
}
