package models;

import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import utils.Utils;

import java.io.Serializable;
import java.util.Objects;

@Collection(name = "categories")
public class Category extends Entity implements Serializable {
    @Indexed(unique = true)
    private String uid;

    @Indexed
    private String userUid;

    @Indexed
    private String name;

    public Category(String name, String userUid) {
        this.name = Objects.requireNonNull(name, Required.NAME);
        this.userUid = Objects.requireNonNull(userUid, Required.USER_UID);
        this.uid = Utils.randomString();
    }

    public Category() {
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
