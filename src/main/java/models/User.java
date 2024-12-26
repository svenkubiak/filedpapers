package models;

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

    public User(String username) {
        this.username = Objects.requireNonNull(username, "username can not be null");
        this.uid = CodecUtils.uuid();
        this.salt = MangooUtils.randomString(64);
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
}
