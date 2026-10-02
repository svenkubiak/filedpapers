package services;

import io.mangoo.core.Application;
import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith({TestRunner.class})
public class EventServiceTests {

    private EventService eventService() {
        return Application.getInstance(EventService.class);
    }

    @Test
    void testATicketNamesTheUserItWasIssuedFor() {
        String userUid = Utils.randomString();
        String ticket = eventService().createTicket(userUid);

        Optional<String> redeemed = eventService().redeemTicket(ticket);

        assertThat(redeemed).contains(userUid);
    }

    @Test
    void testATicketWorksExactlyOnce() {
        String ticket = eventService().createTicket(Utils.randomString());
        assertThat(eventService().redeemTicket(ticket)).isPresent();

        Optional<String> second = eventService().redeemTicket(ticket);

        assertThat(second).isEmpty();
    }

    @Test
    void testAnExpiredTicketIsWorthless() {
        String ticket = eventService().createTicket(Utils.randomString(), Duration.ofSeconds(-1));

        Optional<String> redeemed = eventService().redeemTicket(ticket);

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
        String valid = eventService().createTicket(Utils.randomString());
        eventService().createTicket(Utils.randomString(), Duration.ofSeconds(-1));

        eventService().purgeExpiredTickets();

        assertThat(eventService().redeemTicket(valid)).isPresent();
    }

    @Test
    void testSendingToSomebodyWithoutAConnectionDoesNothing() {
        eventService().itemAdded(Utils.randomString(), Utils.randomString(), Utils.randomString());
        eventService().itemsMoved(Utils.randomString(), List.of(Utils.randomString()), Utils.randomString(), Utils.randomString());
        eventService().trashEmptied(Utils.randomString(), Utils.randomString());
        eventService().purgeClosedConnections();
    }
}
