package models;

import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import models.enums.Type;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Collection(name = "actions")
public class Action extends Entity implements Serializable {

    @Indexed(unique = true)
    private String token;

    private String userUid;
    private Type type;
    private LocalDateTime expires;

    public Action(String userUid, String token, Type type) {
        this.userUid = Objects.requireNonNull(userUid, "userUid cannot be null");
        this.token = Objects.requireNonNull(token, "token cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.expires = LocalDateTime.now().plusMinutes(60);
    }

    public Action() {
    }

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public LocalDateTime getExpires() {
        return expires;
    }

    public void setExpires(LocalDateTime expires) {
        this.expires = expires;
    }
}
