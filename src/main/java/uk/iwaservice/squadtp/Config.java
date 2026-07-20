package uk.iwaservice.squadtp;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public enum CostMode { NONE, XP, ITEM }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_SQUAD_SIZE;
    public static final ForgeConfigSpec.IntValue TP_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.EnumValue<CostMode> TP_COST_MODE;
    public static final ForgeConfigSpec.IntValue TP_COST_XP_LEVELS;
    public static final ForgeConfigSpec.ConfigValue<String> TP_COST_ITEM;
    public static final ForgeConfigSpec.IntValue TP_COST_ITEM_COUNT;
    public static final ForgeConfigSpec.IntValue DOWNED_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue REVIVE_CAST_SECONDS;
    public static final ForgeConfigSpec.IntValue REVIVE_HEAL_PERCENT;
    public static final ForgeConfigSpec.IntValue REVIVE_INVULN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NON_SQUAD_REVIVE;
    public static final ForgeConfigSpec.BooleanValue APPROACH_ALERT_ENABLED;
    public static final ForgeConfigSpec.IntValue APPROACH_ALERT_RADIUS;
    public static final ForgeConfigSpec.IntValue GIVE_UP_HOLD_TICKS;
    public static final ForgeConfigSpec.IntValue BEACON_USES;
    public static final ForgeConfigSpec.IntValue POS_UPDATE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue INVITE_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SAME_TEAM;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION_TP;
    public static final ForgeConfigSpec.IntValue COMBAT_BLOCK_SECONDS;
    public static final ForgeConfigSpec.BooleanValue RALLY_RESPAWN_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RESPAWN_CHOICE_ENABLED;
    public static final ForgeConfigSpec.IntValue RESPAWN_CHOICE_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue SPAWN_DANGER_RADIUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("squad");
        MAX_SQUAD_SIZE = b
                .comment("Maximum number of members in a squad.")
                .defineInRange("maxSquadSize", 4, 2, 8);
        INVITE_EXPIRY_SECONDS = b
                .comment("Seconds before a squad invite expires.")
                .defineInRange("inviteExpirySeconds", 120, 10, 3600);
        REQUIRE_SAME_TEAM = b
                .comment("If vanilla scoreboard teams are in use, only players on the same team",
                        "as the squad leader can join the squad (no-team players only with no-team leaders).")
                .define("requireSameTeam", true);
        b.pop();

        b.push("teleport");
        TP_COOLDOWN_SECONDS = b
                .comment("Cooldown in seconds between squad teleports per player. 0 disables the cooldown.")
                .defineInRange("tpCooldownSeconds", 60, 0, 86400);
        TP_COST_MODE = b
                .comment("Cost charged per squad teleport: NONE, XP (experience levels) or ITEM.")
                .defineEnum("tpCostMode", CostMode.NONE);
        TP_COST_XP_LEVELS = b
                .comment("Experience levels charged when tpCostMode = XP.")
                .defineInRange("tpCostXpLevels", 3, 1, 100);
        TP_COST_ITEM = b
                .comment("Item id charged when tpCostMode = ITEM.")
                .define("tpCostItem", "minecraft:ender_pearl");
        TP_COST_ITEM_COUNT = b
                .comment("Item count charged when tpCostMode = ITEM.")
                .defineInRange("tpCostItemCount", 1, 1, 64);
        ALLOW_CROSS_DIMENSION_TP = b
                .comment("Allow teleporting to members or rally points in another dimension.")
                .define("allowCrossDimensionTp", true);
        COMBAT_BLOCK_SECONDS = b
                .comment("Teleporting/respawning to a member is blocked for this many seconds after they take damage. 0 disables.")
                .defineInRange("combatBlockSeconds", 15, 0, 300);
        RALLY_RESPAWN_ENABLED = b
                .comment("If true, squad members respawn at the squad rally point after death (when one is set).",
                        "Takes precedence over the respawn chooser below.")
                .define("rallyRespawnEnabled", false);
        RESPAWN_CHOICE_ENABLED = b
                .comment("Show a respawn location chooser (rally point / squad members) after death.")
                .define("respawnChoiceEnabled", true);
        RESPAWN_CHOICE_WINDOW_SECONDS = b
                .comment("Seconds after respawn during which the respawn choice remains valid.")
                .defineInRange("respawnChoiceWindowSeconds", 60, 10, 600);
        SPAWN_DANGER_RADIUS = b
                .comment("Respawn-choice spawning is blocked when a hostile mob or a player from",
                        "another scoreboard team is within this many blocks of the destination. 0 disables.")
                .defineInRange("spawnDangerRadius", 4, 0, 32);
        b.pop();

        b.push("revive");
        DOWNED_TIMEOUT_SECONDS = b
                .comment("Seconds a downed player survives before dying for real.")
                .defineInRange("downedTimeoutSeconds", 30, 5, 600);
        REVIVE_CAST_SECONDS = b
                .comment("Seconds a rescuer must hold right-click on a downed player to revive them.")
                .defineInRange("reviveCastSeconds", 2.5, 0.5, 60.0);
        REVIVE_HEAL_PERCENT = b
                .comment("Percent of max health restored on revive.")
                .defineInRange("reviveHealPercent", 30, 1, 100);
        REVIVE_INVULN_SECONDS = b
                .comment("Seconds of damage immunity after being revived. 0 disables.")
                .defineInRange("reviveInvulnSeconds", 3, 0, 60);
        ALLOW_NON_SQUAD_REVIVE = b
                .comment("If true, players outside the squad may also revive downed players.")
                .define("allowNonSquadRevive", false);
        APPROACH_ALERT_ENABLED = b
                .comment("While downed, show an action-bar + sound alert when a squad member's synced",
                        "position first comes within approachAlertRadius of you.")
                .define("approachAlertEnabled", true);
        APPROACH_ALERT_RADIUS = b
                .comment("Radius in blocks for the downed approach alert.")
                .defineInRange("approachAlertRadius", 24, 4, 128);
        GIVE_UP_HOLD_TICKS = b
                .comment("Ticks the give-up key must be held while downed before it triggers (20 ticks = 1 second).",
                        "Kept meaningfully longer than the revive cast time so giving up isn't strictly faster than being revived.")
                .defineInRange("giveUpHoldTicks", 60, 1, 200);
        b.pop();

        b.push("beacon");
        BEACON_USES = b
                .comment("Number of times a placed respawn beacon can be used before it's consumed.")
                .defineInRange("beaconUses", 4, 1, 20);
        b.pop();

        b.push("sync");
        POS_UPDATE_INTERVAL_TICKS = b
                .comment("Interval in ticks between member position broadcasts to squad members.")
                .defineInRange("posUpdateIntervalTicks", 20, 5, 200);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
