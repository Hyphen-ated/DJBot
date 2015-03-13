package hyphenated.djbot.auth;

import com.google.common.base.Optional;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import javax.validation.constraints.NotNull;

public class ConfiguredUserAuthenticator implements Authenticator<BasicCredentials, User> {

    @NotNull private String adminUsername;
    @NotNull private String adminPassword;

    public ConfiguredUserAuthenticator(@NotNull String adminUsername, @NotNull String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public Optional<User> authenticate(BasicCredentials credentials) throws AuthenticationException {
        if(adminUsername.equals(credentials.getUsername())
        && adminPassword.equals(credentials.getPassword())) {
            return Optional.of(new User(credentials.getUsername(), true));
        }

        return Optional.absent();
    }
}
