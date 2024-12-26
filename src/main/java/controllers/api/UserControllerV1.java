package controllers.api;

import constants.Const;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.JsonUtils;
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
        this.dataService = Objects.requireNonNull(dataService, "dataService can not be null");
        this.accessTokenSecret = Objects.requireNonNull(accessTokenSecret, "accessTokenSecret can not be null");
        this.refreshTokenSecret = Objects.requireNonNull(refreshTokenSecret, "refreshTokenSecret can not be null");
        this.accessTokenExpires = accessTokenExpires;
        this.refreshTokenExpires = refreshTokenExpires;
    }

    public Response login(Request request) {
        String json = request.getBody();
        if (StringUtils.isNotBlank(json)) {
            Map<String, String> credentials = JsonUtils.toFlatMap(json);

            String username = credentials.get("username");
            String password = credentials.get("password");

            return dataService.authenticateUser(username, password)
                    .map(userUid -> {
                        try {
                            Map<String, String> tokens = Utils.getTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires);
                            return Response.ok().bodyJson(tokens);
                        } catch (MangooTokenException e) {
                            return Response.unauthorized();
                        }
                    })
                    .orElse(Response.unauthorized());
        }

        return Response.unauthorized();
    }

    public Response refresh(Request request) {
        String json = request.getBody();
        if (StringUtils.isNotBlank(json)) {
            Map<String, String> credentials = JsonUtils.toFlatMap(json);
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
        }

        return Response.unauthorized();
    }
}
