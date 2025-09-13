package services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import constants.Collections;
import constants.Const;
import constants.Invalid;
import constants.Required;
import de.svenkubiak.http.Http;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.DateUtils;
import io.mangoo.utils.JsonUtils;
import io.mangoo.utils.TotpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import models.*;
import models.enums.Role;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.bson.Document;
import org.bson.conversions.Bson;
import utils.Result;
import utils.Utils;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;
import static constants.Const.PLACEHOLDER_IMAGE;

@Singleton
public class DataService {
    private static final Logger LOG = LogManager.getLogger(DataService.class);
    private static final String FAILED_TO_FETCH_LINK_PREVIEW = "Failed to fetch link preview";
    private final Datastore datastore;
    private final MediaService mediaService;
    private final String applicationUrl;

    @Inject
    public DataService(Datastore datastore, MediaService mediaService, @Named("application.url") String applicationUrl) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
        this.applicationUrl = Objects.requireNonNull(applicationUrl, Required.APPLICATION_URL);
    }

    public void indexify() {
        datastore.query(Token.class)
                .createIndex(
                        Indexes.descending("timestamp"),
                        new IndexOptions().expireAfter(8L, TimeUnit.DAYS));
    }

    public Optional<List<Map<String, Object>>> findCategories(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        MongoCollection<Document> categories = datastore
                .getMongoDatabase()
                .getCollection("categories", Document.class);

        var pipeline = Arrays.asList(
                match(eq(Const.USER_UID, userUid)),
                lookup("items", "uid", "categoryUid", "items"),
                project(new Document("name", 1)
                        .append("uid", 1)
                        .append("itemCount", new Document("$size", "$items"))
                )
        );

        List<Map<String, Object>> output = new ArrayList<>();
        try (MongoCursor<Document> cursor = categories.aggregate(pipeline).iterator()) {
            while (cursor.hasNext()) {
                var document = cursor.next();
                output.add(Map.of(
                        Const.NAME, document.getString(Const.NAME),
                        Const.UID, document.getString(Const.UID),
                        Const.COUNT, String.valueOf(document.getInteger("itemCount", 0))
                ));
            }
            return output.isEmpty() ? Optional.empty() : Optional.of(output);
        }
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

    public Optional<String> authenticateUser(String username, String password, Authentication authentication) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(password, Required.PASSWORD);

        User user = datastore.find(User.class, eq(Const.USERNAME, username));
        if (user != null && authentication.isValidLogin(user.getUid(), password, user.getSalt(), user.getPassword())) {
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
                    "archived", item.isArchived(),
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

    public Result.Of deleteItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        var updateResult = datastore.query(Collections.ITEMS).updateOne(
                and(
                    eq(Const.USER_UID, userUid),
                    eq(Const.UID, itemUid)),
                        set(Const.CATEGORY_UID, trash.getUid()));

        return updateResult.getModifiedCount() == 1 ? Result.Success.empty() : Result.Failure.server("Failed to delete item");
    }

    @SuppressWarnings("unchecked")
    public Result.Of emptyTrash(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        List<String> mediaUidsToDelete = new ArrayList<>();
        datastore.query(Item.class)
                .find(and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.CATEGORY_UID, trash.getUid()),
                        ne(Const.MEDIA_UID, null),
                        ne(Const.MEDIA_UID, Strings.EMPTY)))
                .projection(include(Const.MEDIA_UID, Const.ARCHIVE_UID))
                .forEach(doc -> {
                    if (doc instanceof Item item) {
                        mediaUidsToDelete.add(item.getMediaUid());
                    }
                });

        var deleteResult = datastore.query(Item.class)
                .deleteMany(and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.CATEGORY_UID, trash.getUid())));

        if (deleteResult.wasAcknowledged()) {
            mediaUidsToDelete.forEach(mediaUid -> mediaService.delete(mediaUid, userUid));
        }

        return deleteResult.wasAcknowledged() ? Result.Success.empty() : Result.Failure.server("Failed to empty trash");
    }

    private Category findTrash(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                    eq(Const.USER_UID, userUid),
                    eq(Const.ROLE, Role.TRASH)));
    }

    public Category findInbox(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return datastore.find(Category.class,
                and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.ROLE, Role.INBOX)));
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

    public Result.Of moveItem(String itemUid, String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);

        var item = findItem(itemUid, userUid);
        var sourceCategory = findCategory(item.getCategoryUid(), userUid);
        var targetCategory = findCategory(categoryUid, userUid);

        if (!sourceCategory.getUid().equals(targetCategory.getUid())) {
            var updateResult = datastore.query(Collections.ITEMS).updateOne(
                    and(
                            eq(Const.USER_UID, userUid),
                            eq(Const.UID, itemUid)),
                    set(Const.CATEGORY_UID, categoryUid));

            return updateResult.wasAcknowledged() ? Result.Success.empty() : Result.Failure.server("Failed to move item");
        } else {
            return Result.Failure.server("Can not move an item into the same category");
        }
    }

    public Result.Of addItem(String userUid, String url, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidURL(url), Invalid.URL);
        var user = findUserByUid(userUid);

        if (user == null) {
            return Result.Failure.user("user does not exist");
        }

        LinkPreview linkPreview;
        try {
            linkPreview = LinkPreviewFetcher.fetch(url, user.getLanguage());
        } catch (Exception e) {
            LOG.error(FAILED_TO_FETCH_LINK_PREVIEW, e);
            return Result.Failure.server(FAILED_TO_FETCH_LINK_PREVIEW);
        }

        Category category = null;
        if (Utils.isValidRandom(categoryUid)) {
            category = findCategory(categoryUid, userUid);
        }

        if (category == null) {
            category = findInbox(userUid);
        }

        if (category != null) {
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

            return StringUtils.isNoneBlank(categoryResult, itemResult) ? Result.Success.empty() : Result.Failure.server("Failed to save bookmark");
        } else {
            return Result.Failure.user("category does not exist");
        }
    }

    public Result.Of addCategory(String userUid, String name) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidName(name), Invalid.CATEGORY_NAME);

        String result = null;
        if (findCategoryByName(name, userUid) == null) {
            result = save(new Category(name, userUid, Role.CUSTOM));
        } else {
            return Result.Failure.user("Category with same name already exists");
        }

        return StringUtils.isNotBlank(result) ? Result.Success.empty() : Result.Failure.server("Failed to add category");
    }

    public Result.Of deleteCategory(String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);

        Category inbox = findInbox(userUid);
        Category trash = findTrash(userUid);

        if (!categoryUid.equals(inbox.getUid()) && !categoryUid.equals(trash.getUid())) {
            datastore.query(Item.class)
                    .updateMany(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq(Const.CATEGORY_UID, categoryUid)),
                            set(Const.CATEGORY_UID, trash.getUid()));

            var deleteResult = datastore.query(Category.class)
                    .deleteOne(
                            and(
                                    eq(Const.USER_UID, userUid),
                                    eq(Const.UID, categoryUid)));

            return deleteResult.getDeletedCount() == 1 ? Result.Success.empty() : Result.Failure.server("Failed to delete category");
        } else {
            return Result.Failure.user("Can not delete Inbox or Trash");
        }
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

        var pattern = Pattern.compile("^" + categoryName + "$", Pattern.CASE_INSENSITIVE);
        return datastore.find(Category.class,
                and(
                        regex(Const.NAME, pattern),
                        eq(Const.USER_UID, userUid)));
    }

    public boolean deleteAccount(String password, String userUid) {
        Objects.requireNonNull(password, Required.PASSWORD);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null && user.getPassword().equals(CommonUtils.hashArgon2(password, user.getSalt()))) {
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
        return user != null && user.isMfa() && ( TotpUtils.verifyTotp(user.getMfaSecret(), otp) || CommonUtils.matchArgon2(otp, user.getSalt(), user.getMfaFallback()));
    }

    public String enableMfa(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        String fallback = null;
        var user = findUserByUid(userUid);
        if (!user.isMfa()) {
            fallback = Utils.randomString();
            user.setMfaFallback(CommonUtils.hashArgon2(fallback, user.getSalt()));
            user.setMfa(true);
            save(user);
        }

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
            user.setPassword(CommonUtils.hashArgon2(password, user.getSalt()));
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
                        LOG.error(FAILED_TO_FETCH_LINK_PREVIEW, e);
                    }
                    save(item);
        });
        LOG.info("Finished resync");
    }

    @SuppressWarnings("unchecked")
    public void upgrade() {
        //Remove outdated Item attributes
        datastore.query(Collections.ITEMS).updateMany(
                exists("imageBase64"),
                unset("imageBase64"));

        //Remove outdated Category attributes
        datastore.query(Collections.ITEMS).updateMany(
                exists("count"),
                unset("count"));

        //Add new refresh token key
        datastore.query(Collections.USERS).updateMany(
                exists("refreshTokenKey"),
                unset("refreshTokenKey"));

        //Add new archived value
        datastore.query(Collections.ITEMS).updateMany(
                not(exists("archived")),
                set("archived", false));

        //Add new role type to INBOX
        datastore.query(Collections.CATEGORIES).updateOne(
                and(eq("name", "Inbox"), exists("role", false)),
                set("role", "INBOX"));

        //Add new role type to TRASH
        datastore.query(Collections.CATEGORIES).updateOne(
                and(eq("name", "Trash"), exists("role", false)),
                set("role", "TRASH"));

        //Add new role type to CUSTOM categories
        datastore.query(Collections.CATEGORIES).updateMany(
                and(ne("name", "Inbox"), ne("name", "Trash"), exists("role", false)),
                set("role", "CUSTOM")
        );

        //Updated items which have null value mediaUids
        List<Item> items = new ArrayList<>();
        datastore.query(Collections.ITEMS)
                .find(or(eq(Const.MEDIA_UID, null), eq(Const.MEDIA_UID, Strings.EMPTY)))
                .into(items);

        for (Item item : items) {
            datastore.query(Collections.ITEMS).updateOne(
                    eq("_id", item.getId()),
                    set(Const.MEDIA_UID, Utils.randomString())
            );
        }

        Thread.ofVirtual().start(() -> {
            //Remove stored media with null uid valus
            datastore.query(Const.FILEDPAPERS_FILES)
                    .find(Filters.eq(Const.METADATA_UID, null))
                    .forEach(media -> {
                        var document = (Document) media;
                        var id = document.getObjectId("_id");

                        mediaService.delete(id);
                        LOG.info("Deleted media with null uid");
                    });

            //Remove all media that is not linked as a mediauid in an item
            List<String> usedMediaUids = new ArrayList<>();
            datastore.query(Item.class).find()
                    .projection(include(Const.MEDIA_UID, Const.ARCHIVE_UID))
                    .forEach(doc -> {
                        var item = (Item) doc;
                        if (item != null && ( StringUtils.isNotBlank(item.getMediaUid()) || StringUtils.isNotBlank(item.getArchiveUid()) )) {
                            usedMediaUids.add(item.getMediaUid());
                            usedMediaUids.add(item.getArchiveUid());
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

                datastore.query(Const.FILEDPAPERS_FILES)
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

    public Result.Of updateCategory(String userUid, String categoryUid, String name) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);
        Utils.checkCondition(Utils.isValidRandom(name), Invalid.CATEGORY_NAME);

        var category = findCategory(categoryUid, userUid);
        if (category != null && category.getRole() != Role.INBOX && category.getRole() != Role.TRASH) {
            category.setName(name);
            return save(category) != null ? Result.Success.empty() : Result.Failure.server("Failed to rename category");
        } else {
            return Result.Failure.user("Category either not exists it is Inbox or Trash or a category with same name already exists");
        }
    }

    public Result.Of archive(String uid, String userUid) {
        Objects.requireNonNull(uid, Required.UID);

        Item item = findItem(uid, userUid);
        if (item != null) {
            LOG.info("Archiving media with url {}", item.getUrl());

            var result = Http.get(LinkPreviewFetcher.getUrl() + "/archive?url=" + item.getUrl())
                    .withTimeout(Duration.ofSeconds(120))
                    .send();

            if (result.isValid()) {
                Map<String, String> json = JsonUtils.toFlatMap(result.body());
                if (!json.isEmpty() && json.get("success").equals("true")) {
                    String archiveUid = mediaService.store(json.get("archive").getBytes(StandardCharsets.UTF_8), item.getUserUid());
                    item.setArchived(true);
                    item.setArchiveUid(archiveUid);
                    save(item);

                    return Result.Success.empty();
                }
            } else {
                return Result.Failure.server("Received invalid response from archiving endpoint");
            }
        } else {
            return Result.Failure.user("Could not find item");
        }

        return Result.Failure.server("Failed to archive item");
    }

    public Optional<byte[]> findArchive(Item item) {
        Objects.requireNonNull(item, "item cannot be null");

        return mediaService.retrieve(item.getArchiveUid());
    }

    public boolean tokenExists(String id) {
        return datastore.query(Token.class).find(eq ("uid", id)).first() != null;
    }
}