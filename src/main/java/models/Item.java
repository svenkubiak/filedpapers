package models;

import constants.Required;
import io.mangoo.annotations.Collection;
import io.mangoo.annotations.Indexed;
import io.mangoo.persistence.Entity;
import utils.Utils;

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
    private String description;
    private String domain;
    private String mediaUid;

    public Item() {
        this.uid = Utils.randomString();
        this.timestamp = LocalDateTime.now();
    }

    public static Item create() {
        return new Item();
    }

    public Item withUserUid(String userUid) {
        this.userUid = Objects.requireNonNull(userUid, Required.USER_UID);
        return this;
    }

    public Item withCategoryUid(String categoryUid) {
        this.categoryUid = Objects.requireNonNull(categoryUid, Required.CATEGORY_UID);
        return this;
    }

    public Item withUrl(String url) {
        this.url = Objects.requireNonNull(url, Required.URL);
        return this;
    }

    public Item withImage(String image) {
        this.image = Objects.requireNonNull(image, Required.IMAGE);
        return this;
    }

    public Item withTitle(String title) {
        this.title = Objects.requireNonNull(title, Required.TITLE);
        return this;
    }

    public Item withDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public Item withDescription(String description) {
        this.description = description;
        return this;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getMediaUid() {
        return mediaUid;
    }

    public void setMediaUid(String mediaUid) {
        this.mediaUid = mediaUid;
    }
}
