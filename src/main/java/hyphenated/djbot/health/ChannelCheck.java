package hyphenated.djbot.health;

import com.codahale.metrics.health.HealthCheck;
import hyphenated.djbot.DjService;
import hyphenated.djbot.DjResource;

public class ChannelCheck extends HealthCheck {
    private final DjService bot;
    public ChannelCheck(DjResource resource) {
        this.bot = resource.getDj();
    }

    @Override
    protected Result check() throws Exception {
        String channel = bot.getStreamer();
        if (!channel.startsWith("#")) {
            return HealthCheck.Result.healthy();
        } else {
            return Result.unhealthy("Channel name \"" + bot.getStreamer() + " shouldn't start with #");
        }
    }
}
