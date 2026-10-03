package uk.iwaservice.squadtp.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted to the Forge event bus right after a channelled revive completes (the target is back on
 * its feet). Replaces the need for other mods to infer the reviver by watching interactions and
 * downed-state transitions. Not posted for admin/forced revives, which have no reviver.
 */
public final class PlayerRevivedEvent extends Event {

    private final ServerPlayer reviver;
    private final ServerPlayer target;

    public PlayerRevivedEvent(ServerPlayer reviver, ServerPlayer target) {
        this.reviver = reviver;
        this.target = target;
    }

    /** The player who performed the revive. */
    public ServerPlayer getReviver() {
        return reviver;
    }

    /** The player who was revived. */
    public ServerPlayer getTarget() {
        return target;
    }
}
