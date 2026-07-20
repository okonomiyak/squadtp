package uk.iwaservice.squadtp;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.squadtp.block.DummyPlayerBlock;
import uk.iwaservice.squadtp.block.DummyPlayerBlockEntity;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SquadTp.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SquadTp.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SquadTp.MODID);

    public static final RegistryObject<Block> DUMMY_PLAYER = BLOCKS.register("dummy_player",
            () -> new DummyPlayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(0.5f)));

    public static final RegistryObject<Item> DUMMY_PLAYER_ITEM = ITEMS.register("dummy_player",
            () -> new BlockItem(DUMMY_PLAYER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<DummyPlayerBlockEntity>> DUMMY_PLAYER_BE =
            BLOCK_ENTITIES.register("dummy_player",
                    () -> BlockEntityType.Builder.of(DummyPlayerBlockEntity::new, DUMMY_PLAYER.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private ModRegistry() {}
}
