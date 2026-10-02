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

@ExtendWith({TestRunner.class})
public class EventStreamTests {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private String url(String ticket) {
        int port = Application.getInstance(Config.class).getConnectorHttpPort();
        return "http://localhost:" + port + "/api/v1/events?ticket=" + ticket;
    }

    private record Listener(List<String> lines, InputStream body) {
        void hangUp() throws Exception {
            body.close();
        }

        String received() {
            return String.join("\n", lines);
        }
    }

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
                // expected: the stream is closed when the test ends
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
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String categoryUid = Utils.randomString();
        String itemUid = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        eventService.itemAdded(userUid, categoryUid, itemUid);

        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("item.added")));
        assertThat(String.join("\n", lines)).contains(categoryUid).contains(itemUid);
    }

    @Test
    void testTheTabsOfAUserSurviveOneOfThemClosing() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String categoryUid = Utils.randomString();
        String itemUid = Utils.randomString();

        Listener first = open(eventService.createTicket(userUid));
        Listener second = open(eventService.createTicket(userUid));
        awaitOpen(first, second);
        assertThat(eventService.connectionCount(userUid)).isEqualTo(2);

        first.hangUp();

        // a dead peer is only noticed on the next write, which is the heartbeat
        await().atMost(TIMEOUT).until(() -> {
            eventService.heartbeat();
            return eventService.connectionCount(userUid) == 1;
        });

        eventService.itemAdded(userUid, categoryUid, itemUid);
        await().atMost(TIMEOUT).until(() -> second.received().contains("item.added"));
        assertThat(second.received()).contains(itemUid);
    }

    @Test
    void testTheHeartbeatReachesEveryConnectionOfAUser() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();

        Listener first = open(eventService.createTicket(userUid));
        Listener second = open(eventService.createTicket(userUid));
        awaitOpen(first, second);

        eventService.heartbeat();

        // undertow writes everything as a data frame, so the ping cannot be an sse comment
        await().atMost(TIMEOUT).until(() -> first.received().contains("stream.ping")
                && second.received().contains("stream.ping"));
        assertThat(first.received()).contains("data:{\"event\":\"stream.ping\"}");
    }

    @Test
    void testAnEventDoesNotReachAnotherUser() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String listener = userWithInbox();
        String other = userWithInbox();

        List<String> lines = listen(eventService.createTicket(listener));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        eventService.itemAdded(other, Utils.randomString(), Utils.randomString());
        eventService.itemsMoved(other, List.of(Utils.randomString()), Utils.randomString(), Utils.randomString());

        Thread.sleep(1000);
        assertThat(String.join("\n", lines)).doesNotContain("item.added", "items.moved");
    }

    @Test
    void testAConnectionWithoutATicketIsClosed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("")))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.body()).doesNotContain("stream.open");
    }

    @Test
    void testAConnectionWithASpentTicketIsClosed() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String ticket = eventService.createTicket(userWithInbox());
        eventService.redeemTicket(ticket);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url(ticket)))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.body()).doesNotContain("stream.open");
    }

    @Test
    void testAMoveNamesTheItemsAndBothCategories() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String from = Utils.randomString();
        String to = Utils.randomString();
        String first = Utils.randomString();
        String second = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        eventService.itemsMoved(userUid, List.of(first, second), from, to);

        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("items.moved")));

        String received = String.join("\n", lines);
        assertThat(received).contains(first).contains(second).contains(from).contains(to);
    }

    @Test
    void testAMoveFromSeveralCategoriesOmitsTheSource() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String to = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        eventService.itemsMoved(userUid, List.of(Utils.randomString()), null, to);

        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("items.moved")));
        assertThat(String.join("\n", lines)).doesNotContain("\"from\"");
    }

    @Test
    void testRestoringFromTrashAnnouncesTheMove() throws Exception {
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

        dataService.moveItem(item.getUid(), userUid, inboxUid);

        await().atMost(TIMEOUT).until(() -> listener.received().contains("items.moved"));

        String received = listener.received();
        assertThat(received).contains(item.getUid()).contains(inboxUid);
        assertThat(received).contains("\"to\":\"" + inboxUid + "\"");
    }

    @Test
    void testAnEmptiedTrashNamesTheCategory() throws Exception {
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String trashUid = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("stream.open")));

        eventService.trashEmptied(userUid, trashUid);

        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("trash.emptied")));
        assertThat(String.join("\n", lines)).contains(trashUid);
    }
}
