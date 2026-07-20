package uk.iwaservice.squadtp;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
import uk.iwaservice.squadtp.entity.RespawnBeaconEntity;
import uk.iwaservice.squadtp.item.RespawnBeaconItem;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SquadTp.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SquadTp.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SquadTp.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SquadTp.MODID);

    public static final RegistryObject<Block> DUMMY_PLAYER = BLOCKS.register("dummy_player",
            () -> new DummyPlayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(0.5f)));

    public static final RegistryObject<Item> DUMMY_PLAYER_ITEM = ITEMS.register("dummy_player",
            () -> new BlockItem(DUMMY_PLAYER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<DummyPlayerBlockEntity>> DUMMY_PLAYER_BE =
            BLOCK_ENTITIES.register("dummy_player",
                    () -> BlockEntityType.Builder.of(DummyPlayerBlockEntity::new, DUMMY_PLAYER.get()).build(null));

    public static final RegistryObject<EntityType<RespawnBeaconEntity>> RESPAWN_BEACON = ENTITY_TYPES.register(
            "respawn_beacon",
            () -> EntityType.Builder.of(RespawnBeaconEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.2f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("respawn_beacon"));

    public static final RegistryObject<Item> RESPAWN_BEACON_ITEM = ITEMS.register("respawn_beacon",
            () -> new RespawnBeaconItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

    private ModRegistry() {}
}
