package hyphenated.djbot;

import hyphenated.djbot.health.ChannelCheck;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class DjApplication extends Application<DjConfiguration> {

    public static void main(String[] args) throws Exception {
        new DjApplication().run(args);
    }

    @Override
    public String getName() {
        return "DjBot";
    }

    @Override
    public void initialize(Bootstrap<DjConfiguration> bootstrap) {

        bootstrap.addBundle(new AssetsBundle("/assets", "/djbot/ui", "index.html"));
    }

    @Override
    public void run(DjConfiguration configuration,
                    Environment environment) {
        DjResource resource = new DjResource(configuration);
        environment.jersey().register(resource);

        final ChannelCheck channelCheck = new ChannelCheck(resource);

        environment.healthChecks().register("currentSong", channelCheck);
    }
}
