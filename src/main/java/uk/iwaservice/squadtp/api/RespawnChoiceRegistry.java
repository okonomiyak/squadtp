package uk.iwaservice.squadtp.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of {@link RespawnChoiceProvider}s. Register during mod construction; providers are
 * only ever read from the server thread while handling a respawn or a /squad respawn command.
 */
public final class RespawnChoiceRegistry {

    private static final List<RespawnChoiceProvider> PROVIDERS = new ArrayList<>();

    public static void register(RespawnChoiceProvider provider) {
        PROVIDERS.add(provider);
    }

    public static List<RespawnChoiceProvider> providers() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    private RespawnChoiceRegistry() {}
}
