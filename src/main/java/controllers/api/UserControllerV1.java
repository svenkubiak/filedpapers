package controllers.api;

import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.routing.Response;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import services.AuthenticationService;
import services.DataService;

import java.util.Map;
import java.util.Objects;

public class UserControllerV1 {
    private final DataService dataService;
    private final AuthenticationService authenticationService;

    @Inject
    public UserControllerV1(DataService dataService, AuthenticationService authenticationService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authenticationService = Objects.requireNonNull(authenticationService, Required.AUTHENTICATION_SERVICE);
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
                                    .bodyJson(authenticationService.getChallengeToken(userUid));
                        } else {
                            return Response.ok()
                                    .bodyJson(authenticationService.getAccessTokens(userUid));
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
            var token = authenticationService.parseChallengeToken(challengeToken);
            if (token == null) {
                return Response.forbidden();
            }

            return authenticationService.validateToken(token)
                    .filter(userUid -> dataService.isValidMfa(userUid, otp))
                    .map(userUid -> {
                        try {
                            return Response.ok().bodyJson(authenticationService.getAccessTokens(userUid));
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
            var token = authenticationService.parseRefreshToken(refreshToken);
            if (token == null) {
                return Response.unauthorized();
            }

            return authenticationService.validateToken(token)
                    .map(userUid -> {
                        try {
                            return Response.ok().bodyJson(
                                    authenticationService.getAccessTokens(userUid));
                        } catch (MangooTokenException e) {
                            return Response.unauthorized();
                        }
                    }).orElseGet(Response::unauthorized);

        } catch (MangooTokenException e) {
            return Response.unauthorized();
        }
    }
}
