package tasks;

import constants.Required;
import io.mangoo.annotations.Run;
import jakarta.inject.Inject;
import services.EventService;

import java.util.Objects;

// Keeps idle SSE streams alive; proxies typically close them after 60s.
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
