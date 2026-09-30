package services;

import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.core.Config;
import io.mangoo.test.TestRunner;
import models.Category;
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

    /** Opens the stream and collects whatever arrives, without blocking the test. */
    private List<String> listen(String ticket) throws Exception {
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

        return lines;
    }

    private String userWithInbox() {
        User user = new User(Utils.randomString() + "@bar.com");
        Application.getInstance(io.mangoo.persistence.interfaces.Datastore.class).save(user);
        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Application.getInstance(io.mangoo.persistence.interfaces.Datastore.class).save(inbox);

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
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("ok")));

        //when a bookmark is added somewhere else
        eventService.itemAdded(userUid, categoryUid, itemUid);

        //then it arrives, and it names both the category and the item, so the
        //dashboard can fetch that one tile instead of reloading
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("item.added")));
        assertThat(String.join("\n", lines)).contains(categoryUid).contains(itemUid);
    }

    @Test
    void testAnEventDoesNotReachAnotherUser() throws Exception {
        //given two dashboards of two different users
        EventService eventService = Application.getInstance(EventService.class);
        String listener = userWithInbox();
        String other = userWithInbox();

        List<String> lines = listen(eventService.createTicket(listener));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("ok")));

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
        assertThat(response.body()).doesNotContain("ok");
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
        assertThat(response.body()).doesNotContain("ok");
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
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("ok")));

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
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("ok")));

        //when the source is not a single category
        eventService.itemsMoved(userUid, List.of(Utils.randomString()), null, to);

        //then the event still arrives, just without a source to count down
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("items.moved")));
        assertThat(String.join("\n", lines)).doesNotContain("\"from\"");
    }

    @Test
    void testAnEmptiedTrashNamesTheCategory() throws Exception {
        //given
        EventService eventService = Application.getInstance(EventService.class);
        String userUid = userWithInbox();
        String trashUid = Utils.randomString();

        List<String> lines = listen(eventService.createTicket(userUid));
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("ok")));

        //when
        eventService.trashEmptied(userUid, trashUid);

        //then
        await().atMost(TIMEOUT).until(() -> lines.stream().anyMatch(line -> line.contains("trash.emptied")));
        assertThat(String.join("\n", lines)).contains(trashUid);
    }
}
