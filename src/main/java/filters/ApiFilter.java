package filters;

import constants.Const;
import constants.Required;
import io.mangoo.constants.Key;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.RequestUtils;
import io.undertow.server.handlers.Cookie;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import services.DataService;
import utils.Utils;

import java.util.Objects;

public class ApiFilter implements PerRequestFilter {
    private final DataService dataService;
    private final String apiSecret;
    private final String cookieName;
    private final String cookieSecret;

    @Inject
    public ApiFilter(DataService dataService,
                     @Named("api.accessToken.secret") String apiSecret,
                     @Named(Key.AUTHENTICATION_COOKIE_NAME) String cookieName,
                     @Named(Key.AUTHENTICATION_COOKIE_SECRET) String cookieSecret) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.apiSecret = Objects.requireNonNull(apiSecret, Required.API_SECRET);
        this.cookieName = Objects.requireNonNull(cookieName, Required.COOKIE_NAME);
        this.cookieSecret = Objects.requireNonNull(cookieSecret, Required.COOKIE_SECRET);
    }

    @Override
    public Response execute(Request request, Response response) {
        var cookie = request.getCookie(cookieName);
        if (cookie != null) {
            String cookieValue = cookie.getValue();
            return authorize(cookieValue, request, response, cookieSecret);
        }

        return RequestUtils.getAuthorizationHeader(request)
                .map(authorization -> authorize(authorization, request, response, apiSecret))
                .orElseGet(() -> Response.unauthorized().end());
    }

    private Response authorize(String authorization, Request request, Response response, String secret) {
        try {
            var token = Utils.parsePaseto(authorization, secret);
            if (token != null) {
                return Utils.validateToken(token).map(userUid -> {
                    if (dataService.userExists(userUid)) {
                        request.addAttribute(Const.USER_UID, userUid);
                        return response;
                    }
                    return Response.unauthorized().end();
                }).orElseGet(() -> Response.unauthorized().end());
            }
        } catch (MangooTokenException e) {
            return Response.unauthorized().end();
        }

        return Response.unauthorized().end();
    }
}