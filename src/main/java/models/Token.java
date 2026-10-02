package models;

import constants.Collections;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;

import java.io.Serializable;
import java.util.Date;

// A revoked token id; removed by a TTL index once the token would have expired anyway.
@Collection(name = Collections.TOKENS)
public class Token extends Entity implements Serializable {
    @Indexed(unique = true)
    private String uid;
    private Date expiresAt;

    public Token() {}

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
