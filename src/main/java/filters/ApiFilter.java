package filters;

import constants.Const;
import constants.Required;
import io.mangoo.constants.Key;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.RequestUtils;
import io.mangoo.utils.paseto.Token;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import services.AuthenticationService;
import services.DataService;

import java.util.Objects;

public class ApiFilter implements PerRequestFilter {
    private final DataService dataService;
    private final AuthenticationService authenticationService;
    private final String cookieName;

    @Inject
    public ApiFilter(DataService dataService,
                     AuthenticationService authenticationService,
                     @Named(Key.AUTHENTICATION_COOKIE_NAME) String cookieName) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authenticationService = Objects.requireNonNull(authenticationService, Required.DATA_SERVICE);
        this.cookieName = Objects.requireNonNull(cookieName, Required.COOKIE_NAME);
    }

    @Override
    public Response execute(Request request, Response response) {
        var cookie = request.getCookie(cookieName);
        if (cookie != null) {
            String cookieValue = cookie.getValue();
            return authorize(cookieValue, request, response, true);
        }

        return RequestUtils.getAuthorizationHeader(request)
                .map(authorization -> authorize(authorization, request, response, false))
                .orElseGet(() -> Response.unauthorized().end());
    }

    private Response authorize(String authorization, Request request, Response response, boolean cookie) {
        try {
            Token token = null;
            if (cookie) {
                token = authenticationService.parseAuthenticationCookie(authorization);
            } else {
                token = authenticationService.parseAccessToken(authorization);
            }

            if (token != null) {
                return authenticationService.validateToken(token).map(userUid -> {
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