package hyphenated.djbot;

import hyphenated.djbot.auth.ConfiguredUserAuthenticator;
import hyphenated.djbot.auth.User;
import hyphenated.djbot.db.DbMetaDAO;
import hyphenated.djbot.db.SongQueueDAO;
import hyphenated.djbot.health.ChannelCheck;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.basic.BasicAuthFactory;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.HttpClient;
import org.jnativehook.GlobalScreen;
import org.skife.jdbi.v2.DBI;

import java.io.File;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DjApplication extends Application<DjConfiguration> {

    public static void main(String[] args) {
        try {
            new DjApplication().run(args);
        } catch (Exception e) {
            try {
                File file = new File("logs/errorFromMain.txt");
                PrintStream ps = new PrintStream(file);
                e.printStackTrace(ps);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
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

        final HttpClient client = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration())
                .build("http-client");

        final DBIFactory factory = new DBIFactory();
        final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "postgresql");
        final DbMetaDAO meta = jdbi.onDemand(DbMetaDAO.class);
        int currentDbVersion = 1; //if this is ever incremented, you need to fix the below code to be a "real" migration system!

        if(!meta.songqueueTableExists()) {
            //we are creating a fresh new db
            meta.createSongqueueTable();
            meta.createMetaTable();
            meta.insertDbVersion(currentDbVersion);
        }
        
        if(!meta.metaTableExists()) {
            //we are updating from version 0 of the db when there was no meta table
            meta.createMetaTable();
            meta.insertDbVersion(currentDbVersion);
            meta.addSiteColumn();
            
            meta.populateLegacySoundcloudIds();
            meta.populateLegacyYoutubeIds();
        }
        
        jdbi.close(meta);

        final SongQueueDAO dao = jdbi.onDemand(SongQueueDAO.class);
        DjResource resource = new DjResource(configuration, client, dao);
        environment.jersey().register(resource);

        final ChannelCheck channelCheck = new ChannelCheck(resource);

        if(configuration.isDjbotPublic()) {
            String adminUsername = configuration.getAdminUsername();
            String adminPassword = configuration.getAdminPassword();
            if(StringUtils.isBlank(adminUsername) || StringUtils.isBlank(adminPassword)) {
                throw new RuntimeException("Djbot is set to public, so it needs an adminUsername and adminPassword");
            }

            environment.jersey().register(AuthFactory.binder(
                    new BasicAuthFactory<User>(
                            new ConfiguredUserAuthenticator(adminUsername, adminPassword),
                            "djBotRealm",
                            User.class )));

        }

        environment.healthChecks().register("currentSong", channelCheck);
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new KeyboardListener(configuration, resource));
            // turn off the logging from jnativehook
            Logger hooklogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            hooklogger.setLevel(Level.OFF);
            hooklogger.setUseParentHandlers(false);
            
        } catch (Exception e) {
            System.out.println("Couldn't register keyboard shortcut hooks. Proceeding anyway, but here's the exception:\n" + ExceptionUtils.getStackTrace(e));
        }
    }
}
