package uk.iwaservice.squadtp;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import uk.iwaservice.squadtp.network.NetworkHandler;

@Mod(SquadTp.MODID)
public class SquadTp {
    public static final String MODID = "squadtp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SquadTp() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::buildCreativeTabs);
        ModRegistry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, uk.iwaservice.squadtp.client.ClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
        event.enqueueWork(uk.iwaservice.squadtp.compat.TaczCompat::init);
        event.enqueueWork(uk.iwaservice.squadtp.compat.SuperbWarfareCompat::init);
    }

    private void buildCreativeTabs(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.DUMMY_PLAYER_ITEM.get());
        }
    }
}
