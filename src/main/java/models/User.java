package models;

import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.MangooUtils;

import java.io.Serializable;
import java.util.Objects;

@Collection(name = "users")
public class User extends Entity implements Serializable  {
    @Indexed(unique = true)
    private String uid;

    @Indexed(unique = true)
    private String username;

    @Indexed
    private String password;

    private String salt;
    private String mfaSecret;
    private String mfaFallback;
    private boolean mfa;
    private boolean confirmed;

    public User(String username) {
        this.username = Objects.requireNonNull(username, Required.USERNAME);
        this.uid = CodecUtils.uuid();
        this.salt = MangooUtils.randomString(64);
        this.mfaSecret = MangooUtils.randomString(64);
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
}
