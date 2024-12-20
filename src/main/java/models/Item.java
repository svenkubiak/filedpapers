package models;

import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import io.mangoo.utils.CodecUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Collection(name = "items")
public class Item extends Entity implements Serializable {
    @Indexed
    private String uid;
    @Indexed
    private String userUid;
    @Indexed
    private String categoryUid;
    @Indexed
    private LocalDateTime timestamp;
    private String url;
    private String image;
    private String title;

    public Item(String userUid, String categoryUid, String url, String image, String title) {
        this.userUid = Objects.requireNonNull(userUid, "userUid can not be null");
        this.categoryUid = Objects.requireNonNull(categoryUid, "categoryUid can not be null");
        this.url = Objects.requireNonNull(url, "url can not be null");
        this.image = Objects.requireNonNull(image, "image can not be null");
        this.title = Objects.requireNonNull(title, "title can not be null");
        this.uid = CodecUtils.uuid();
        this.timestamp = LocalDateTime.now();
    }

    public Item() {
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getCategoryUid() {
        return categoryUid;
    }

    public void setCategoryUid(String categoryUid) {
        this.categoryUid = categoryUid;
    }
}
