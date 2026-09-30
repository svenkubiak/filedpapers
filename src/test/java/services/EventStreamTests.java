package services;

import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.core.Config;
import io.mangoo.test.TestRunner;
import io.mangoo.persistence.interfaces.Datastore;
import models.Category;
import models.Item;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Drives the event stream end to end over http: ticket in the query string,
 * connection accepted, event delivered. The pieces are cheap to unit test on
 * their own, but whether a payload actually reaches the right socket is not
 * something they can answer.
 */
@ExtendWith({TestRunner.class})
public class EventStreamTests {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private String url(String ticket) {
        int port = Application.getInstance(Config.class).getConnectorHttpPort();
        return "http://localhost:" + port + "/api/v1/events?ticket=" + ticket;
    }

    /** An open stream, and whatever has arrived on it so far. */
    private record Listener(List<String> lines, InputStream body) {
        void hangUp() throws Exception {
            body.close();
        }

        String received() {
            return String.join("\n", lines);
        }
    }

    /** Opens the stream and collects whatever arrives, without blocking the test. */
    private List<String> listen(String ticket) throws Exception {
        return open(ticket).lines();
    }

    private Listener open(String ticket) throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url(ticket)))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(response.statusCode()).isEqualTo(200);

        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception e) {
                // the stream is closed when the test is done, that is expected
            }
        });

        return new Listener(lines, response.body());
    }

    private void awaitOpen(Listener... listeners) {
        for (Listener listener : listeners) {
            await().atMost(TIMEOUT).until(() -> listener.received().contains("stream.open"));
        }
    }

    private String userWithInbox() {
        User user = new User(Utils.randomString() + "@bar.com");
        Application.getInstance(io.mangoo.persistence.interfaces.Datastore.class).save(user);
        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Application.getInstance(io.mangoo.persistence.interfaces.Datastore.class).save(inbox);
        Category trash = new Category("Trash", user.getUid(), Role.TRASH);
        Application.getInstance(io.mangoo.persistence.interfaces.Datastore.class).save(trash);

        return user.getUid();
    }

    @Test
    void testAnEventReachesTheConnectedUser() throws Exception {
        //given a dashboard that is listening
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String categoryUid = Utils.randomString();
        String itemUid = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        //when a bookmark is added somewhere else
        eventService.itemAdded(userUid, categoryUid, itemUid);

        //then it arrives, and it names both the category and the item, so the
        //dashboard can fetch that one tile instead of reloading
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("item.added")));
        assertThat(String.join("\n", lines)).contains(categoryUid).contains(itemUid);
    }

    @Test
    void testTheTabsOfAUserSurviveOneOfThemClosing() throws Exception {
        //given one user with two dashboards open
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String categoryUid = Utils.randomString();
        String itemUid = Utils.randomString();

        Listener first = open(eventService.createTicket(userUid));
        Listener second = open(eventService.createTicket(userUid));
        awaitOpen(first, second);
        assertThat(eventService.connectionCount(userUid)).isEqualTo(2);

        //when one of them goes away - a closed tab, or the connection a reload
        //leaves behind after the new one has already registered
        first.hangUp();

        //a peer that is gone is noticed on the next write, which is what the
        //heartbeat is - it drops the connection it cannot reach any more
        await().atMost(TIMEOUT).until(() -> {
            eventService.heartbeat();
            return eventService.connectionCount(userUid) == 1;
        });

        //then the one that is still open keeps being served, instead of sitting
        //there without events and without a heartbeat until it times out
        eventService.itemAdded(userUid, categoryUid, itemUid);
        await().atMost(TIMEOUT).until(() -> second.received().contains("item.added"));
        assertThat(second.received()).contains(itemUid);
    }

    @Test
    void testTheHeartbeatReachesEveryConnectionOfAUser() throws Exception {
        //given one user with two dashboards open
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();

        Listener first = open(eventService.createTicket(userUid));
        Listener second = open(eventService.createTicket(userUid));
        awaitOpen(first, second);

        //when the keep alive goes out
        eventService.heartbeat();

        //then both of them get it, and it is a frame the dashboard can parse -
        //undertow writes a data frame for everything, so it cannot be a comment
        await().atMost(TIMEOUT).until(() -> first.received().contains("stream.ping")
                && second.received().contains("stream.ping"));
        assertThat(first.received()).contains("data:{\"event\":\"stream.ping\"}");
    }

    @Test
    void testAnEventDoesNotReachAnotherUser() throws Exception {
        //given two dashboards of two different users
        EventService eventService = Application.getInstance(EventService.class);
        String listener = userWithInbox();
        String other = userWithInbox();

        List<String> lines = listen(eventService.createTicket(listener));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        //when the other user gets a bookmark
        eventService.itemAdded(other, Utils.randomString(), Utils.randomString());
        eventService.itemsMoved(other, List.of(Utils.randomString()), Utils.randomString(), Utils.randomString());

        //then nothing of it shows up here
        Thread.sleep(1000);
        assertThat(String.join("\n", lines)).doesNotContain("item.added", "items.moved");
    }

    @Test
    void testAConnectionWithoutATicketIsClosed() throws Exception {
        //when connecting without a ticket
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("")))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        //then the handler drops it instead of leaving an anonymous stream open
        assertThat(response.body()).doesNotContain("stream.open");
    }

    @Test
    void testAConnectionWithASpentTicketIsClosed() throws Exception {
        //given a ticket that was already used
        EventService eventService = Application.getInstance(EventService.class);
        String ticket = eventService.createTicket(userWithInbox());
        eventService.redeemTicket(ticket);

        //when it is presented again
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(ticket)))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        //then
        assertThat(response.body()).doesNotContain("stream.open");
    }

    @Test
    void testAMoveNamesTheItemsAndBothCategories() throws Exception {
        //given a dashboard that is listening
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String from = Utils.randomString();
        String to = Utils.randomString();
        String first = Utils.randomString();
        String second = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        //when two bookmarks are moved elsewhere
        eventService.itemsMoved(userUid, List.of(first, second), from, to);

        //then the payload says what moved and where, so the dashboard can drop
        //the tiles and correct both counters instead of reloading
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("items.moved")));

        String received = String.join("\n", lines);
        assertThat(received).contains(first).contains(second).contains(from).contains(to);
    }

    @Test
    void testAMoveFromSeveralCategoriesOmitsTheSource() throws Exception {
        //given
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String to = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        //when the source is not a single category
        eventService.itemsMoved(userUid, List.of(Utils.randomString()), null, to);

        //then the event still arrives, just without a source to count down
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("items.moved")));
        assertThat(String.join("\n", lines)).doesNotContain("\"from\"");
    }

    /**
     * Restoring is a move like any other, but it is the one direction that was
     * suspected of having a branch of its own that forgets to announce itself.
     * It does not - and this pins that down, because two clients independently
     * failed to show a restored bookmark and the server was blamed for it.
     */
    @Test
    void testRestoringFromTrashAnnouncesTheMove() throws Exception {
        //given a bookmark that sits in the trash, and a dashboard listening
        DataService dataService = Application.getInstance(DataService.class);
        EventService eventService = Application.getInstance(EventService.class);
        Datastore datastore = Application.getInstance(Datastore.class);

        String userUid = userWithInbox();
        String inboxUid = dataService.findInbox(userUid).getUid();

        Item item = Item.create()
                .withUserUid(userUid)
                .withCategoryUid(inboxUid)
                .withUrl("https://example.com")
                .withTitle("restored");
        datastore.save(item);
        dataService.deleteItem(item.getUid(), userUid);

        Listener listener = open(eventService.createTicket(userUid));
        awaitOpen(listener);

        //when it is moved back into the inbox
        dataService.moveItem(item.getUid(), userUid, inboxUid);

        //then the move is announced like any other, naming the trash as source
        //and the inbox as target, so a dashboard on the inbox can pull the tile
        await().atMost(TIMEOUT).until(() -> listener.received().contains("items.moved"));

        String received = listener.received();
        assertThat(received).contains(item.getUid()).contains(inboxUid);
        assertThat(received).contains("\"to\":\"" + inboxUid + "\"");
    }

    @Test
    void testAnEmptiedTrashNamesTheCategory() throws Exception {
        //given
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String trashUid = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        //when
        eventService.trashEmptied(userUid, trashUid);

        //then
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("trash.emptied")));
        assertThat(String.join("\n", lines)).contains(trashUid);
    }
}
