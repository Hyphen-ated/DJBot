package hyphenated.djbot.json;

public class LoginInfo {
    String username;
    String userToken;

    public LoginInfo(String username, String userToken) {
        this.username = username;
        this.userToken = userToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }
}
