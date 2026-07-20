package uk.iwaservice.squadtp.squad;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Individually switchable features. Admins toggle these server-wide at
 * runtime via /squad admin; the state persists with the world.
 */
public enum SquadFeature {
    CREATE("create"),
    INVITE("invite"),
    JOIN_REQUEST("join"),
    TELEPORT("tp"),
    RALLY("rally"),
    RESPAWN_CHOICE("respawn"),
    POSITION_SHARING("positions"),
    DUMMY("dummy"),
    REVIVE("revive");

    private final String key;

    SquadFeature(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "squadtp.feature." + key;
    }

    @Nullable
    public static SquadFeature byKey(String key) {
        for (SquadFeature feature : values()) {
            if (feature.key.equalsIgnoreCase(key)) {
                return feature;
            }
        }
        return null;
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (SquadFeature feature : values()) {
            keys.add(feature.key);
        }
        return keys;
    }
}
