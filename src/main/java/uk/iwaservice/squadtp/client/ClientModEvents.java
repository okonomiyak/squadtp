package uk.iwaservice.squadtp.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.squadtp.SquadTp;

/** Mod-bus client events: keybind registration. */
@Mod.EventBusSubscriber(modid = SquadTp.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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
    public static void onRegisterGuiOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("downed", uk.iwaservice.squadtp.client.gui.DownedHudOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(uk.iwaservice.squadtp.ModRegistry.RESPAWN_BEACON.get(), RespawnBeaconRenderer::new);
    }

    private ClientModEvents() {}
}
