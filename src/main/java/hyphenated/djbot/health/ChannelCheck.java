package hyphenated.djbot.health;

import com.codahale.metrics.health.HealthCheck;
import hyphenated.djbot.DjBot;
import hyphenated.djbot.DjResource;

public class ChannelCheck extends HealthCheck {
    private final DjBot bot;
    public ChannelCheck(DjResource resource) {
        this.bot = resource.getBot();
    }

    @Override
    protected Result check() throws Exception {
        String channel = bot.getChannel();
        if (!channel.startsWith("#")) {
            return HealthCheck.Result.healthy();
        } else {
            return Result.unhealthy("Channel name \"" + bot.getChannel() + " shouldn't start with #");
        }
    }
}
