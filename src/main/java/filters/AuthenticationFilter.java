package filters;

import constants.Const;
import constants.Required;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import models.enums.Type;
import org.apache.commons.lang3.StringUtils;
import services.DataService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class AuthenticationFilter implements PerRequestFilter {
    private final DataService dataService;
    private final Map<Type, String> allowed;

    @Inject
    public AuthenticationFilter(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.allowed = Map.of(
                Type.RESET_PASSWORD, "/auth/reset-password",
                Type.CONFIRM_EMAIL, "/auth/confirm"
        );
    }

    @Override
    public Response execute(Request request, Response response) {
        Objects.requireNonNull(request, Required.REQUEST);
        Objects.requireNonNull(request, Required.RESPONSE);

        String uri = request.getURI();
        String token = request.getParameter("token");
        if (StringUtils.isNoneBlank(token, uri)) {
            return dataService.findAction(token)
                    .filter(action -> LocalDateTime.now().isBefore(action.getExpires()))
                    .filter(action -> uri.startsWith(allowed.get(action.getType())))
                    .map(action -> {
                        request.addAttribute(Const.ACTION, action);
                        return response;
                    }).orElseGet(() -> Response.redirect("/error").end());
        }

        return Response.redirect("/error").end();
    }
}