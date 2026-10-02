package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiAccessFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import services.EventService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiAccessFilter.class)
public class EventsControllerV1 {
    private final EventService eventService;

    @Inject
    public EventsControllerV1(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, Required.EVENT_SERVICE);
    }

    public Response ticket(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return Response.ok().bodyJson(Map.of("ticket", eventService.createTicket(userUid)));
    }
}
