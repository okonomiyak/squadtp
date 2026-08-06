package uk.iwaservice.squadtp;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.iwaservice.squadtp.block.DummyPlayerBlock;
import uk.iwaservice.squadtp.block.DummyPlayerBlockEntity;
import uk.iwaservice.squadtp.entity.RespawnBeaconEntity;
import uk.iwaservice.squadtp.item.RespawnBeaconItem;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, SquadTp.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, SquadTp.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SquadTp.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, SquadTp.MODID);

    public static final DeferredHolder<Block, DummyPlayerBlock> DUMMY_PLAYER = BLOCKS.register("dummy_player",
            () -> new DummyPlayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(0.5f)));

    public static final DeferredHolder<Item, BlockItem> DUMMY_PLAYER_ITEM = ITEMS.register("dummy_player",
            () -> new BlockItem(DUMMY_PLAYER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DummyPlayerBlockEntity>> DUMMY_PLAYER_BE =
            BLOCK_ENTITIES.register("dummy_player",
                    () -> BlockEntityType.Builder.of(DummyPlayerBlockEntity::new, DUMMY_PLAYER.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<RespawnBeaconEntity>> RESPAWN_BEACON = ENTITY_TYPES.register(
            "respawn_beacon",
            () -> EntityType.Builder.of(RespawnBeaconEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.2f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("respawn_beacon"));

    public static final DeferredHolder<Item, RespawnBeaconItem> RESPAWN_BEACON_ITEM = ITEMS.register("respawn_beacon",
            () -> new RespawnBeaconItem(new Item.Properties().stacksTo(16)));

    /** Reusable revive tool; see {@link uk.iwaservice.squadtp.squad.ReviveSystem}. */
    public static final RegistryObject<Item> AED_ITEM = ITEMS.register("aed",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

    private ModRegistry() {}
}
