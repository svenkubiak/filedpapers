package controllers.api;

import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.fury.util.StringUtils;
import services.DataService;
import utils.Utils;

import java.util.Map;
import java.util.Objects;

public class UserControllerV1 {
    private final DataService dataService;
    private final String accessTokenSecret;
    private final String refreshTokenSecret;
    private final int accessTokenExpires;
    private final int refreshTokenExpires;

    @Inject
    public UserControllerV1(DataService dataService,
                            @Named("api.accessToken.secret") String accessTokenSecret,
                            @Named("api.refreshToken.secret") String refreshTokenSecret,
                            @Named("api.accessToken.expires") int accessTokenExpires,
                            @Named("api.refreshToken.expires") int refreshTokenExpires) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.accessTokenSecret = Objects.requireNonNull(accessTokenSecret, Required.ACCESS_TOKEN_SECRET);
        this.refreshTokenSecret = Objects.requireNonNull(refreshTokenSecret, Required.REFRESH_TOKEN_SECRET);
        this.accessTokenExpires = accessTokenExpires;
        this.refreshTokenExpires = refreshTokenExpires;
    }

    public Response login(Request request, Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (StringUtils.isNotBlank(username) || StringUtils.isNotBlank(password)) {
            return dataService.authenticateUser(username, password)
                    .map(userUid -> {
                        try {
                            Map<String, String> tokens = Utils.getTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires);
                            return Response.ok().bodyJson(tokens);
                        } catch (MangooTokenException e) {
                            return Response.unauthorized();
                        }
                    })
                    .orElseGet(Response::unauthorized);
        }

        return Response.unauthorized();
    }

    public Response refresh(Request request, Map<String, String> credentials) {
        String refreshToken = credentials.get(Const.REFRESH_TOKEN);
        if (StringUtils.isNotBlank(refreshToken)) {
            try {
                var token = Utils.parsePaseto(refreshToken, refreshTokenSecret);
                if (token != null) {
                    return Utils.validateToken(token).map(userUid -> {
                        Map<String, String> tokens = null;
                        try {
                            tokens = Utils.getTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires);
                            return Response.ok().bodyJson(tokens);
                        } catch (MangooTokenException e) {
                            return Response.unauthorized();
                        }
                    }).orElseGet(Response::unauthorized);
                }
            } catch (MangooTokenException e) {
                return Response.unauthorized();
            }
        }

        return Response.unauthorized();
    }
}
