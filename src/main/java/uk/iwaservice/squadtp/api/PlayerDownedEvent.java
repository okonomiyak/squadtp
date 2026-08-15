package uk.iwaservice.squadtp.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;

/**
 * Posted to the NeoForge event bus right after a player enters the downed state (a lethal hit that
 * squadtp's revive system intercepted instead of letting the player die). Lets other mods
 * attribute the takedown — e.g. for kill scoring — at the moment it happens, since the player's
 * eventual real death may never occur (revived) or, when it does (bleed-out timeout), uses a
 * sourceless damage source that no longer identifies the original attacker.
 */
public final class PlayerDownedEvent extends Event {

    private final ServerPlayer player;
    private final DamageSource source;

    public PlayerDownedEvent(ServerPlayer player, DamageSource source) {
        this.player = player;
        this.source = source;
    }

    /** The player who was downed. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The damage source that would have killed {@link #getPlayer()}; carries the attacker, if any. */
    public DamageSource getSource() {
        return source;
    }
}
