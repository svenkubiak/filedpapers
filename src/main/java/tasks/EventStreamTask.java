package tasks;

import constants.Required;
import io.mangoo.annotations.Run;
import jakarta.inject.Inject;
import services.EventService;

import java.util.Objects;

// The keep-alive for idle SSE streams is sent by mangoo, this only cleans up behind them.
public class EventStreamTask {
    private final EventService eventService;

    @Inject
    public EventStreamTask(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, Required.EVENT_SERVICE);
    }

    @Run(at = "Every 30s")
    public void execute() {
        eventService.purgeClosedConnections();
        eventService.purgeExpiredTickets();
    }
}
