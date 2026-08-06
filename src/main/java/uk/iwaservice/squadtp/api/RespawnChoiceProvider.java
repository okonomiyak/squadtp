package uk.iwaservice.squadtp.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Lets a third-party mod add its own options to squadtp's post-death respawn
 * chooser ({@code RespawnChoiceScreen}), alongside squad rally/beacon/member
 * spawns. Register an instance via {@link RespawnChoiceRegistry#register}.
 */
public interface RespawnChoiceProvider {

    /** Stable, unique id for this provider (used to route the client's pick back to it). */
    String id();

    /** Options to offer {@code player} right now; called fresh on every respawn. Empty if none apply. */
    List<RespawnChoiceEntry> getChoices(ServerPlayer player);

    /**
     * Called when the player picks {@code choiceId} (one previously returned from
     * {@link #getChoices}), inside the server-tracked respawn window. Perform the teleport (and
     * any of your own player-facing feedback) here.
     *
     * @return false if the choice is no longer valid (e.g. its target vanished since the screen
     *         opened) — the caller reports a generic "expired" failure in that case.
     */
    boolean onChosen(ServerPlayer player, String choiceId);
}
