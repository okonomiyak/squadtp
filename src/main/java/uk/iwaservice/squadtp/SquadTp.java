package uk.iwaservice.squadtp;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import uk.iwaservice.squadtp.network.NetworkHandler;

@Mod(SquadTp.MODID)
public class SquadTp {
    public static final String MODID = "squadtp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SquadTp(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::buildCreativeTabs);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(NetworkHandler::register);
        ModRegistry.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, uk.iwaservice.squadtp.client.ClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModRegistry.RESPAWN_BEACON.get(), uk.iwaservice.squadtp.entity.RespawnBeaconEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(uk.iwaservice.squadtp.compat.TaczCompat::init);
        event.enqueueWork(uk.iwaservice.squadtp.compat.SuperbWarfareCompat::init);
    }

    private void buildCreativeTabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.DUMMY_PLAYER_ITEM.get());
            event.accept(ModRegistry.RESPAWN_BEACON_ITEM.get());
            event.accept(ModRegistry.AED_ITEM.get());
        }
    }
}
