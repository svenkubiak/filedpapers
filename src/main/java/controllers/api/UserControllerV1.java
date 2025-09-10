package controllers.api;

import com.nimbusds.jwt.JWTClaimsSet;
import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import services.AuthenticationService;
import services.DataService;

import java.text.ParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class UserControllerV1 {
    private final DataService dataService;
    private final AuthenticationService authenticationService;

    @Inject
    public UserControllerV1(DataService dataService, AuthenticationService authenticationService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authenticationService = Objects.requireNonNull(authenticationService, Required.AUTHENTICATION_SERVICE);
    }

    public Response login(Map<String, String> credentials, Authentication authentication) {
        if (credentials == null || credentials.isEmpty()) {
            return Response.unauthorized();
        }

        String username = Optional.ofNullable(credentials.get("username")).orElse(Strings.EMPTY);
        String password = Optional.ofNullable(credentials.get("password")).orElse(Strings.EMPTY);
        if (StringUtils.isAnyBlank(username, password)) {
            return Response.unauthorized();
        }

        return dataService.authenticateUser(username, password, authentication)
                .map(userUid -> {
                    try {
                        if (dataService.userHasMfa(userUid)) {
                            return Response.accepted()
                                    .bodyJson(authenticationService.getChallengeToken(userUid));
                        } else {
                            return Response.ok()
                                    .bodyJson(authenticationService.getRefreshAndAccessToken(userUid));
                        }
                    } catch (MangooJwtException e) {
                        return Response.unauthorized();
                    }
                }).orElseGet(Response::unauthorized);
    }

    public Response mfa(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return Response.unauthorized();
        }

        String challengeToken = Optional.ofNullable(credentials.get(Const.CHALLENGE_TOKEN)).orElse(Strings.EMPTY);
        String otp = Optional.ofNullable(credentials.get(Const.OTP)).orElse(Strings.EMPTY);

        if (StringUtils.isAnyBlank(challengeToken, otp) || !NumberUtils.isCreatable(otp)) {
            return Response.forbidden();
        }

        try {
            JWTClaimsSet jwtClaimsSet = authenticationService.parseChallengeToken(challengeToken);
            if (jwtClaimsSet == null || authenticationService.isBlacklisted(jwtClaimsSet.getJWTID())) {
                return Response.forbidden();
            }

            String userUid = jwtClaimsSet.getSubject();
            if (dataService.isValidMfa(userUid, otp)) {
                authenticationService.blacklist(jwtClaimsSet.getJWTID());
                return Response.ok().bodyJson(authenticationService.getRefreshAndAccessToken(userUid));
            }
            return Response.forbidden();

        } catch (MangooJwtException e) {
            return Response.forbidden();
        }
    }

    public Response refresh(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return Response.unauthorized();
        }

        String refreshToken = Optional.ofNullable(credentials.get(Const.REFRESH_TOKEN)).orElse(Strings.EMPTY);
        if (StringUtils.isBlank(refreshToken)) {
            return Response.unauthorized();
        }

        try {
            String userUid = authenticationService.getSubject(refreshToken);
            if (StringUtils.isBlank(userUid)) {
                return Response.unauthorized();
            }

            JWTClaimsSet jwtClaimsSet = authenticationService.parseRefreshToken(refreshToken, userUid);
            if (jwtClaimsSet == null) {
                return Response.unauthorized();
            }

            userUid = jwtClaimsSet.getSubject();
            authenticationService.blacklist(jwtClaimsSet.getClaimAsString(Const.ATID));
            if (authenticationService.refreshTokenKeyRotate(userUid)) {
                return Response.ok()
                        .bodyJson(authenticationService.getRefreshAndAccessToken(userUid));
            } else {
                return Response.unauthorized();
            }
        } catch (MangooJwtException | ParseException e) {
            return Response.unauthorized();
        }
    }

    public Response logout(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return Response.unauthorized();
        }

        String accessToken = Optional.ofNullable(credentials.get(Const.ACCESS_TOKEN)).orElse(Strings.EMPTY);
        if (StringUtils.isBlank(accessToken)) {
            return Response.unauthorized();
        }

        try {
            JWTClaimsSet jwtClaimsSet = authenticationService.parseAccessToken(accessToken);
            if (jwtClaimsSet == null || authenticationService.isBlacklisted(jwtClaimsSet.getJWTID())) {
                return Response.unauthorized();
            }

            String userUid = jwtClaimsSet.getSubject();
            authenticationService.blacklist(jwtClaimsSet.getJWTID());
            if (authenticationService.refreshTokenKeyRotate(userUid)) {
                return Response.ok();
            } else {
                return Response.unauthorized();
            }
        } catch (MangooJwtException e) {
            return Response.unauthorized();
        }
    }
}
