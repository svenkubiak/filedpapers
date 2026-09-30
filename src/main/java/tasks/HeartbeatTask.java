package tasks;

import constants.Required;
import io.mangoo.annotations.Run;
import jakarta.inject.Inject;
import services.EventService;

import java.util.Objects;

/**
 * Keeps the event streams open. A dashboard without changes sends nothing for
 * minutes, and a proxy in front of the application treats that as an idle
 * connection and closes it - so a keep alive goes out well within the usual
 * sixty second timeout.
 */
public class HeartbeatTask {
    private final EventService eventService;

    @Inject
    public HeartbeatTask(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, Required.EVENT_SERVICE);
    }

    @Run(at = "Every 25s")
    public void execute() {
        eventService.heartbeat();
        eventService.purgeExpiredTickets();
    }
}
