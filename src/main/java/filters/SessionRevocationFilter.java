package filters;

import constants.Required;
import io.mangoo.constants.Key;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.AuthenticationService;
import services.DataService;
import utils.Utils;

import java.util.Objects;

// mangoo only consults the token blacklist with authentication.blacklist enabled; this enforces sessionsValidFrom regardless.
public class SessionRevocationFilter implements PerRequestFilter {
    private static final Logger LOG = LogManager.getLogger(SessionRevocationFilter.class);
    private final DataService dataService;
    private final AuthenticationService authenticationService;
    private final String cookieName;
    private final String loginRedirect;

    @Inject
    public SessionRevocationFilter(DataService dataService,
                                   AuthenticationService authenticationService,
                                   @Named(Key.AUTHENTICATION_COOKIE_NAME) String cookieName,
                                   @Named("authentication.redirect.login") String loginRedirect) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authenticationService = Objects.requireNonNull(authenticationService, Required.AUTHENTICATION_SERVICE);
        this.cookieName = Objects.requireNonNull(cookieName, Required.COOKIE_NAME);
        this.loginRedirect = Objects.requireNonNull(loginRedirect, Required.LOGIN_REDIRECT);
    }

    @Override
    public Response execute(Request request, Response response) {
        Objects.requireNonNull(request, Required.REQUEST);
        Objects.requireNonNull(response, Required.RESPONSE);

        var cookie = request.getCookie(cookieName);
        if (cookie == null) {
            return Response.redirect(loginRedirect).end();
        }

        try {
            var jwtClaimsSet = authenticationService.parseAuthenticationCookie(cookie.getValue());
            if (jwtClaimsSet == null) {
                return Response.redirect(loginRedirect).end();
            }

            String userUid = jwtClaimsSet.getSubject();
            if (!Utils.isValidRandom(userUid)) {
                return Response.redirect(loginRedirect).end();
            }

            if (AuthenticationService.isSessionValid(dataService.findUserByUid(userUid), jwtClaimsSet)) {
                return response;
            }
        } catch (MangooJwtException e) {
            LOG.error("Failed to parse authentication cookie", e);
        }

        return Response.redirect(loginRedirect).end();
    }
}
