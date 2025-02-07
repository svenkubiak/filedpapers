package controllers.api;

import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.routing.Response;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import services.DataService;
import utils.Utils;

import java.util.Map;
import java.util.Objects;

public class UserControllerV1 {
    private final DataService dataService;
    private final String accessTokenSecret;
    private final String refreshTokenSecret;
    private final String challengeTokenSecret;
    private final int accessTokenExpires;
    private final int refreshTokenExpires;

    @Inject
    public UserControllerV1(DataService dataService,
                            @Named("api.challengeToken.secret") String challengeTokenSecret,
                            @Named("api.accessToken.secret") String accessTokenSecret,
                            @Named("api.refreshToken.secret") String refreshTokenSecret,
                            @Named("api.accessToken.expires") int accessTokenExpires,
                            @Named("api.refreshToken.expires") int refreshTokenExpires) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.challengeTokenSecret = Objects.requireNonNull(challengeTokenSecret, Required.CHALLENGE_TOKEN_SECRET);
        this.accessTokenSecret = Objects.requireNonNull(accessTokenSecret, Required.ACCESS_TOKEN_SECRET);
        this.refreshTokenSecret = Objects.requireNonNull(refreshTokenSecret, Required.REFRESH_TOKEN_SECRET);
        this.accessTokenExpires = accessTokenExpires;
        this.refreshTokenExpires = refreshTokenExpires;
    }

    public Response login(Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (StringUtils.isAnyBlank(username, password)) {
            return Response.unauthorized();
        }

        return dataService.authenticateUser(username, password)
                .map(userUid -> {
                    try {
                        if (dataService.userHasMfa(userUid)) {
                            return Response.accepted()
                                    .bodyJson(Utils.getChallengeToken(userUid, challengeTokenSecret));
                        } else {
                            return Response.ok()
                                    .bodyJson(Utils.getAccessTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires));
                        }
                    } catch (MangooTokenException e) {
                        return Response.unauthorized();
                    }
                }).orElseGet(Response::unauthorized);
    }

    public Response mfa(Map<String, String> credentials) {
        String challengeToken = credentials.get(Const.CHALLENGE_TOKEN);
        String otp = credentials.get(Const.OTP);

        if (StringUtils.isAnyBlank(challengeToken, otp) || !NumberUtils.isCreatable(otp)) {
            return Response.forbidden();
        }

        try {
            var token = Utils.parsePaseto(challengeToken, challengeTokenSecret);
            if (token == null) {
                return Response.forbidden();
            }

            return Utils.validateToken(token)
                    .filter(userUid -> dataService.isValidMfa(userUid, otp))
                    .map(userUid -> {
                        try {
                            return Response.ok().bodyJson(
                                    Utils.getAccessTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires));
                        } catch (MangooTokenException e) {
                            return Response.forbidden();
                        }
                    }).orElseGet(Response::forbidden);

        } catch (MangooTokenException e) {
            return Response.forbidden();
        }
    }

    public Response refresh(Map<String, String> credentials) {
        String refreshToken = credentials.get(Const.REFRESH_TOKEN);

        if (StringUtils.isBlank(refreshToken)) {
            return Response.unauthorized();
        }

        try {
            var token = Utils.parsePaseto(refreshToken, refreshTokenSecret);
            if (token == null) {
                return Response.unauthorized();
            }

            return Utils.validateToken(token)
                    .map(userUid -> {
                        try {
                            return Response.ok().bodyJson(
                                    Utils.getAccessTokens(userUid, accessTokenSecret, refreshTokenSecret, accessTokenExpires, refreshTokenExpires));
                        } catch (MangooTokenException e) {
                            return Response.unauthorized();
                        }
                    }).orElseGet(Response::unauthorized);

        } catch (MangooTokenException e) {
            return Response.unauthorized();
        }
    }
}
