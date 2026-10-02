package services;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import models.Category;
import models.Item;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({TestExtension.class})
public class DataServiceTrashTests {
    private Datastore datastore;
    private DataService dataService;
    private String userUid;
    private String inboxUid;
    private String trashUid;

    @BeforeEach
    public void init() {
        datastore = Application.getInstance(Datastore.class);
        dataService = Application.getInstance(DataService.class);

        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);
        datastore.dropCollection(User.class);

        User user = new User("trash@bar.com");
        datastore.save(user);

        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Category trash = new Category(Const.TRASH, user.getUid(), Role.TRASH);
        datastore.save(inbox);
        datastore.save(trash);

        userUid = user.getUid();
        inboxUid = inbox.getUid();
        trashUid = trash.getUid();
    }

    @Test
    void testCleanTrashRemovesExpiredItem() {
        String itemUid = saveItem(trashUid, LocalDateTime.now().minusHours(73));

        dataService.cleanTrash();

        assertThat(findItem(itemUid)).isNull();
    }

    @Test
    void testCleanTrashKeepsItemWithinRetention() {
        String itemUid = saveItem(trashUid, LocalDateTime.now());

        dataService.cleanTrash();

        assertThat(findItem(itemUid)).isNotNull();
    }

    @Test
    void testCleanTrashKeepsItemOutsideOfTrash() {
        // restored item that still carries a stale trash timestamp
        String itemUid = saveItem(inboxUid, LocalDateTime.now().minusHours(2));

        dataService.cleanTrash();

        assertThat(findItem(itemUid)).isNotNull();
    }

    @Test
    void testDeleteItemSetsTrashedTimestamp() {
        String itemUid = saveItem(inboxUid, null);

        dataService.deleteItem(itemUid, userUid);

        Item item = findItem(itemUid);
        assertThat(item.getCategoryUid()).isEqualTo(trashUid);
        assertThat(item.getTrashed()).isNotNull();
    }

    @Test
    void testMoveItemToTrashSetsTrashedTimestamp() {
        String itemUid = saveItem(inboxUid, null);

        dataService.moveItem(itemUid, userUid, trashUid);

        assertThat(findItem(itemUid).getTrashed()).isNotNull();
    }

    @Test
    void testMoveItemOutOfTrashClearsTrashedTimestamp() {
        String itemUid = saveItem(trashUid, LocalDateTime.now().minusHours(2));

        dataService.moveItem(itemUid, userUid, inboxUid);
        dataService.cleanTrash();

        Item item = findItem(itemUid);
        assertThat(item).isNotNull();
        assertThat(item.getTrashed()).isNull();
    }

    private String saveItem(String categoryUid, LocalDateTime trashed) {
        Item item = Item.create()
                .withUserUid(userUid)
                .withCategoryUid(categoryUid)
                .withUrl("https://svenkubiak.de")
                .withImage("foo")
                .withTitle("bar")
                .withDomain("foobar")
                .withDescription("barfoo");

        item.setTrashed(trashed);
        datastore.save(item);

        return item.getUid();
    }

    private Item findItem(String itemUid) {
        return datastore.query(Item.class).find(eq(Const.UID, itemUid)).first();
    }
}
