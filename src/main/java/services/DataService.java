package services;

import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import constants.Const;
import constants.Required;
import io.mangoo.i18n.Messages;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.DateUtils;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.totp.TotpUtils;
import it.auties.linkpreview.LinkPreview;
import it.auties.linkpreview.LinkPreviewMatch;
import it.auties.linkpreview.LinkPreviewMedia;
import jakarta.inject.Inject;
import models.Action;
import models.Category;
import models.Item;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import utils.Utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static constants.Const.PLACEHOLDER_IMAGE;

public class DataService {
    private final Datastore datastore;
    private final Messages messages;

    @Inject
    public DataService(Datastore datastore, Messages messages) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
        this.messages = Objects.requireNonNull(messages, Required.MESSAGES);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findCategories(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        List<Category> categories = new ArrayList<>();
        datastore
                .query(Category.class)
                .find(eq(Const.USER_UID, userUid)).into(categories);

        List<Map<String, Object>> output = new ArrayList<>();
        if (categories.size() >= 2) {
            for (Category category : categories) {
                output.add(Map.of(
                        "name", category.getName(),
                        "uid", category.getUid(),
                        "count", String.valueOf(category.getCount())
                ));
            }

            return Optional.of(output);
        }

        return Optional.empty();
    }

    public long countItems(String userUid, String categoryUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        if (StringUtils.isBlank(categoryUid) || ("null").equals(categoryUid)) {
            categoryUid = findInbox(userUid).getUid();
        }

        return datastore.countAll(Item.class,
                and(eq(Const.USER_UID, userUid), eq(Const.CATEGORY_UID, categoryUid)));
    }

    public boolean userExists(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        return datastore.find(User.class, eq("uid", userUid)) != null;
    }

    public Optional<String> authenticateUser(String username, String password) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(password, Required.PASSWORD);

        User user = datastore.find(User.class, eq("username", username));
        if (user != null && user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
            return Optional.of(user.getUid());
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findItems(String userUid, String categoryUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(categoryUid, Required.CATEGORY_UID);

        List<Item> items = new ArrayList<>();
        datastore
                .query(Item.class)
                .find(and(
                        eq(Const.USER_UID, userUid),
                        eq("categoryUid", categoryUid))).into(items);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Item item: items) {
            output.add(Map.of(
                    "uid", item.getUid(),
                    "url", item.getUrl(),
                    "image", (item.getImage() == null) ? Strings.EMPTY : item.getImage(),
                    "title", item.getTitle(),
                    "sort", item.getTimestamp().toEpochSecond(ZoneOffset.UTC),
                    "added", DateUtils.getPrettyTime(item.getTimestamp()))); // FIX ME: Remove in later API version
        }

        return Optional.of(output);
    }

    public boolean deleteItem(String uid, String userUid) {
        Objects.requireNonNull(uid, Required.UID);
        Objects.requireNonNull(userUid, Required.USER_UID);

        var item = findItem(uid, userUid);
        var category = findCategory(item.getCategoryUid(), userUid);
        category.setCount(category.getCount() - 1);
        datastore.save(category);

        Category trash = findTrash(userUid);
        trash.setCount(trash.getCount() + 1);
        datastore.save(trash);

        var updateResult = datastore.query(Item.class).updateOne(and(
                eq(Const.USER_UID, userUid),
                eq("uid", uid)),
                    Updates.set("categoryUid", trash.getUid()));

        return updateResult.wasAcknowledged() && updateResult.getModifiedCount() == 1;
    }

    public boolean emptyTrash(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        Category trash = findTrash(userUid);
        trash.setCount(0);
        datastore.save(trash);

        var deleteResult = datastore.query(Item.class).deleteMany(and(
                eq(Const.USER_UID, userUid),
                eq("categoryUid", trash.getUid())));

        return deleteResult.wasAcknowledged();
    }

    private Category findTrash(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.USER_UID, userUid),
                    eq("name", Const.TRASH)));
    }

    public Category findInbox(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        return datastore.find(Category.class,
                and(
                        eq(Const.USER_UID, userUid),
                        eq("name", Const.INBOX)));
    }

    public Category findCategory(String uid, String userUid) {
        Objects.requireNonNull(uid, Required.UID);
        Objects.requireNonNull(userUid, Required.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq("uid", uid),
                    eq(Const.USER_UID, userUid)));
    }

    public Item findItem(String uid, String userUid) {
        Objects.requireNonNull(uid, Required.UID);
        Objects.requireNonNull(userUid, Required.USER_UID);

        return datastore.find(Item.class,
                and(
                        eq("uid", uid),
                        eq(Const.USER_UID, userUid)));
    }

    public boolean moveItem(String uid, String userUid, String categoryUid) {
        Objects.requireNonNull(uid, Required.UID);
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(categoryUid, Required.CATEGORY_UID);

        var item = findItem(uid, userUid);
        var sourceCategory = findCategory(item.getCategoryUid(), userUid);
        var targetCategory = findCategory(categoryUid, userUid);

        if (!sourceCategory.getUid().equals(targetCategory.getUid())) {
            sourceCategory.setCount(sourceCategory.getCount() - 1);
            datastore.save(sourceCategory);

            targetCategory.setCount(targetCategory.getCount() + 1);
            datastore.save(targetCategory);

            var updateResult = datastore.query(Item.class).updateOne(
                    and(
                            eq(Const.USER_UID, userUid),
                            eq("uid", uid)),
                    Updates.set("categoryUid", categoryUid));

            return updateResult.wasAcknowledged();
        }

        return false;
    }

    public boolean addItem(String userUid, String url, String categoryUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(url, Required.URL);

        if (Utils.isValidURL(url)) {
            String previewImage = PLACEHOLDER_IMAGE;
            String title = messages.get("item.missing.title");
            List<LinkPreviewMatch> previews = LinkPreview.createPreviews(url);
            for (LinkPreviewMatch linkPreviewMatch : previews) {
                title = linkPreviewMatch.result().title();
                Set<LinkPreviewMedia> images = linkPreviewMatch.result().images();
                for (LinkPreviewMedia image : images) {
                    previewImage = image.uri().toString();
                }
            }

            Category category = null;
            if (StringUtils.isNotBlank(categoryUid)) {
                category = findCategory(categoryUid, userUid);
            }

            if (category == null) {
                category = findInbox(userUid);
            }

            if (category != null) {
                category.setCount(category.getCount() + 1);
                String categoryResult = datastore.save(category);

                var item = new Item(userUid, category.getUid(), url, previewImage, title);
                String itemResult = datastore.save(item);

                return StringUtils.isNoneBlank(categoryResult, itemResult);
            }
        }

        return false;
    }

    public boolean addCategory(String userUid, String name) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(name, Required.CATEGORY_NAME);

        String result = datastore.save(new Category(name, userUid));

        return StringUtils.isNotBlank(result);
    }

    public boolean deleteCategory(String userUid, String uid) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(uid, Required.UID);

        Category inbox = findInbox(userUid);
        Category trash = findTrash(userUid);

        if (!uid.equals(inbox.getUid()) && !uid.equals(trash.getUid())) {
            var updateResult = datastore.query(Item.class)
                    .updateMany(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq("categoryUid", uid)),
                            Updates.set("categoryUid", trash.getUid()));

            long modifiedCount = updateResult.getModifiedCount();
            trash.setCount((int) (trash.getCount() + modifiedCount));
            datastore.save(trash);

            var deleteResult = datastore.query(Category.class)
                    .deleteOne(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq("uid", uid)));

            return deleteResult.getDeletedCount() == 1;
        }

        return false;
    }

    public User findUser(String username) {
        Objects.requireNonNull(username, Required.USERNAME);

        return datastore.find(User.class, eq("username", username));
    }

    public User findUserByUid(String uid) {
        Objects.requireNonNull(uid, Required.UID);

        return datastore.find(User.class, eq("uid", uid));
    }

    public void save(Object object) {
        Objects.requireNonNull(object, Required.OBJECT);

        datastore.save(object);
    }

    public Category findCategoryByName(String name, String userUid) {
        Objects.requireNonNull(name, Required.CATEGORY_NAME);
        Objects.requireNonNull(userUid, Required.USER_UID);

        return datastore.find(Category.class, and(eq("name", name), eq(Const.USER_UID, userUid)));
    }

    public boolean deleteAccount(String password, String userUid) {
        Objects.requireNonNull(password, Required.PASSWORD);
        Objects.requireNonNull(userUid, Required.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null && user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
            DeleteResult deleteCategories = datastore.query(Category.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteItems = datastore.query(Item.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteUser = datastore.query(User.class).deleteOne(eq("uid", userUid));

            return deleteCategories.wasAcknowledged() && deleteItems.wasAcknowledged() && deleteUser.wasAcknowledged();
        }

        return false;
    }

    public boolean userHasMfa(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa();
    }

    public boolean isValidMfa(String userUid, String otp) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(otp, Required.OTP);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa() && ( TotpUtils.verifiedTotp(user.getMfaSecret(), otp) || CodecUtils.matchArgon2(otp, user.getSalt(), user.getMfaFallback()));
    }

    public String changeMfa(String userUid, boolean mfa) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        String fallaback = null;
        var user = findUserByUid(userUid);
        if (mfa) {
            fallaback = MangooUtils.randomString(32);
            user.setMfaSecret(MangooUtils.randomString(64));
            user.setMfaFallback(CodecUtils.hashArgon2(fallaback, user.getSalt()));
        }
        user.setMfa(mfa);

        datastore.save(user);

        return fallaback;
    }

    public Optional<Action> findAction(String token) {
        Objects.requireNonNull(token, Required.TOKEN);

        return Optional.ofNullable(datastore.find(Action.class, eq("token", token)));
    }

    public void setPassword(String userUid, String password) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(password, Required.PASSWORD);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setPassword(CodecUtils.hashArgon2(password, user.getSalt()));
            datastore.save(user);
        }
    }

    public void deleteAction(Action action) {
        Objects.requireNonNull(action, Required.ACTION);

        datastore.delete(action);
    }

    public void confirmEmail(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setConfirmed(true);
            datastore.save(user);
        }
    }

    public void cleanActions() {
        datastore
                .query(Action.class)
                .deleteMany(lt("expires", LocalDateTime.now()));
    }

    public boolean updateLanguage(String userUid, String language) {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(language, Required.LANGUAGE);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setLanguage(language.toLowerCase());
            return datastore.save(user) != null;
        }

        return false;
    }

    public boolean updatePepper(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setPepper(MangooUtils.randomString(64));
            return datastore.save(user) != null;
        }

        return false;
    }
}