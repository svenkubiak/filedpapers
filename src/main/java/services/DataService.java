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
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.DateUtils;
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
import utils.SsrfGuard;
import utils.Utils;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.*;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;
import static constants.Const.PLACEHOLDER_IMAGE;

@Singleton
public class DataService {
    private static final Logger LOG = LogManager.getLogger(DataService.class);
    private static final String FAILED_TO_FETCH_LINK_PREVIEW = "Failed to fetch link preview";
    private static final int MAX_BULK_ITEMS = 200;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MIN_SEARCH_LENGTH = 2;
    private final Datastore datastore;
    private final MediaService mediaService;
    private final EventService eventService;
    private final String applicationUrl;

    @Inject
    public DataService(Datastore datastore,
                       MediaService mediaService,
                       EventService eventService,
                       @Named("application.url") String applicationUrl) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
        this.eventService = Objects.requireNonNull(eventService, Required.EVENT_SERVICE);
        this.applicationUrl = Objects.requireNonNull(applicationUrl, Required.APPLICATION_URL);
    }

    public void indexify() {
        datastore.query(Token.class)
                .createIndex(
                        Indexes.ascending(Const.EXPIRES_AT),
                        new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));

        // Entries written before expiresAt existed only carry a timestamp.
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

    public Optional<String> authenticateUser(String username, String password, Authentication authentication) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(password, Required.PASSWORD);

        User user = datastore.find(User.class, eq(Const.USERNAME, username));
        if (user != null && authentication.isValidLogin(user.getUid(), password, user.getSalt(), user.getPassword())) {
            return Optional.of(user.getUid());
        }

        return Optional.empty();
    }

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
            output.add(toMap(item));
        }

        return Optional.of(output);
    }

    public Optional<Map<String, Object>> findItemForDisplay(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Item item = datastore.find(Item.class, and(eq(Const.USER_UID, userUid), eq(Const.UID, itemUid)));
        if (item == null) {
            return Optional.empty();
        }

        // Not in the api list shape; the tile needs it as the drag source.
        Map<String, Object> entry = toMap(item);
        entry.put(Const.CATEGORY_UID, item.getCategoryUid());

        return Optional.of(entry);
    }

    private Map<String, Object> toMap(Item item) {
        Map<String, Object> entry = new HashMap<>(Map.of(
                Const.UID, item.getUid(),
                "url", safeUrl(item.getUrl()),
                "image", getImage(item),
                "title", item.getTitle(),
                "description", StringUtils.isNotBlank(item.getDescription()) ? item.getDescription() : Strings.EMPTY,
                "domain", StringUtils.isNotBlank(item.getDomain()) ? item.getDomain() : Strings.EMPTY,
                "sort", item.getTimestamp().toEpochSecond(ZoneOffset.UTC),
                "archived", item.isArchived(),
                "added", DateUtils.getPrettyTime(item.getTimestamp()))); // FIX ME: Remove in later API version

        if (item.getTrashed() != null) {
            entry.put("deleteAt", item.getTrashed().plusHours(Utils.getTrashRetention()).toEpochSecond(ZoneOffset.UTC));
        }

        return entry;
    }

    // XSS guard for hrefs: records stored before input-side scheme checks are only covered here.
    private String safeUrl(String url) {
        return Utils.isSafeLinkUrl(url) ? url : Const.BLANK_URL;
    }

    private String getImage(Item item) {
        if (StringUtils.isNotBlank(item.getMediaUid()) && mediaService.exists(item.getMediaUid())) {
            return applicationUrl + "/media/image/" + item.getMediaUid();
        } else if (Utils.isSafeLinkUrl(item.getImage())) {
            return item.getImage();
        }

        return PLACEHOLDER_IMAGE;
    }

    public Result.Of deleteItem(String itemUid, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(itemUid), Invalid.ITEM_UID);
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        // Must be read before the update moves the item.
        String source = sourceCategory(List.of(itemUid), userUid);
        var updateResult = datastore.query(Collections.ITEMS).updateOne(
                and(
                        eq(Const.USER_UID, userUid),
                        eq(Const.UID, itemUid)
                ),
                combine(
                        set(Const.CATEGORY_UID, trash.getUid()),
                        set(Const.TRASHED, LocalDateTime.now())
                )
        );

        if (updateResult.getModifiedCount() == 1) {
            eventService.itemsMoved(userUid, List.of(itemUid), source, trash.getUid());
            return Result.Success.empty();
        }

        return Result.Failure.server("Failed to delete item");
    }

    public Result.Of emptyTrash(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        Category trash = findTrash(userUid);
        Bson trashed = and(
                eq(Const.USER_UID, userUid),
                eq(Const.CATEGORY_UID, trash.getUid()));

        List<Item> items = new ArrayList<>();
        datastore.query(Item.class)
                .find(trashed)
                .projection(include(Const.USER_UID, Const.MEDIA_UID, Const.ARCHIVE_UID))
                .into(items);

        var deleteResult = datastore.query(Item.class).deleteMany(trashed);

        if (deleteResult.wasAcknowledged()) {
            items.forEach(this::deleteMedia);
        }

        if (deleteResult.wasAcknowledged()) {
            eventService.trashEmptied(userUid, trash.getUid());
            return Result.Success.empty();
        }

        return Result.Failure.server("Failed to empty trash");
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
            // TRASHED drives the automatic trash cleanup, so it must follow the target category.
            Bson update = targetCategory.getRole() == Role.TRASH
                    ? combine(set(Const.CATEGORY_UID, categoryUid), set(Const.TRASHED, LocalDateTime.now()))
                    : combine(set(Const.CATEGORY_UID, categoryUid), unset(Const.TRASHED));

            var updateResult = datastore.query(Collections.ITEMS).updateOne(
                    and(
                            eq(Const.USER_UID, userUid),
                            eq(Const.UID, itemUid)),
                    update);

            if (updateResult.wasAcknowledged()) {
                eventService.itemsMoved(userUid, List.of(itemUid), sourceCategory.getUid(), categoryUid);
                return Result.Success.empty();
            }

            return Result.Failure.server("Failed to move item");
        } else {
            // Moving into the current category is a no-op, not an error (same as the bulk move).
            return Result.Success.empty();
        }
    }

    public Result.Of moveItems(List<String> itemUids, String userUid, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidRandom(categoryUid), Invalid.CATEGORY_UID);
        List<String> uids = validUids(itemUids);

        var targetCategory = findCategory(categoryUid, userUid);
        if (targetCategory == null) {
            return Result.Failure.user("Invalid category");
        }

        Bson update = targetCategory.getRole() == Role.TRASH
                ? combine(set(Const.CATEGORY_UID, categoryUid), set(Const.TRASHED, LocalDateTime.now()))
                : combine(set(Const.CATEGORY_UID, categoryUid), unset(Const.TRASHED));

        String source = sourceCategory(uids, userUid);

        var updateResult = datastore.query(Collections.ITEMS).updateMany(
                and(
                        eq(Const.USER_UID, userUid),
                        in(Const.UID, uids),
                        ne(Const.CATEGORY_UID, categoryUid)),
                update);

        if (updateResult.wasAcknowledged()) {
            eventService.itemsMoved(userUid, uids, source, categoryUid);
            return Result.Success.empty();
        }

        return Result.Failure.server("Failed to move items");
    }

    // Soft delete: moves into the trash; cleanTrash() removes them later.
    public Result.Of deleteItems(List<String> itemUids, String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        List<String> uids = validUids(itemUids);

        Category trash = findTrash(userUid);
        String source = sourceCategory(uids, userUid);

        var updateResult = datastore.query(Collections.ITEMS).updateMany(
                and(
                        eq(Const.USER_UID, userUid),
                        in(Const.UID, uids),
                        ne(Const.CATEGORY_UID, trash.getUid())),
                combine(
                        set(Const.CATEGORY_UID, trash.getUid()),
                        set(Const.TRASHED, LocalDateTime.now())
                )
        );

        if (updateResult.wasAcknowledged()) {
            eventService.itemsMoved(userUid, uids, source, trash.getUid());
            return Result.Success.empty();
        }

        return Result.Failure.server("Failed to delete items");
    }

    // Null if the items sit in more than one category.
    private String sourceCategory(List<String> itemUids, String userUid) {
        List<Item> items = new ArrayList<>();
        datastore.query(Item.class)
                .find(and(eq(Const.USER_UID, userUid), in(Const.UID, itemUids)))
                .projection(include(Const.CATEGORY_UID))
                .into(items);

        return items.stream()
                .map(Item::getCategoryUid)
                .distinct()
                .count() == 1 ? items.getFirst().getCategoryUid() : null;
    }

    private List<String> validUids(List<String> itemUids) {
        Utils.checkCondition(itemUids != null && !itemUids.isEmpty(), Invalid.ITEM_UID);
        Utils.checkCondition(itemUids.size() <= MAX_BULK_ITEMS, "Too many items in a single request");

        List<String> uids = itemUids.stream().filter(Utils::isValidRandom).toList();
        Utils.checkCondition(uids.size() == itemUids.size(), Invalid.ITEM_UID);

        return uids;
    }

    // The term is quoted so user input is never sent to the database as a regex.
    public Optional<List<Map<String, Object>>> searchItems(String userUid, String query, int limit) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        String term = StringUtils.trimToEmpty(query);
        if (term.length() < MIN_SEARCH_LENGTH) {
            return Optional.of(List.of());
        }

        var pattern = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
        Category trash = findTrash(userUid);

        Map<String, String> categoryNames = new HashMap<>();
        List<Category> categories = new ArrayList<>();
        datastore.query(Category.class).find(eq(Const.USER_UID, userUid)).into(categories);
        categories.forEach(category -> categoryNames.put(category.getUid(), category.getName()));

        List<Item> items = new ArrayList<>();
        datastore.query(Item.class)
                .find(and(
                        eq(Const.USER_UID, userUid),
                        ne(Const.CATEGORY_UID, trash.getUid()),
                        or(
                                regex("title", pattern),
                                regex("domain", pattern),
                                regex("url", pattern))))
                .sort(Sorts.descending("timestamp"))
                .limit(Math.clamp(limit, 1, MAX_SEARCH_RESULTS))
                .into(items);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Item item : items) {
            output.add(Map.of(
                    Const.UID, item.getUid(),
                    "url", safeUrl(item.getUrl()),
                    "title", StringUtils.isNotBlank(item.getTitle()) ? item.getTitle() : safeUrl(item.getUrl()),
                    "domain", StringUtils.isNotBlank(item.getDomain()) ? item.getDomain() : Strings.EMPTY,
                    Const.CATEGORY_UID, item.getCategoryUid(),
                    "category", categoryNames.getOrDefault(item.getCategoryUid(), Strings.EMPTY),
                    "added", DateUtils.getPrettyTime(item.getTimestamp())));
        }

        return Optional.of(output);
    }

    public Result.Of addItem(String userUid, String url, String categoryUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(SsrfGuard.isPubliclyRoutable(url), Invalid.URL);
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

            if (StringUtils.isNoneBlank(categoryResult, itemResult)) {
                eventService.itemAdded(userUid, category.getUid(), item.getUid());
                return Result.Success.empty();
            }

            return Result.Failure.server("Failed to save bookmark");
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
            items.forEach(this::deleteMedia);

            DeleteResult deleteCategories = datastore.query(Category.class).deleteMany(eq(Const.USER_UID, userUid));
            DeleteResult deleteItems = datastore.query(Item.class).deleteMany(eq(Const.USER_UID, userUid));
            datastore.query(Avatar.class).deleteOne(eq(Const.USER_UID, userUid));
            DeleteResult deleteUser = datastore.query(User.class).deleteOne(eq(Const.UID, userUid));

            return deleteCategories.wasAcknowledged() && deleteItems.wasAcknowledged() && deleteUser.wasAcknowledged();
        }

        return false;
    }

    // Expects data already normalized by Avatars.normalize().
    public boolean saveAvatar(String userUid, byte[] data) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Objects.requireNonNull(data, Required.DATA);

        var user = findUserByUid(userUid);
        if (user == null) {
            return false;
        }

        var avatar = Optional.ofNullable(datastore.find(Avatar.class, eq(Const.USER_UID, userUid)))
                .orElseGet(() -> new Avatar(userUid));
        avatar.setData(data);
        datastore.save(avatar);

        user.setAvatar(Utils.randomString());
        save(user);

        return true;
    }

    public Optional<byte[]> findAvatar(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        return Optional.ofNullable(datastore.find(Avatar.class, eq(Const.USER_UID, userUid)))
                .map(Avatar::getData);
    }

    public void deleteAvatar(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        datastore.query(Avatar.class).deleteOne(eq(Const.USER_UID, userUid));

        var user = findUserByUid(userUid);
        if (user != null && user.getAvatar() != null) {
            user.setAvatar(null);
            save(user);
        }
    }

    public boolean userHasMfa(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        return user != null && user.isMfa();
    }

    public boolean isValidMfa(String userUid, String otp, Authentication authentication) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        Utils.checkCondition(Utils.isValidOtp(otp) || Utils.isValidMfaFallback(otp), Invalid.OTP);
        Objects.requireNonNull(authentication, Required.AUTHENTICATION);

        var user = findUserByUid(userUid);
        if (user == null || !user.isMfa()) {
            return false;
        }

        // Check the lock first, or a locked account could still trigger the expensive Argon2 fallback check.
        if (authentication.userHasSecondFactorLock(user.getUid())) {
            return false;
        }

        if (Utils.isValidOtp(otp)) {
            return authentication.isValidSecondFactor(user.getUid(), user.getMfaSecret(), otp);
        }

        return isValidMfaFallback(user, otp);
    }

    // Single use: a match disables mfa and rotates secret and code so it can't be replayed.
    private boolean isValidMfaFallback(User user, String fallback) {
        boolean matches = CommonUtils.matchArgon2(fallback, user.getSalt(), user.getMfaFallback());

        if (matches) {
            user.setMfa(false);
            user.setMfaFallback(Utils.randomString());
            user.setMfaSecret(TotpUtils.createSecret());
            save(user);
        }

        return matches;
    }

    public String enableMfa(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        String fallback = null;
        var user = findUserByUid(userUid);
        if (!user.isMfa()) {
            String code = Utils.randomString();
            fallback = code;
            user.setMfaFallback(CommonUtils.hashArgon2(code, user.getSalt()));
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
            // A reset implies suspected compromise, so all earlier sessions are revoked.
            user.setSessionsValidFrom(Instant.now().getEpochSecond());
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

    public boolean revokeSessions(String userUid) {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var user = findUserByUid(userUid);
        if (user != null) {
            user.setSessionsValidFrom(Instant.now().getEpochSecond());
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
                    if (!SsrfGuard.isPubliclyRoutable(item.getUrl())) {
                        LOG.warn("Skipping resync of item {} with non-public url", item.getUid());
                        return;
                    }

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
        datastore.query(Collections.ITEMS).updateMany(
                exists("imageBase64"),
                unset("imageBase64"));

        datastore.query(Collections.CATEGORIES).updateMany(
                exists("count"),
                unset("count"));

        datastore.query(Collections.USERS).updateMany(
                exists("refreshTokenKey"),
                unset("refreshTokenKey"));

        datastore.query(Collections.USERS).updateMany(
                exists("pepper"),
                unset("pepper"));

        datastore.query(Collections.USERS).updateMany(
                not(exists("sessionsValidFrom")),
                set("sessionsValidFrom", 0L));

        datastore.query(Collections.USERS).updateMany(
                not(exists("language")),
                set("language", Const.DEFAULT_LANGUAGE));

        datastore.query(Collections.ITEMS).updateMany(
                not(exists("archived")),
                set("archived", Boolean.FALSE));

        datastore.query(Collections.CATEGORIES).updateMany(
                and(eq("name", "Inbox"), exists("role", false)),
                set("role", "INBOX"));

        datastore.query(Collections.CATEGORIES).updateMany(
                and(eq("name", "Trash"), exists("role", false)),
                set("role", "TRASH"));

        datastore.query(Collections.CATEGORIES).updateMany(
                and(ne("name", "Inbox"), ne("name", "Trash"), exists("role", false)),
                set("role", "CUSTOM")
        );

        List<Item> items = new ArrayList<>();
        datastore.query(Item.class)
                .find(or(eq(Const.MEDIA_UID, null), eq(Const.MEDIA_UID, Strings.EMPTY)))
                .into(items);

        for (Item item : items) {
            datastore.query(Collections.ITEMS).updateOne(
                    eq("_id", item.getId()),
                    set(Const.MEDIA_UID, Utils.randomString())
            );
        }

        List<String> trashUids = new ArrayList<>();
        datastore.query(Category.class)
                .find(eq(Const.ROLE, Role.TRASH))
                .forEach(category -> trashUids.add(category.getUid()));

        if (!trashUids.isEmpty()) {
            datastore.query(Collections.ITEMS).updateMany(
                    and(in(Const.CATEGORY_UID, trashUids), not(exists(Const.TRASHED))),
                    set(Const.TRASHED, LocalDateTime.now())
            );
        }

        datastore.query(Collections.ITEMS).updateMany(
                and(nin(Const.CATEGORY_UID, trashUids), exists(Const.TRASHED)),
                unset(Const.TRASHED)
        );

        Thread.ofVirtual().start(() -> {
            datastore.query(Const.FILEDPAPERS_FILES)
                    .find(Filters.eq(Const.METADATA_UID, null))
                    .forEach(media -> {
                        var document = (Document) media;
                        var id = document.getObjectId("_id");

                        mediaService.delete(id);
                        LOG.info("Deleted media with null uid");
                    });

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

    public void cleanTrash() {
        List<String> trashUids = new ArrayList<>();
        datastore.query(Category.class)
                .find(eq(Const.ROLE, Role.TRASH))
                .forEach(category -> trashUids.add(category.getUid()));

        if (trashUids.isEmpty()) {
            return;
        }

        int trashRetention = Utils.getTrashRetention();

        Bson expired = and(
                in(Const.CATEGORY_UID, trashUids),
                lt(Const.TRASHED, LocalDateTime.now().minusHours(trashRetention))
        );

        List<Item> items = new ArrayList<>();
        datastore.query(Item.class)
                .find(expired)
                .projection(include(Const.USER_UID, Const.MEDIA_UID, Const.ARCHIVE_UID))
                .into(items);

        if (items.isEmpty()) {
            return;
        }

        var deleteResult = datastore.query(Item.class).deleteMany(expired);

        if (deleteResult.wasAcknowledged()) {
            items.forEach(this::deleteMedia);
            LOG.info("Removed {} expired item(s) from trash", deleteResult.getDeletedCount());
        } else {
            LOG.error("Failed to remove expired items from trash");
        }
    }

    private void deleteMedia(Item item) {
        if (StringUtils.isNotBlank(item.getMediaUid())) {
            mediaService.delete(item.getMediaUid(), item.getUserUid());
        }

        if (StringUtils.isNotBlank(item.getArchiveUid())) {
            mediaService.delete(item.getArchiveUid(), item.getUserUid());
        }
    }
}