package uk.iwaservice.squadtp.client;

/** Client-side downed/revive HUD state, fed exclusively by S2C packets. */
public final class ClientReviveData {

    /** Remaining downed ticks; -1 when not downed. Counted down locally between packets. */
    private static int downedRemainingTicks = -1;
    /** Revive channel progress; -1 when no channel is active. */
    private static int reviveProgressTicks = -1;
    private static int reviveTotalTicks;
    /** Ticks the give-up key has been held (client-side only). */
    private static int giveUpHoldTicks;

    public static synchronized void setDowned(boolean downed, int remainingTicks) {
        downedRemainingTicks = downed ? remainingTicks : -1;
    }

    public static synchronized void setReviveProgress(int progressTicks, int totalTicks) {
        reviveProgressTicks = progressTicks;
        reviveTotalTicks = totalTicks;
    }

    public static synchronized void tick() {
        if (downedRemainingTicks > 0) {
            downedRemainingTicks--;
        }
    }

    public static synchronized void clear() {
        downedRemainingTicks = -1;
        reviveProgressTicks = -1;
        reviveTotalTicks = 0;
        giveUpHoldTicks = 0;
    }

    /** Returns the new hold duration after incrementing. */
    public static synchronized int incrementGiveUpHold() {
        return ++giveUpHoldTicks;
    }

    public static synchronized void resetGiveUpHold() {
        giveUpHoldTicks = 0;
    }

    public static synchronized int getGiveUpHoldTicks() {
        return giveUpHoldTicks;
    }

    public static synchronized int getDownedRemainingTicks() {
        return downedRemainingTicks;
    }

    public static synchronized int getReviveProgressTicks() {
        return reviveProgressTicks;
    }

    public static synchronized int getReviveTotalTicks() {
        return reviveTotalTicks;
    }

    private ClientReviveData() {}
}
