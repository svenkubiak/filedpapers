package models;

import constants.Collections;
import constants.Const;
import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import utils.Utils;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Collection(name = Collections.USERS)
public class User extends Entity implements Serializable  {
    @Indexed(unique = true)
    private String uid;

    @Indexed(unique = true)
    private String username;

    @Indexed
    private String password;

    private String salt;

    /**
     * Epoch seconds. Every access token, refresh token and authentication cookie
     * issued before this point is rejected, which is what makes a revocation
     * effective across all devices. Stored as epoch seconds to keep the
     * comparison against the iat claim free of time zone ambiguity.
     */
    private long sessionsValidFrom;

    private String mfaSecret;
    private String mfaFallback;
    private String language;
    private boolean mfa;
    private boolean confirmed;

    public User(String username) {
        this.username = Objects.requireNonNull(username, Required.USERNAME);
        this.uid = Utils.randomString();
        this.salt = Utils.randomString();
        this.sessionsValidFrom = Instant.now().getEpochSecond();
        this.mfaSecret = Utils.randomString();
        this.language = Const.DEFAULT_LANGUAGE;
    }

    public User() {
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }

    public boolean isMfa() {
        return mfa;
    }

    public void setMfa(boolean mfa) {
        this.mfa = mfa;
    }

    public String getMfaFallback() {
        return mfaFallback;
    }

    public void setMfaFallback(String mfaFallback) {
        this.mfaFallback = mfaFallback;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public long getSessionsValidFrom() {
        return sessionsValidFrom;
    }

    public void setSessionsValidFrom(long sessionsValidFrom) {
        this.sessionsValidFrom = sessionsValidFrom;
    }
}
