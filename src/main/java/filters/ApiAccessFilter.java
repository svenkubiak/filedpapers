package filters;

import com.nimbusds.jwt.JWTClaimsSet;
import constants.Const;
import constants.Required;
import io.mangoo.constants.Header;
import io.mangoo.constants.Key;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.RequestUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.AuthenticationService;
import services.DataService;

import java.util.Objects;

public class ApiAccessFilter implements PerRequestFilter {
    private static final Logger LOG = LogManager.getLogger(DataService.class);
    private final DataService dataService;
    private final AuthenticationService authenticationService;
    private final String cookieName;

    @Inject
    public ApiAccessFilter(DataService dataService,
                           AuthenticationService authenticationService,
                           @Named(Key.AUTHENTICATION_COOKIE_NAME) String cookieName) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authenticationService = Objects.requireNonNull(authenticationService, Required.DATA_SERVICE);
        this.cookieName = Objects.requireNonNull(cookieName, Required.COOKIE_NAME);
    }

    @Override
    public Response execute(Request request, Response response) {
        Objects.requireNonNull(request, Required.REQUEST);
        Objects.requireNonNull(request, Required.RESPONSE);

        var cookie = request.getCookie(cookieName);
        if (cookie != null && !hasAccessToken(request)) {
            String cookieValue = cookie.getValue();
            return authorize(cookieValue, request, response, true);
        }

        return RequestUtils.getAuthorizationHeader(request)
                .map(authorization -> authorize(authorization, request, response, false))
                .orElseGet(() -> Response.unauthorized().end());
    }

    private boolean hasAccessToken(Request request) {
        return StringUtils.isNotBlank(request.getHeader(Header.AUTHORIZATION));
    }

    private Response authorize(String authorization, Request request, Response response, boolean cookie) {
        try {
            JWTClaimsSet jwtClaimsSet;
            if (cookie) {
                jwtClaimsSet = authenticationService.parseAuthenticationCookie(authorization);
            } else {
                jwtClaimsSet = authenticationService.parseAccessToken(authorization);
            }

            if (jwtClaimsSet != null) {
                String userUid = jwtClaimsSet.getSubject();

                if (cookie && !request.hasValidCsrf()) {
                    return Response.unauthorized().end();
                }

                if (dataService.userExists(userUid)) {
                    request.addAttribute(Const.USER_UID, userUid);
                    return response;
                }

                return Response.unauthorized().end();
            }
        } catch (MangooJwtException e) {
            LOG.error("Failed to parse authorization", e);
            return Response.unauthorized().end();
        }

        return Response.unauthorized().end();
    }
}