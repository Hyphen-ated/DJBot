package hyphenated.djbot;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * Created with IntelliJ IDEA.
 * User: sean
 * Date: 5/13/14
 * Time: 4:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class DjBotApplication extends Application<DjConfiguration> {

    public static void main(String[] args) throws Exception {
        new DjBotApplication().run(args);
    }

    @Override
    public String getName() {
        return "DjBot";
    }

    @Override
    public void initialize(Bootstrap<DjConfiguration> bootstrap) {
        // nothing to do yet
    }

    @Override
    public void run(DjConfiguration configuration,
                    Environment environment) {
        DjResource resource = new DjResource(configuration);
        environment.jersey().register(resource);
    }
}
