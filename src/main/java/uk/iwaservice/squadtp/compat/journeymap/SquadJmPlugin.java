package uk.iwaservice.squadtp.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import uk.iwaservice.squadtp.SquadTp;

import javax.annotation.Nullable;

/**
 * Discovered and instantiated by JourneyMap itself (via the {@link JourneyMapPlugin}
 * annotation), so this class never loads when JourneyMap is absent.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class SquadJmPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        SquadTp.LOGGER.info("JourneyMap integration initialized");
        JmWaypointHandler.refresh();
    }

    @Override
    public String getModId() {
        return SquadTp.MODID;
    }

    @Nullable
    static IClientAPI api() {
        return api;
    }
}
