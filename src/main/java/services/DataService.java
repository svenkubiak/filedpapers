package services;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import constants.Const;
import constants.Invalid;
import constants.Required;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.DateUtils;
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
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import utils.Utils;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.unset;
import static constants.Const.PLACEHOLDER_IMAGE;

public class DataService {
    private static final Logger LOG = LogManager.getLogger(DataService.class);
    private final Datastore datastore;
    private final MediaService mediaService;
    private final String applicationUrl;

    @Inject
    public DataService(Datastore datastore, MediaService mediaService, @Named("application.url") String applicationUrl) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
        this.applicationUrl = Objects.requireNonNull(applicationUrl, Required.APPLICATION_URL);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> findCategories(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        if (StringUtils.isBlank(categoryUid) || ("null").equals(categoryUid)) {
            categoryUid = findInbox(userUid).getUid();
        }

        return datastore.countAll(Item.class,
                and(eq(Const.USER_UID, userUid), eq(Const.CATEGORY_UID, categoryUid)));
    }

    public boolean userExists(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);

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
                    "image", getImage(item),
                    "title", item.getTitle(),
                    "description", StringUtils.isNotBlank(item.getDescription()) ? item.getDescription() : Strings.EMPTY,
                    "domain", StringUtils.isNotBlank(item.getDomain()) ? item.getDomain() : Strings.EMPTY,
                    "sort", item.getTimestamp().toEpochSecond(ZoneOffset.UTC),
                    "added", DateUtils.getPrettyTime(item.getTimestamp()))); // FIX ME: Remove in later API version
        }

        return Optional.of(output);
    }

    private String getImage(Item item) {
        if (StringUtils.isNotBlank(item.getMediaUid()) && mediaService.exists(item.getMediaUid())) {
            return applicationUrl + "/media/image/" + item.getMediaUid();
        } else if (StringUtils.isNotBlank(item.getImage())) {
            return item.getImage();
        }

        return PLACEHOLDER_IMAGE;
    }

    public Optional<Boolean> deleteItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

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

        return updateResult.getModifiedCount() == 1 ? Optional.of(Boolean.TRUE) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<Boolean> emptyTrash(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        trash.setCount(0);
        save(trash);

        List<Item> items = new ArrayList<>();
        datastore.query(Item.class).find(and(
                eq(Const.USER_UID, userUid),
                eq(Const.CATEGORY_UID, trash.getUid()))).into(items);

        var deleteResult = datastore.query(Item.class).deleteMany(and(
                eq(Const.USER_UID, userUid),
                eq(Const.CATEGORY_UID, trash.getUid())));

        if (deleteResult.wasAcknowledged()) {
            items.stream()
                    .filter(item -> StringUtils.isNotBlank(item.getMediaUid()))
                    .forEach(item -> mediaService.delete(item.getMediaUid(), userUid));
        }

        return deleteResult.wasAcknowledged() ? Optional.of(Boolean.TRUE) : Optional.empty();
    }

    private Category findTrash(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.USER_UID, userUid),
                    eq(Const.NAME, Const.TRASH)));
    }

    public Category findInbox(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.NAME, Const.INBOX)));
    }

    public Category findCategory(String categoryUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.UID, categoryUid),
                    eq(Const.USER_UID, userUid)));
    }

    public Item findItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Item.class,
                and(
                        eq(Const.UID, itemUid),
                        eq(Const.USER_UID, userUid)));
    }

    public Optional<Boolean> moveItem(String itemUid, String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);

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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidURL(url), Invalid.URL);
        var user = findUserByUid(userUid);

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
        if (Utils.isValidRandom(categoryUid)) {
            category = findCategory(categoryUid, userUid);
        }

        if (category == null) {
            category = findInbox(userUid);
        }

        if (category != null) {
            category.setCount(category.getCount() + 1);
            String categoryResult = save(category);
            String image = linkPreview.image();

            var item = Item.create()
                    .withUserUid(userUid)
                    .withCategoryUid(category.getUid())
                    .withUrl(url)
                    .withImage(image)
                    .withTitle(linkPreview.title())
                    .withDomain(linkPreview.domain())
                    .withDescription(linkPreview.description());

            if (!PLACEHOLDER_IMAGE.equals(image) && StringUtils.isNotBlank(image)) {
                item.setMediaUid(mediaService.fetchAndStore(image, userUid).orElse(Utils.randomString()));
            } else {
                item.setMediaUid(Utils.randomString());
            }

            String itemResult = save(item);

            return Optional.of(StringUtils.isNoneBlank(categoryResult, itemResult));
        } else {
            LOG.error("Failed to store item. Could not find any category.");
        }

        return Optional.empty();
    }

    public Optional<Boolean> addCategory(String userUid, String name) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Objects.requireNonNull(name, Required.CATEGORY_NAME);

        String result = save(new Category(name, userUid));

        return StringUtils.isNotBlank(result) ? Optional.of(Boolean.TRUE) : Optional.empty();
    }

    public Optional<Boolean> deleteCategory(String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);

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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(User.class, eq(Const.UID, userUid));
    }

    public String save(Object object) {
        Objects.requireNonNull(object, Required.OBJECT);

        return datastore.save(object);
    }

    public Category findCategoryByName(String categoryName, String userUid) {
        Objects.requireNonNull(categoryName, Required.CATEGORY_NAME);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class, and(eq(Const.NAME, categoryName), eq(Const.USER_UID, userUid)));
    }

    public boolean deleteAccount(String password, String userUid) {
        Objects.requireNonNull(password, Required.PASSWORD);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null && user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
            List<Item> items = datastore.findAll(Item.class, eq(Const.USER_UID, userUid), Sorts.ascending(Const.USER_UID));
            items.stream()
                    .filter(item -> StringUtils.isNotBlank(item.getMediaUid()))
                    .forEach(item -> mediaService.delete(item.getMediaUid(), userUid));

            DeleteResult deleteCategories = datastore.query(Category.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteItems = datastore.query(Item.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteUser = datastore.query(User.class).deleteOne(eq(Const.UID, userUid));

            return deleteCategories.wasAcknowledged() && deleteItems.wasAcknowledged() && deleteUser.wasAcknowledged();
        }

        return false;
    }

    public boolean userHasMfa(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa();
    }

    public boolean isValidMfa(String userUid, String otp) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidOtp(otp), Invalid.OTP);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa() && ( TotpUtils.verifiedTotp(user.getMfaSecret(), otp) || CodecUtils.matchArgon2(otp, user.getSalt(), user.getMfaFallback()));
    }

    public String changeMfa(String userUid, boolean mfa) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        String fallback = null;
        var user = findUserByUid(userUid);
        if (mfa) {
            fallback = Utils.randomString();
            user.setMfaSecret(Utils.randomString());
            user.setMfaFallback(CodecUtils.hashArgon2(fallback, user.getSalt()));
        }
        user.setMfa(mfa);

        save(user);

        return fallback;
    }

    public Optional<Action> findAction(String token) {
        Objects.requireNonNull(token, Required.TOKEN);

        return Optional.ofNullable(datastore.find(Action.class, eq("token", token)));
    }

    public void setPassword(String userUid, String password) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

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
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Objects.requireNonNull(language, Required.LANGUAGE);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setLanguage(language.toLowerCase());
            return save(user) != null;
        }

        return false;
    }

    public boolean updatePepper(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setPepper(Utils.randomString());
            return save(user) != null;
        }

        return false;
    }

    public void resync(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        LOG.info("Started resync");
        var user = findUserByUid(userUid);

        datastore.findAll(Item.class, eq("userUid", userUid), Sorts.ascending("timestamp"))
                .forEach(item -> {
                    LinkPreview linkPreview;
                    try {
                        linkPreview = LinkPreviewFetcher.fetch(item.getUrl(), user.getLanguage());
                        String image = linkPreview.image();
                        item.setImage(image);
                        if (!PLACEHOLDER_IMAGE.equals(image) && StringUtils.isNotBlank(image)) {
                            mediaService.clean(item.getMediaUid(), item.getUserUid());
                            item.setMediaUid(mediaService.fetchAndStore(item.getImage(), item.getUserUid()).orElse(null));
                        }
                    } catch (Exception e) {
                        item.setImage(PLACEHOLDER_IMAGE);
                        LOG.error("Failed to fetch link preview", e);
                    }
                    save(item);
        });
        LOG.info("Finished resync");
    }

    @SuppressWarnings("unchecked")
    public void cleanup() {
        //Remove outdated Item attributes
        datastore.query(Item.class)
                .updateMany(exists("imageBase64"), unset("imageBase64"));

        Thread.ofVirtual().start(() -> {
            //Remove stored media with null uid valus
            datastore.query("filedpapers.files")
                    .find(Filters.eq(Const.METADATA_UID, null))
                    .forEach(media -> {
                        Document document = (Document) media;

                        ObjectId id = document.getObjectId("_id");

                        mediaService.delete(id);
                        LOG.info("Deleted media with null uid");
                    });

            //Remove all media that is not linked as a mediauid in an item
            List<String> usedMediaUids = new ArrayList<>();
            datastore.query(Item.class).find()
                    .projection(include("mediaUid"))
                    .forEach(doc -> {
                        Item item = (Item) doc;
                        if (item != null && StringUtils.isNotBlank(item.getMediaUid())) {
                            usedMediaUids.add(item.getMediaUid());
                        }
                    });

            if (!usedMediaUids.isEmpty()) {
                Bson filter = Filters.and(
                        Filters.nin(Const.METADATA_UID, usedMediaUids),
                        Filters.exists(Const.METADATA_UID, true),
                        Filters.exists(Const.METADATA_USER_UID, true),
                        Filters.ne(Const.METADATA_UID, null),
                        Filters.ne(Const.METADATA_USER_UID, null)
                );

                datastore.query("filedpapers.files")
                        .find(filter)
                        .forEach(media -> {
                            Document metadata = ((Document) media).get("metadata", Document.class);

                            var uid = metadata.getString(Const.UID);
                            var userUid = metadata.getString(Const.USER_UID);

                            mediaService.delete(uid, userUid);
                            LOG.info("Deleted unused media with uid {}", uid);
                        });
            }
        });
    }
}