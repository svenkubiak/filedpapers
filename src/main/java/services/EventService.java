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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // Undertow has no api for an sse comment - send() always writes a data frame -
    // so the keep alive travels as an event the dashboard knows to drop.
    public static final String PING = "{\"event\":\"stream.ping\"}";

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, Set<ServerSentEventConnection>> connections = new ConcurrentHashMap<>();
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

    /**
     * Takes note of one connection of a user. The connections are held per user
     * and not just as a set of users, because a user regularly has more than one:
     * a second tab, and the connection a reload leaves behind until the socket is
     * actually torn down.
     *
     * @param userUid the user the connection belongs to
     * @param connection the connection to send to from now on
     */
    public void register(String userUid, ServerSentEventConnection connection) {
        Objects.requireNonNull(connection, Required.CONNECTION);
        Utils.checkCondition(Utils.isValidRandom(userUid), constants.Invalid.USER_UID);

        eventManager.addConnection(userUid, connection);
        connections.compute(userUid, (uid, values) -> {
            Set<ServerSentEventConnection> open = values == null ? ConcurrentHashMap.newKeySet() : values;
            open.add(connection);

            return open;
        });
    }

    /**
     * Drops one connection of a user, and the user only with its last one.
     *
     * The close task of a connection can run long after the client gave up on it
     * - a proxy that resets the stream towards the browser does not necessarily
     * tear down its own connection to us at the same moment. Removing the user
     * here would take the reconnect that has meanwhile registered down with it,
     * and that connection would then sit there without events and without a
     * heartbeat until the next idle timeout.
     *
     * @param userUid the user the connection belongs to
     * @param connection the connection that is gone
     */
    public void unregister(String userUid, ServerSentEventConnection connection) {
        if (userUid == null || connection == null) {
            return;
        }

        eventManager.removeConnection(userUid, connection);
        connections.computeIfPresent(userUid, (uid, values) -> {
            values.remove(connection);

            return values.isEmpty() ? null : values;
        });
    }

    /**
     * Tells the dashboards of a user that a category gained an item.
     *
     * The payload names the item instead of carrying it: the dashboard fetches
     * the rendered tile from the item endpoint, which keeps the markup in one
     * place instead of a second copy in javascript.
     *
     * @param userUid the owner of the item
     * @param categoryUid the category the item landed in
     * @param itemUid the item that was added
     */
    public void itemAdded(String userUid, String categoryUid, String itemUid) {
        if (!Utils.isValidRandom(userUid) || !Utils.isValidRandom(categoryUid) || !Utils.isValidRandom(itemUid)) {
            return;
        }

        send(userUid, Map.of(
                "event", "item.added",
                Const.CATEGORY_UID, categoryUid,
                Const.UID, itemUid));
    }

    /**
     * Tells the dashboards of a user that bookmarks changed category. Deleting
     * is a move into the trash here, so it travels on this event too.
     *
     * @param userUid the owner of the items
     * @param itemUids the items that moved
     * @param from the category they came from, or null if they came from several
     * @param to the category they landed in
     */
    public void itemsMoved(String userUid, List<String> itemUids, String from, String to) {
        if (!Utils.isValidRandom(userUid) || itemUids == null || itemUids.isEmpty() || !Utils.isValidRandom(to)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>(Map.of(
                "event", "items.moved",
                "uids", itemUids,
                "to", to));

        // A selection always comes from one view, but an api client can move
        // items that sit in different categories - then the dashboards can only
        // drop the tiles they find and leave the counters to the next load.
        if (Utils.isValidRandom(from)) {
            payload.put("from", from);
        }

        send(userUid, payload);
    }

    /**
     * Tells the dashboards of a user that the trash is empty. Unlike a move,
     * this one is final - the items are gone, not filed elsewhere.
     *
     * @param userUid the owner of the items
     * @param trashUid the trash category
     */
    public void trashEmptied(String userUid, String trashUid) {
        if (!Utils.isValidRandom(userUid) || !Utils.isValidRandom(trashUid)) {
            return;
        }

        send(userUid, Map.of(
                "event", "trash.emptied",
                Const.CATEGORY_UID, trashUid));
    }

    private void send(String userUid, Map<String, Object> payload) {
        if (!connections.containsKey(userUid)) {
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
     *
     * Addresses every connection on its own instead of going through the manager:
     * one that is no longer open is dropped right here, so an entry whose close
     * task never arrived cannot outlive the socket it stands for.
     */
    public void heartbeat() {
        List<Map.Entry<String, ServerSentEventConnection>> stale = new ArrayList<>();

        connections.forEach((userUid, values) -> values.forEach(connection -> {
            if (connection.isOpen()) {
                try {
                    connection.send(PING);
                } catch (RuntimeException e) {
                    LOG.debug("Heartbeat for user '{}' failed", userUid, e);
                }
            } else {
                stale.add(Map.entry(userUid, connection));
            }
        }));

        stale.forEach(entry -> unregister(entry.getKey(), entry.getValue()));
    }

    /**
     * How many connections of a user are currently held. Package private: a test
     * has to be able to see the bookkeeping, nothing in the application does.
     *
     * @param userUid the user to count the connections of
     * @return the number of connections held for that user
     */
    int connectionCount(String userUid) {
        return connections.getOrDefault(userUid, Set.of()).size();
    }

    public void purgeExpiredTickets() {
        tickets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
