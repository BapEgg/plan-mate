package com.planmate.auth.local;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.local-test-users")
public class LocalTestUserProperties {

    private boolean enabled;
    private List<Account> accounts = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Account> getAccounts() {
        return List.copyOf(accounts);
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts == null ? new ArrayList<>() : new ArrayList<>(accounts);
    }

    public static class Account {

        private String loginId;
        private String password;
        private String email;
        private String nickname;

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

    }

}
