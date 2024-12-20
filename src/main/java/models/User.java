package models;

import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import io.mangoo.utils.CodecUtils;

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
    @Indexed(unique = true)
    private String email;

    public User(String username, String password, String email) {
        this.username = Objects.requireNonNull(username, "username can not be null");
        this.password = Objects.requireNonNull(password, "password can not be null");
        this.email = Objects.requireNonNull(email, "email can not be null");
        this.uid = CodecUtils.uuid();
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
