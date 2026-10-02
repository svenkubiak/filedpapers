package handlers;

import constants.Required;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.EventService;

import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

// SSE routes run no filters, so connections authenticate here with a ticket from /api/v1/events/ticket.
@Singleton
public class DashboardEventHandler implements ServerSentEventConnectionCallback {
    private static final Logger LOG = LogManager.getLogger(DashboardEventHandler.class);

    // Tells the client the ticket was accepted, the first keep-alive comment only follows after 30s.
    // Undertow sends everything as a data frame, so this can't be an SSE comment; the client ignores stream.* events.
    private static final String OPEN = "{\"event\":\"stream.open\"}";

    private final EventService eventService;

    @Inject
    public DashboardEventHandler(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, Required.EVENT_SERVICE);
    }

    @Override
    public void connected(ServerSentEventConnection connection, String lastEventId) {
        Objects.requireNonNull(connection, Required.CONNECTION);

        Thread.ofVirtual().start(() -> {
            Optional<String> userUid = ticket(connection).flatMap(eventService::redeemTicket);

            if (userUid.isEmpty()) {
                LOG.debug("Rejected a server sent event connection without a valid ticket");
                close(connection);
                return;
            }

            String uid = userUid.orElseThrow();
            eventService.register(uid, connection);

            // The framework removes connections by request uri, not by this key.
            connection.addCloseTask(closed -> eventService.unregister(uid, closed));
            connection.send(OPEN);
        });
    }

    private Optional<String> ticket(ServerSentEventConnection connection) {
        Deque<String> values = connection.getQueryParameters().get("ticket");
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.peekFirst());
    }

    private void close(ServerSentEventConnection connection) {
        try {
            connection.shutdown();
        } catch (Exception e) {
            LOG.debug("Failed to shut down an unauthenticated server sent event connection", e);
        }
    }
}
