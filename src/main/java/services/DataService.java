package services;

import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import constants.Const;
import io.mangoo.constants.NotNull;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.DateUtils;
import it.auties.linkpreview.LinkPreview;
import it.auties.linkpreview.LinkPreviewMatch;
import it.auties.linkpreview.LinkPreviewMedia;
import jakarta.inject.Inject;
import models.Category;
import models.Item;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.time.ZoneOffset;
import java.util.*;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class DataService {
    public static final String USER_UID_CAN_NOT_BE_NULL = "userUid can not be null";
    public static final String CATEGORY_UID_CAN_NOT_BE_NULL = "categoryUid can not be null";
    public static final String NAME_CAN_NOT_BE_NULL = "name can not be null";
    public static final String URL_CAN_NOT_BE_NULL = "url can not be null";
    public static final String UID_CAN_NOT_BE_NULL = "uid can not be null";
    public static final String USERNAME_CAN_NOT_BE_NULL = "username can not be null";
    public static final String PASSWORD_CAN_NOT_BE_NULL = "password can not be null";
    private final Datastore datastore;

    @Inject
    public DataService(Datastore datastore) {
        this.datastore = Objects.requireNonNull(datastore, "datastore can not be null");
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findCategories(String userUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        List<Category> categories = new ArrayList<>();
        datastore
                .query(Category.class)
                .find(eq("userUid", userUid)).into(categories);

        List<Map<String, Object>> output = new ArrayList<>();
        if (categories.size() >= 2) {
            for (Category category : categories) {
                output.add(Map.of(
                        "name", category.getName(),
                        "uid", category.getUid(),
                        "count", String.valueOf(category.getCount())
                ));
            };
            return Optional.of(output);
        }

        return Optional.empty();
    }

    public boolean userExists(String userUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        return datastore.find(User.class, eq("uid", userUid)) != null;
    }

    public Optional<String> authenticateUser(String username, String password) {
        Objects.requireNonNull(username, USERNAME_CAN_NOT_BE_NULL);
        Objects.requireNonNull(password, PASSWORD_CAN_NOT_BE_NULL);

        User user = datastore
                .find(User.class, and(
                        eq("username", username),
                        eq("password", CodecUtils.hashArgon2(password))));

        return (user != null) ? Optional.of(user.getUid()) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findItems(String userUid, String categoryUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(categoryUid, CATEGORY_UID_CAN_NOT_BE_NULL);

        List<Item> items = new ArrayList<>();
        datastore
                .query(Item.class)
                .find(and(
                        eq("userUid", userUid),
                        eq("categoryUid", categoryUid))).into(items);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Item item: items) {
            output.add(Map.of(
                    "uid", item.getUid(),
                    "url", item.getUrl(),
                    "image", (item.getImage() == null) ? Strings.EMPTY : item.getImage(),
                    "title", item.getTitle(),
                    "sort", item.getTimestamp().toEpochSecond(ZoneOffset.UTC),
                    "added", DateUtils.getPrettyTime(item.getTimestamp())));
        };

        return Optional.of(output);
    }

    public boolean deleteItem(String uid, String userUid) {
        Objects.requireNonNull(uid, UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        Item item = findItem(uid, userUid);
        Category category = findCategory(item.getCategoryUid(), userUid);
        category.setCount(category.getCount() - 1);
        datastore.save(category);

        Category trash = findTrash(userUid);
        trash.setCount(trash.getCount() + 1);
        datastore.save(trash);

        UpdateResult updateResult = datastore.query(Item.class).updateOne(and(
                eq("userUid", userUid),
                eq("uid", uid)),
                    Updates.set("categoryUid", trash.getUid()));

        return updateResult.wasAcknowledged();
    }

    public boolean emptyTrash(String userUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        Category trash = findTrash(userUid);
        trash.setCount(0);
        datastore.save(trash);

        DeleteResult deleteResult = datastore.query(Item.class).deleteMany(and(
                eq("userUid", userUid),
                eq("categoryUid", trash.getUid())));

        return deleteResult.wasAcknowledged();
    }

    private Category findTrash(String userUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        return datastore.find(Category.class,
                and(
                    eq("userUid", userUid),
                    eq("name", Const.TRASH)));
    }

    public Category findInbox(String userUid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        return datastore.find(Category.class,
                and(
                        eq("userUid", userUid),
                        eq("name", Const.INBOX)));
    }

    public Category findCategory(String uid, String userUid) {
        Objects.requireNonNull(uid, UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        return datastore.find(Category.class,
                and(
                    eq("uid", uid),
                    eq("userUid", userUid)));
    }

    public Item findItem(String uid, String userUid) {
        Objects.requireNonNull(uid, UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        return datastore.find(Item.class,
                and(
                        eq("uid", uid),
                        eq("userUid", userUid)));
    }

    public boolean moveItem(String uid, String userUid, String categoryUid) {
        Objects.requireNonNull(uid, UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(categoryUid, CATEGORY_UID_CAN_NOT_BE_NULL);

        Item item = findItem(uid, userUid);
        Category sourceCategory = findCategory(item.getCategoryUid(), userUid);
        Category targetCategory = findCategory(categoryUid, userUid);

        if (!sourceCategory.getUid().equals(targetCategory.getUid())) {
            sourceCategory.setCount(sourceCategory.getCount() - 1);
            datastore.save(sourceCategory);

            targetCategory.setCount(targetCategory.getCount() + 1);
            datastore.save(targetCategory);

            UpdateResult updateResult = datastore.query(Item.class).updateOne(
                    and(
                            eq("userUid", userUid),
                            eq("uid", uid)),
                    Updates.set("categoryUid", categoryUid));

            return updateResult.wasAcknowledged();
        }

        return false;
    }

    public void addItem(String userUid, String url) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(url, URL_CAN_NOT_BE_NULL);

        String previewImage = Strings.EMPTY;
        String title = Strings.EMPTY;
        List<LinkPreviewMatch> previews = LinkPreview.createPreviews(url);
        for (LinkPreviewMatch o : previews) {
            title = o.result().title();
            Set<LinkPreviewMedia> images = o.result().images();
            for (LinkPreviewMedia image : images) {
                previewImage = image.uri().toString();
            }
        }

        Category inbox = findInbox(userUid);
        inbox.setCount(inbox.getCount() + 1);
        datastore.save(inbox);

        Item item = new Item(userUid, inbox.getUid(), url, previewImage, title);
        datastore.save(item);
    }

    public void addCategory(String userUid, String name) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(name, NAME_CAN_NOT_BE_NULL);

        datastore.save(new Category(name, userUid));
    }

    public void deleteCategory(String userUid, String uid) {
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);
        Objects.requireNonNull(uid, UID_CAN_NOT_BE_NULL);

        Category inbox = findInbox(userUid);
        Category trash = findTrash(userUid);

        if (!uid.equals(inbox.getUid()) && !uid.equals(trash.getUid())) {
            UpdateResult updateResult = datastore.query(Item.class)
                    .updateMany(
                            and(
                                    eq("userUid", userUid),
                                    eq("categoryUid", uid)),
                            Updates.set("categoryUid", trash.getUid()));

            long modifiedCount = updateResult.getModifiedCount();
            trash.setCount((int) (trash.getCount() + modifiedCount));
            datastore.save(trash);

            datastore.query(Category.class)
                    .deleteOne(
                            and(
                                    eq("userUid", userUid),
                                    eq("uid", uid)));
        }
    }

    public User findUser(String username) {
        Objects.requireNonNull(username, NotNull.USERNAME);

        return datastore.find(User.class, eq("username", username));
    }

    public User findUserByUid(String uid) {
        Objects.requireNonNull(uid, "uid can not be null");

        return datastore.find(User.class, eq("uid", uid));
    }

    public void save(Object object) {
        Objects.requireNonNull(object, "object can not be null");

        datastore.save(object);
    }

    public Category findCategoryByName(String name, String userUid) {
        Objects.requireNonNull(name, NotNull.NAME);
        Objects.requireNonNull(userUid, USER_UID_CAN_NOT_BE_NULL);

        return datastore.find(Category.class, and(eq("name", name), eq("userUid", userUid)));
    }
}