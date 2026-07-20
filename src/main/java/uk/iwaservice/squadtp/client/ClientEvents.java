package uk.iwaservice.squadtp.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.squadtp.SquadTp;
import uk.iwaservice.squadtp.client.gui.SquadScreen;
import uk.iwaservice.squadtp.compat.JourneyMapCompat;

@Mod.EventBusSubscriber(modid = SquadTp.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SquadClientData.clear();
        ClientReviveData.clear();
        ApproachAlertTracker.clear();
        JourneyMapCompat.refresh();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ClientReviveData.tick();
            ApproachAlertTracker.tick();
        }
        while (ClientModEvents.OPEN_SQUAD_SCREEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new SquadScreen());
            }
        }
        // Give-up requires holding the key (accidental-tap protection).
        while (ClientModEvents.GIVE_UP.consumeClick()) {
            // drain click buffer; hold detection below uses isDown()
        }
        if (mc.player != null && ClientReviveData.getDownedRemainingTicks() >= 0
                && ClientModEvents.GIVE_UP.isDown()) {
            if (ClientReviveData.incrementGiveUpHold() == GIVE_UP_HOLD_TICKS) {
                mc.player.connection.sendCommand("squad giveup");
            }
        } else {
            ClientReviveData.resetGiveUpHold();
        }
    }

    /** Ticks the give-up key must be held (1.5s). */
    public static final int GIVE_UP_HOLD_TICKS = 30;

    @SubscribeEvent
    public static void onClientRespawn(ClientPlayerNetworkEvent.Clone event) {
        // Safety net: never leave the DOWNED overlay up across a respawn.
        ClientReviveData.clear();
        ApproachAlertTracker.clear();
    }

    /** While downed: suppress the jump input (movement keys stay usable for crawling). */
    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (ClientReviveData.getDownedRemainingTicks() >= 0) {
            event.getInput().jumping = false;
        }
    }

    /** While downed: block attack, item use and pick-block clicks on the client. */
    @SubscribeEvent
    public static void onClickInput(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientReviveData.getDownedRemainingTicks() >= 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** While downed: no screens except chat, pause menu and the death screen. */
    @SubscribeEvent
    public static void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (ClientReviveData.getDownedRemainingTicks() < 0) {
            return;
        }
        var screen = event.getNewScreen();
        boolean allowed = screen == null
                || screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || screen instanceof net.minecraft.client.gui.screens.PauseScreen
                || screen instanceof net.minecraft.client.gui.screens.DeathScreen;
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    /** Client-side jump suppression while downed (prevents rubber-banding against the server). */
    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && event.getEntity() == mc.player
                && ClientReviveData.getDownedRemainingTicks() >= 0) {
            var motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x, Math.min(0.0, motion.y), motion.z);
        }
    }

    private ClientEvents() {}
}
