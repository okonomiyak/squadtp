package uk.iwaservice.squadtp.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.squadtp.SquadTp;

/** Mod-bus client events: keybind registration. */
@EventBusSubscriber(modid = SquadTp.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    public static final KeyMapping OPEN_SQUAD_SCREEN = new KeyMapping(
            "key.squadtp.open_squad_screen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.squadtp");

    public static final KeyMapping GIVE_UP = new KeyMapping(
            "key.squadtp.give_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.squadtp");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SQUAD_SCREEN);
        event.register(GIVE_UP);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SquadTp.MODID, "downed"),
                uk.iwaservice.squadtp.client.gui.DownedHudOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(uk.iwaservice.squadtp.ModRegistry.RESPAWN_BEACON.get(), RespawnBeaconRenderer::new);
    }

    private ClientModEvents() {}
}
