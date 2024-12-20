package models;

import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.constants.NotNull;
import io.mangoo.persistence.Entity;
import io.mangoo.utils.CodecUtils;

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
    private int count;

    public Category(String name, String userUid) {
        this.name = Objects.requireNonNull(name, NotNull.NAME);
        this.userUid = Objects.requireNonNull(userUid, "userUid can not be null");
        this.uid = CodecUtils.uuid();
        this.count = 0;
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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
