package controllers.api;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JsonUtils;
import models.Category;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import services.EventService;

import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({TestExtension.class})
public class EventsControllerV1Tests {
    private static String ACCESS_TOKEN;
    private static String USER_UID;

    @BeforeEach
    public void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(User.class);

        User user = new User("events@bar.com");
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));

        String body = JsonUtils.toJson(Map.of("username", "events@bar.com", "password", "bar"));
        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        ACCESS_TOKEN = JsonUtils.toFlatMap(response.getContent()).get("accessToken");
        USER_UID = user.getUid();
    }

    @Test
    void testTicketUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/api/v1/events/ticket")
                .withContentType("application/json")
                .execute();

        //then an unauthenticated caller must not be able to obtain a ticket -
        //it is the only thing the sse route checks
        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void testTicket() {
        //when
        TestResponse response = TestRequest.post("/api/v1/events/ticket")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.ticket").isString();
    }

    @Test
    void testTheTicketBelongsToTheCallerAndWorksOnce() {
        //given
        TestResponse response = TestRequest.post("/api/v1/events/ticket")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        String ticket = JsonUtils.toFlatMap(response.getContent()).get("ticket");
        EventService eventService = Application.getInstance(EventService.class);

        //when the sse handler trades it in
        //then it names the user who asked for it, and only the first time
        assertThat(eventService.redeemTicket(ticket)).contains(USER_UID);
        assertThat(eventService.redeemTicket(ticket)).isEmpty();
    }

    @Test
    void testEveryCallHandsOutADifferentTicket() {
        //given
        String first = JsonUtils.toFlatMap(TestRequest.post("/api/v1/events/ticket")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute().getContent()).get("ticket");

        String second = JsonUtils.toFlatMap(TestRequest.post("/api/v1/events/ticket")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute().getContent()).get("ticket");

        //then
        assertThat(first).isNotEqualTo(second);
    }
}
