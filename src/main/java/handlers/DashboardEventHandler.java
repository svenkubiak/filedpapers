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

/**
 * Accepts a dashboard that wants to be told about changes.
 *
 * The sse route carries no filters, so this is where the connection is
 * authenticated: the client passes the ticket it fetched from
 * /api/v1/events/ticket as a query parameter, and a connection that cannot
 * produce a valid one is closed right away. Nothing is ever sent to a
 * connection that has not been tied to a user.
 */
@Singleton
public class DashboardEventHandler implements ServerSentEventConnectionCallback {
    private static final Logger LOG = LogManager.getLogger(DashboardEventHandler.class);
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

            // The framework listener removes a connection under its request uri,
            // which is not the key it was added with here, so the clean up has to
            // name that key itself.
            connection.addCloseTask(closed -> eventService.unregister(uid, closed));
            connection.send(": ok");
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
