package models;

import constants.Collections;
import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;

import java.io.Serializable;
import java.util.Objects;

// Own collection because the user document is loaded on every request.
@Collection(name = Collections.AVATARS)
public class Avatar extends Entity implements Serializable {
    @Indexed(unique = true)
    private String userUid;
    private byte[] data;

    public Avatar() {
    }

    public Avatar(String userUid) {
        this.userUid = Objects.requireNonNull(userUid, Required.USER_UID);
    }

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
