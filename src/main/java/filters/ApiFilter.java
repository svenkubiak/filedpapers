package filters;

import constants.Const;
import io.mangoo.constants.NotNull;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.interfaces.filters.PerRequestFilter;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.RequestUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import utils.Utils;

import java.util.Objects;

public class ApiFilter implements PerRequestFilter {
    private final String secret;

    @Inject
    public ApiFilter(@Named("api.accessToken.secret") String secret) {
        this.secret = Objects.requireNonNull(secret, NotNull.SECRET);
    }

    @Override
    public Response execute(Request request, Response response) {
        return RequestUtils.getAuthorizationHeader(request)
                .map(authorization -> authorize(authorization, request, response))
                .orElseGet(() -> Response.unauthorized().end());
    }

    private Response authorize(String authorization, Request request, Response response) {
        try {
            var token = Utils.parsePaseto(authorization, secret);
            if (token != null) {
                return Utils.validateToken(token).map(userUid -> {
                    request.addAttribute(Const.USER_UID, userUid);
                    return response;
                }).orElseGet(() -> Response.unauthorized().end());
            }
        } catch (MangooTokenException e) {
            return Response.unauthorized().end();
        }

        return Response.unauthorized().end();
    }
}