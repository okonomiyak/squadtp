package uk.iwaservice.squadtp.client;

/**
 * Shared member color palette (join order) so the GUI and the JourneyMap
 * waypoints stay visually consistent. Kept free of any JourneyMap imports.
 */
public final class SquadColors {

    private static final int[] MEMBER_COLORS = {
            0xFF5555, 0x55FF55, 0x5599FF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFAA55, 0xAAAAFF};

    public static final int RALLY_COLOR = 0xFFAA00;

    /** RGB color (no alpha) for the member at the given join-order slot. */
    public static int memberColor(int slot) {
        return MEMBER_COLORS[Math.floorMod(slot, MEMBER_COLORS.length)];
    }

    private SquadColors() {}
}
