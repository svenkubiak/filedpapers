package models;

import constants.Collections;
import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import io.mangoo.utils.Arguments;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Collection(name = Collections.TOKENS)
public class Token extends Entity implements Serializable {
    @Indexed(unique = true)
    private String uid;
    private LocalDateTime timestamp;

    public Token() {}

    public Token(String uid, LocalDateTime timestamp) {
        this.uid = Arguments.requireNonBlank(uid, Required.UID);
        this.timestamp = Objects.requireNonNull(timestamp, Required.CREATED_AT);
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
