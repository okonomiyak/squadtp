package uk.iwaservice.squadtp.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Per-client preferences, stored in {@code config/squadtp-client.toml}
 * (independent of any world/server, unlike {@link uk.iwaservice.squadtp.Config}).
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BELL_SOUND_ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BELL_SOUND_ENABLED = b
                .comment("Play a bell sound for the downed-approach alert. The action-bar message always shows regardless.")
                .define("bellSoundEnabled", true);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
