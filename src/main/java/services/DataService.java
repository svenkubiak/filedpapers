package services;

import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import constants.Const;
import constants.Invalid;
import constants.Required;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.DateUtils;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.totp.TotpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Action;
import models.Category;
import models.Item;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import utils.Utils;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static constants.Const.PLACEHOLDER_IMAGE;

public class DataService {
    private static final Logger LOG = LogManager.getLogger(DataService.class);
    private final Datastore datastore;
    private final boolean storeImages;

    @Inject
    public DataService(Datastore datastore, @Named("application.images.store") boolean storeImages) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
        this.storeImages = storeImages;
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findCategories(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        List<Category> categories = new ArrayList<>();
        datastore
                .query(Category.class)
                .find(eq(Const.USER_UID, userUid)).into(categories);

        List<Map<String, Object>> output = new ArrayList<>();
        if (categories.size() >= 2) {
            for (Category category : categories) {
                output.add(Map.of(
                        Const.NAME, category.getName(),
                        Const.UID, category.getUid(),
                        Const.COUNT, String.valueOf(category.getCount())
                ));
            }

            return Optional.of(output);
        }

        return Optional.empty();
    }

    public long countItems(String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        if (StringUtils.isBlank(categoryUid) || ("null").equals(categoryUid)) {
            categoryUid = findInbox(userUid).getUid();
        }

        return datastore.countAll(Item.class,
                and(eq(Const.USER_UID, userUid), eq(Const.CATEGORY_UID, categoryUid)));
    }

    public boolean userExists(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        return datastore.find(User.class, eq(Const.UID, userUid)) != null;
    }

    public Optional<String> authenticateUser(String username, String password) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(password, Required.PASSWORD);

        User user = datastore.find(User.class, eq(Const.USERNAME, username));
        if (user != null && user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
            return Optional.of(user.getUid());
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findItems(String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidUuid(categoryUid), Invalid.CATEGORY_UID);

        List<Item> items = new ArrayList<>();
        datastore
                .query(Item.class)
                .find(and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.CATEGORY_UID, categoryUid))).into(items);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Item item: items) {
            output.add(Map.of(
                    Const.UID, item.getUid(),
                    "url", item.getUrl(),
                    "image", (storeImages && StringUtils.isNotBlank(item.getImageBase64())) ? item.getImageBase64() : (StringUtils.isBlank(item.getImage())) ? Strings.EMPTY : item.getImage(),
                    "title", item.getTitle(),
                    "sort", item.getTimestamp().toEpochSecond(ZoneOffset.UTC),
                    "added", DateUtils.getPrettyTime(item.getTimestamp()))); // FIX ME: Remove in later API version
        }

        return Optional.of(output);
    }

    public Optional<Boolean> deleteItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidUuid(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        var item = findItem(itemUid, userUid);
        var category = findCategory(item.getCategoryUid(), userUid);
        category.setCount(category.getCount() - 1);
        save(category);

        Category trash = findTrash(userUid);
        trash.setCount(trash.getCount() + 1);
        save(trash);

        var updateResult = datastore.query(Item.class).updateOne(and(
                eq(Const.USER_UID, userUid),
                eq(Const.UID, itemUid)),
                    Updates.set(Const.CATEGORY_UID, trash.getUid()));

        return updateResult.getModifiedCount() == 1 ? Optional.of(true) : Optional.empty();
    }

    public Optional<Boolean> emptyTrash(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        trash.setCount(0);
        save(trash);

        var deleteResult = datastore.query(Item.class).deleteMany(and(
                eq(Const.USER_UID, userUid),
                eq(Const.CATEGORY_UID, trash.getUid())));

        return deleteResult.wasAcknowledged() ? Optional.of(true) : Optional.empty();
    }

    private Category findTrash(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.USER_UID, userUid),
                    eq(Const.NAME, Const.TRASH)));
    }

    public Category findInbox(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.NAME, Const.INBOX)));
    }

    public Category findCategory(String categoryUid, String userUid) {
        Utils.checkCondition(Utils.isValidUuid(categoryUid), Invalid.CATEGORY_UID);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.UID, categoryUid),
                    eq(Const.USER_UID, userUid)));
    }

    public Item findItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidUuid(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(Item.class,
                and(
                        eq(Const.UID, itemUid),
                        eq(Const.USER_UID, userUid)));
    }

    public Optional<Boolean> moveItem(String itemUid, String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidUuid(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidUuid(categoryUid), Invalid.CATEGORY_UID);

        var item = findItem(itemUid, userUid);
        var sourceCategory = findCategory(item.getCategoryUid(), userUid);
        var targetCategory = findCategory(categoryUid, userUid);

        if (!sourceCategory.getUid().equals(targetCategory.getUid())) {
            sourceCategory.setCount(sourceCategory.getCount() - 1);
            save(sourceCategory);

            targetCategory.setCount(targetCategory.getCount() + 1);
            save(targetCategory);

            var updateResult = datastore.query(Item.class).updateOne(
                    and(
                            eq(Const.USER_UID, userUid),
                            eq(Const.UID, itemUid)),
                    Updates.set(Const.CATEGORY_UID, categoryUid));

            return Optional.of(updateResult.wasAcknowledged());
        }

        return Optional.empty();
    }

    public Optional<Boolean> addItem(String userUid, String url, String categoryUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidURL(url), Invalid.URL);
        User user = findUserByUid(userUid);

        if (user == null) {
            return Optional.empty();
        }

        LinkPreview linkPreview;
        try {
            linkPreview = LinkPreviewFetcher.fetch(url, user.getLanguage());
        } catch (Exception e) {
            LOG.error("Failed to fetch link preview", e);
            return Optional.empty();
        }

        Category category = null;
        if (Utils.isValidUuid(categoryUid)) {
            category = findCategory(categoryUid, userUid);
        }

        if (category == null) {
            category = findInbox(userUid);
        }

        if (category != null) {
            category.setCount(category.getCount() + 1);
            String categoryResult = save(category);

            var item = new Item(userUid, category.getUid(), url, linkPreview.imageUrl(), linkPreview.title());
            if (storeImages && !linkPreview.imageUrl().equals(PLACEHOLDER_IMAGE)) {
                item.setImageBase64(Utils.getImageAsBase64(linkPreview.imageUrl()).orElse(PLACEHOLDER_IMAGE));
            }
            String itemResult = save(item);

            return Optional.of(StringUtils.isNoneBlank(categoryResult, itemResult));
        } else {
            LOG.error("Failed to store item. Could not find any category.");
        }

        return Optional.empty();
    }

    public Optional<Boolean> addCategory(String userUid, String name) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Objects.requireNonNull(name, Required.CATEGORY_NAME);

        String result = save(new Category(name, userUid));

        return StringUtils.isNotBlank(result) ? Optional.of(true) : Optional.empty();
    }

    public Optional<Boolean> deleteCategory(String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidUuid(categoryUid), Invalid.CATEGORY_UID);

        Category inbox = findInbox(userUid);
        Category trash = findTrash(userUid);

        if (!categoryUid.equals(inbox.getUid()) && !categoryUid.equals(trash.getUid())) {
            var updateResult = datastore.query(Item.class)
                    .updateMany(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq(Const.CATEGORY_UID, categoryUid)),
                            Updates.set(Const.CATEGORY_UID, trash.getUid()));

            long modifiedCount = updateResult.getModifiedCount();
            trash.setCount((int) (trash.getCount() + modifiedCount));
            save(trash);

            var deleteResult = datastore.query(Category.class)
                    .deleteOne(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq(Const.UID, categoryUid)));

            return Optional.of(deleteResult.getDeletedCount() == 1);
        }

        return Optional.empty();
    }

    public User findUser(String username) {
        Objects.requireNonNull(username, Required.USERNAME);

        return datastore.find(User.class, eq(Const.USERNAME, username));
    }

    public User findUserByUid(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(User.class, eq(Const.UID, userUid));
    }

    public String save(Object object) {
        Objects.requireNonNull(object, Required.OBJECT);

        return datastore.save(object);
    }

    public Category findCategoryByName(String categoryName, String userUid) {
        Objects.requireNonNull(categoryName, Required.CATEGORY_NAME);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        return datastore.find(Category.class, and(eq(Const.NAME, categoryName), eq(Const.USER_UID, userUid)));
    }

    public boolean deleteAccount(String password, String userUid) {
        Objects.requireNonNull(password, Required.PASSWORD);
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null && user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
            DeleteResult deleteCategories = datastore.query(Category.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteItems = datastore.query(Item.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteUser = datastore.query(User.class).deleteOne(eq(Const.UID, userUid));

            return deleteCategories.wasAcknowledged() && deleteItems.wasAcknowledged() && deleteUser.wasAcknowledged();
        }

        return false;
    }

    public boolean userHasMfa(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa();
    }

    public boolean isValidMfa(String userUid, String otp) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidOtp(otp), Invalid.OTP);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa() && ( TotpUtils.verifiedTotp(user.getMfaSecret(), otp) || CodecUtils.matchArgon2(otp, user.getSalt(), user.getMfaFallback()));
    }

    public String changeMfa(String userUid, boolean mfa) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        String fallaback = null;
        var user = findUserByUid(userUid);
        if (mfa) {
            fallaback = MangooUtils.randomString(32);
            user.setMfaSecret(MangooUtils.randomString(64));
            user.setMfaFallback(CodecUtils.hashArgon2(fallaback, user.getSalt()));
        }
        user.setMfa(mfa);

        save(user);

        return fallaback;
    }

    public Optional<Action> findAction(String token) {
        Objects.requireNonNull(token, Required.TOKEN);

        return Optional.ofNullable(datastore.find(Action.class, eq("token", token)));
    }

    public void setPassword(String userUid, String password) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Objects.requireNonNull(password, Required.PASSWORD);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setPassword(CodecUtils.hashArgon2(password, user.getSalt()));
            save(user);
        }
    }

    public void deleteAction(Action action) {
        Objects.requireNonNull(action, Required.ACTION);

        datastore.delete(action);
    }

    public void confirmEmail(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setConfirmed(true);
            save(user);
        }
    }

    public void cleanActions() {
        datastore
                .query(Action.class)
                .deleteMany(lt("expires", LocalDateTime.now()));
    }

    public boolean updateLanguage(String userUid, String language) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        Objects.requireNonNull(language, Required.LANGUAGE);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setLanguage(language.toLowerCase());
            return save(user) != null;
        }

        return false;
    }

    public boolean updatePepper(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setPepper(MangooUtils.randomString(64));
            return save(user) != null;
        }

        return false;
    }

    public void convertImages() {
        datastore.findAll(Item.class,
                and(
                        ne("image", null),
                        eq("imageBase64", null),
                        ne("image", PLACEHOLDER_IMAGE)),
                Sorts.ascending("timestamp"))
            .forEach(item -> {
                    item.setImageBase64(Utils.getImageAsBase64(item.getImage()).orElse(PLACEHOLDER_IMAGE));
                    save(item);
        });
    }

    public void resync(String userUid) {
        Utils.checkCondition(Utils.isValidUuid(userUid), Invalid.USER_UID);
        LOG.info("Started resync");
        User user = findUserByUid(userUid);

        datastore.findAll(Item.class, eq("userUid", userUid), Sorts.ascending("timestamp"))
                .forEach(item -> {
                    LinkPreview linkPreview;
                    try {
                        linkPreview = LinkPreviewFetcher.fetch(item.getUrl(), user.getLanguage());
                        item.setImage(linkPreview.imageUrl());
                        if (storeImages && !linkPreview.imageUrl().equals(PLACEHOLDER_IMAGE)) {
                            item.setImageBase64(Utils.getImageAsBase64(linkPreview.imageUrl()).orElse(PLACEHOLDER_IMAGE));
                        }
                    } catch (Exception e) {
                        item.setImage(PLACEHOLDER_IMAGE);
                        LOG.error("Failed to fetch link preview", e);
                    }
                    save(item);
        });
        LOG.info("Finished resync");
    }
}