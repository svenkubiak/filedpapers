package services;

import constants.Const;
import constants.Required;
import io.mangoo.manager.ServerSentEventManager;
import io.mangoo.utils.JsonUtils;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.Utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes changes to the dashboards of a single user.
 *
 * EventSource cannot send an Authorization header, and the sse route has no
 * filters, so the connection authenticates itself with a ticket in the query
 * string: the client asks an authenticated endpoint for one, hands it over when
 * it connects, and the handler trades it for the user it belongs to. A ticket is
 * valid for {@link #TICKET_TTL} and can be redeemed exactly once, which keeps
 * the window in which a leaked url is worth anything very short.
 *
 * Connections are registered under the user uid, so a send reaches the tabs of
 * that one user and nobody else.
 */
@Singleton
public class EventService {
    private static final Logger LOG = LogManager.getLogger(EventService.class);
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int MAX_TICKETS = 10_000;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Set<String> connectedUsers = ConcurrentHashMap.newKeySet();
    private final ServerSentEventManager eventManager;

    private record Ticket(String userUid, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    @Inject
    public EventService(ServerSentEventManager eventManager) {
        this.eventManager = Objects.requireNonNull(eventManager, Required.EVENT_MANAGER);
    }

    /**
     * Hands out a single use ticket for the given user. Called from an
     * authenticated endpoint - this is the only place the user is established.
     *
     * @param userUid the user the ticket belongs to
     * @return the ticket to pass to the sse endpoint
     */
    public String createTicket(String userUid) {
        return createTicket(userUid, TICKET_TTL);
    }

    /**
     * Same as {@link #createTicket(String)} with an explicit lifetime, so a test
     * can hand out a ticket that is already expired without waiting for it.
     *
     * @param userUid the user the ticket belongs to
     * @param ttl how long the ticket stays valid
     * @return the ticket to pass to the sse endpoint
     */
    String createTicket(String userUid, Duration ttl) {
        Utils.checkCondition(Utils.isValidRandom(userUid), constants.Invalid.USER_UID);

        // A client that never connects leaves its ticket behind. Expired ones go
        // on the way in, so the map cannot grow without an upper bound.
        if (tickets.size() >= MAX_TICKETS) {
            purgeExpiredTickets();
        }

        String ticket = Utils.randomString();
        tickets.put(ticket, new Ticket(userUid, Instant.now().plus(ttl)));

        return ticket;
    }

    /**
     * Trades a ticket for the user it was issued to. The ticket is gone
     * afterwards, whether it was still valid or not.
     *
     * @param ticket the ticket handed over by the client
     * @return the user uid, or empty if the ticket is unknown or expired
     */
    public Optional<String> redeemTicket(String ticket) {
        if (ticket == null || !Utils.isValidRandom(ticket)) {
            return Optional.empty();
        }

        Ticket value = tickets.remove(ticket);
        if (value == null || value.isExpired()) {
            return Optional.empty();
        }

        return Optional.of(value.userUid());
    }

    public void register(String userUid, ServerSentEventConnection connection) {
        Objects.requireNonNull(connection, Required.CONNECTION);
        Utils.checkCondition(Utils.isValidRandom(userUid), constants.Invalid.USER_UID);

        eventManager.addConnection(userUid, connection);
        connectedUsers.add(userUid);
    }

    public void unregister(String userUid, ServerSentEventConnection connection) {
        if (userUid == null || connection == null) {
            return;
        }

        eventManager.removeConnection(userUid, connection);
        connectedUsers.remove(userUid);
    }

    /**
     * Tells the dashboards of a user that a category gained an item. The payload
     * carries the category rather than the item itself: the dashboard renders
     * server side, so it reloads the view it is showing and leaves the others
     * alone.
     *
     * @param userUid the owner of the item
     * @param categoryUid the category the item landed in
     */
    public void itemAdded(String userUid, String categoryUid) {
        if (!Utils.isValidRandom(userUid) || !Utils.isValidRandom(categoryUid)) {
            return;
        }

        send(userUid, Map.of(
                "event", "item.added",
                Const.CATEGORY_UID, categoryUid));
    }

    /**
     * Tells the dashboards of a user that its list is out of date without saying
     * which one - a move, a delete or an emptied trash can touch two categories
     * at once, and working out which view is affected is not worth the payload.
     *
     * @param userUid the owner of the items
     */
    public void itemsChanged(String userUid) {
        if (!Utils.isValidRandom(userUid)) {
            return;
        }

        send(userUid, Map.of("event", "items.changed"));
    }

    private void send(String userUid, Map<String, Object> payload) {
        if (!connectedUsers.contains(userUid)) {
            return;
        }

        try {
            eventManager.send(userUid, JsonUtils.toJson(payload));
        } catch (RuntimeException e) {
            LOG.error("Failed to send a server sent event to user '{}'", userUid, e);
        }
    }

    /**
     * Keeps the connections alive. Proxies in front of the application close an
     * idle connection after a minute or so, and a dashboard that sits there
     * without changes is idle by definition.
     */
    public void heartbeat() {
        connectedUsers.forEach(userUid -> {
            try {
                eventManager.send(userUid, ": ping");
            } catch (RuntimeException e) {
                LOG.debug("Heartbeat for user '{}' failed", userUid, e);
            }
        });
    }

    public void purgeExpiredTickets() {
        tickets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
