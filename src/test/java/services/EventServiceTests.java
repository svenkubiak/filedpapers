package services;

import io.mangoo.core.Application;
import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ticket is what stands between an unauthenticated sse route and a user's
 * bookmarks, so its lifecycle is worth pinning down.
 */
@ExtendWith({TestRunner.class})
public class EventServiceTests {

    private EventService eventService() {
        return Application.getInstance(EventService.class);
    }

    @Test
    void testATicketNamesTheUserItWasIssuedFor() {
        //given
        String userUid = Utils.randomString();
        String ticket = eventService().createTicket(userUid);

        //when
        Optional<String> redeemed = eventService().redeemTicket(ticket);

        //then
        assertThat(redeemed).contains(userUid);
    }

    @Test
    void testATicketWorksExactlyOnce() {
        //given a ticket that has been used
        String ticket = eventService().createTicket(Utils.randomString());
        assertThat(eventService().redeemTicket(ticket)).isPresent();

        //when the same url is opened a second time
        Optional<String> second = eventService().redeemTicket(ticket);

        //then
        assertThat(second).isEmpty();
    }

    @Test
    void testAnExpiredTicketIsWorthless() {
        //given
        String ticket = eventService().createTicket(Utils.randomString(), Duration.ofSeconds(-1));

        //when
        Optional<String> redeemed = eventService().redeemTicket(ticket);

        //then
        assertThat(redeemed).isEmpty();
    }

    @Test
    void testAnUnknownTicketIsRejected() {
        assertThat(eventService().redeemTicket(Utils.randomString())).isEmpty();
    }

    @Test
    void testAMalformedTicketIsRejected() {
        assertThat(eventService().redeemTicket("not a ticket")).isEmpty();
        assertThat(eventService().redeemTicket("")).isEmpty();
        assertThat(eventService().redeemTicket(null)).isEmpty();
    }

    @Test
    void testATicketNeedsAUser() {
        assertThatThrownBy(() -> eventService().createTicket("nonsense uid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPurgingKeepsValidTickets() {
        //given
        String valid = eventService().createTicket(Utils.randomString());
        eventService().createTicket(Utils.randomString(), Duration.ofSeconds(-1));

        //when
        eventService().purgeExpiredTickets();

        //then
        assertThat(eventService().redeemTicket(valid)).isPresent();
    }

    @Test
    void testSendingToSomebodyWithoutAConnectionDoesNothing() {
        //a user whose dashboard is closed must not blow up the caller
        eventService().itemAdded(Utils.randomString(), Utils.randomString());
        eventService().itemsChanged(Utils.randomString());
        eventService().heartbeat();
    }
}
