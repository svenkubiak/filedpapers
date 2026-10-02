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

// EventSource can't send an Authorization header and the sse route has no filters, so the
// stream authenticates ticket-first: a single-use, short-lived ticket traded for the user uid.
@Singleton
public class EventService {
    private static final Logger LOG = LogManager.getLogger(EventService.class);
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int MAX_TICKETS = 10_000;

    // Undertow can't send sse comments, so the keep-alive is an event the dashboard ignores.
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

    // Must only be called from an authenticated endpoint; this is where the user is established.
    public String createTicket(String userUid) {
        return createTicket(userUid, TICKET_TTL);
    }

    String createTicket(String userUid, Duration ttl) {
        Utils.checkCondition(Utils.isValidRandom(userUid), constants.Invalid.USER_UID);

        if (tickets.size() >= MAX_TICKETS) {
            purgeExpiredTickets();
        }

        String ticket = Utils.randomString();
        tickets.put(ticket, new Ticket(userUid, Instant.now().plus(ttl)));

        return ticket;
    }

    // Removes the ticket before checking expiry, so it can never be redeemed twice.
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
        connections.compute(userUid, (uid, values) -> {
            Set<ServerSentEventConnection> open = values == null ? ConcurrentHashMap.newKeySet() : values;
            open.add(connection);

            return open;
        });
    }

    // Removes only this connection: a close task can arrive late (e.g. behind a proxy), and
    // dropping the whole user would orphan a reconnect that registered in the meantime.
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

    public void itemAdded(String userUid, String categoryUid, String itemUid) {
        if (!Utils.isValidRandom(userUid) || !Utils.isValidRandom(categoryUid) || !Utils.isValidRandom(itemUid)) {
            return;
        }

        send(userUid, Map.of(
                "event", "item.added",
                Const.CATEGORY_UID, categoryUid,
                Const.UID, itemUid));
    }

    public void itemsMoved(String userUid, List<String> itemUids, String from, String to) {
        if (!Utils.isValidRandom(userUid) || itemUids == null || itemUids.isEmpty() || !Utils.isValidRandom(to)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>(Map.of(
                "event", "items.moved",
                "uids", itemUids,
                "to", to));

        if (Utils.isValidRandom(from)) {
            payload.put("from", from);
        }

        send(userUid, payload);
    }

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

    // Keeps proxies from closing idle streams. Sends per connection instead of via the
    // manager so closed ones are unregistered here even if their close task never arrives.
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

    int connectionCount(String userUid) {
        return connections.getOrDefault(userUid, Set.of()).size();
    }

    public void purgeExpiredTickets() {
        tickets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
