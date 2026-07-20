package uk.iwaservice.squadtp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import uk.iwaservice.squadtp.Config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * While the local player is downed, plays an action-bar + sound alert the
 * first time a squad member's synced position comes within range - a
 * "help is coming" cue. Purely client-side and cosmetic; it rides on the
 * position data the server already broadcasts every second, so it needs no
 * new packets.
 */
public final class ApproachAlertTracker {

    /** Members currently inside the alert radius, so re-entry after leaving can re-trigger. */
    private static final Set<UUID> inRange = new HashSet<>();

    public static void tick() {
        if (ClientReviveData.getDownedRemainingTicks() < 0 || !Config.APPROACH_ALERT_ENABLED.get()) {
            inRange.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        UUID self = mc.player.getUUID();
        ResourceLocation selfDim = mc.player.level().dimension().location();
        double radius = Config.APPROACH_ALERT_RADIUS.get();
        double radiusSqr = radius * radius;

        Set<UUID> stillInRange = new HashSet<>();
        for (Map.Entry<UUID, SquadClientData.MemberPos> entry : SquadClientData.getPositions().entrySet()) {
            UUID uuid = entry.getKey();
            if (uuid.equals(self)) {
                continue;
            }
            SquadClientData.MemberPos pos = entry.getValue();
            if (!pos.dimension().equals(selfDim)) {
                continue;
            }
            double distSqr = pos.pos().distToCenterSqr(mc.player.position());
            if (distSqr <= radiusSqr) {
                stillInRange.add(uuid);
                if (!inRange.contains(uuid)) {
                    alert(mc, pos.name(), Math.sqrt(distSqr));
                }
            }
        }
        inRange.clear();
        inRange.addAll(stillInRange);
    }

    private static void alert(Minecraft mc, String name, double distance) {
        mc.player.displayClientMessage(
                Component.translatable("squadtp.hud.approaching", name, (int) distance), true);
        if (ClientConfig.BELL_SOUND_ENABLED.get()) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.get(), 1.0f, 1.2f);
        }
    }

    public static void clear() {
        inRange.clear();
    }

    private ApproachAlertTracker() {}
}
