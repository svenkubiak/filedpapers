package controllers.api;

import com.nimbusds.jwt.JWTClaimsSet;
import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    public Response login(@NotNull @NotEmpty Map<String, String> credentials, Authentication authentication) {
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

    public Response mfa(@NotNull @NotEmpty Map<String, String> credentials) {
        String challengeToken = Optional.ofNullable(credentials.get(Const.CHALLENGE_TOKEN)).orElse(Strings.EMPTY);
        String otp = Optional.ofNullable(credentials.get(Const.OTP)).orElse(Strings.EMPTY);

        if (StringUtils.isAnyBlank(challengeToken, otp) || !NumberUtils.isCreatable(otp)) {
            return Response.forbidden();
        }

        try {
            var jwtClaimsSet = authenticationService.parseChallengeToken(challengeToken);
            if (jwtClaimsSet == null || authenticationService.isTokenBlacklisted(jwtClaimsSet.getJWTID())) {
                return Response.forbidden();
            }

            String userUid = jwtClaimsSet.getSubject();
            if (dataService.isValidMfa(userUid, otp)) {
                authenticationService.blacklistToken(jwtClaimsSet.getJWTID());
                return Response.ok().bodyJson(authenticationService.getRefreshAndAccessToken(userUid));
            }
            return Response.forbidden();

        } catch (MangooJwtException e) {
            return Response.forbidden();
        }
    }

    public Response refresh(@NotNull @NotEmpty Map<String, String> credentials) {
        String refreshToken = Optional.ofNullable(credentials.get(Const.REFRESH_TOKEN)).orElse(Strings.EMPTY);
        if (StringUtils.isBlank(refreshToken)) {
            return Response.unauthorized();
        }

        try {
            var jwtClaimsSet = authenticationService.parseRefreshToken(refreshToken);
            if (jwtClaimsSet == null) {
                return Response.unauthorized();
            }

            if (authenticationService.isRefreshBlacklisted(jwtClaimsSet.getJWTID())) {
                return Response.unauthorized();
            }

            String userUid = jwtClaimsSet.getSubject();
            authenticationService.blacklistToken(jwtClaimsSet.getClaimAsString(Const.ATID));
            authenticationService.blacklistRefreshToken(jwtClaimsSet.getJWTID());

            return Response.ok().bodyJson(authenticationService.getRefreshAndAccessToken(userUid));
        } catch (MangooJwtException | ParseException e) {
            return Response.unauthorized();
        }
    }

    public Response logout(@NotNull @NotEmpty Map<String, String> credentials) {
        String accessToken = Optional.ofNullable(credentials.get(Const.ACCESS_TOKEN)).orElse(Strings.EMPTY);
        String refreshToken = Optional.ofNullable(credentials.get(Const.REFRESH_TOKEN)).orElse(Strings.EMPTY);
        if (StringUtils.isAnyBlank(accessToken, refreshToken)) {
            return Response.unauthorized();
        }

        try {
            JWTClaimsSet accessTokenClaims = authenticationService.parseAccessToken(accessToken);
            if (accessTokenClaims == null || authenticationService.isTokenBlacklisted(accessTokenClaims.getJWTID())) {
                return Response.unauthorized();
            }

            JWTClaimsSet refreshTokenClaims = authenticationService.parseRefreshToken(refreshToken);
            if (refreshTokenClaims == null || authenticationService.isRefreshBlacklisted(refreshTokenClaims.getJWTID())) {
                return Response.unauthorized();
            }

            authenticationService.blacklistToken(accessTokenClaims.getJWTID());
            authenticationService.blacklistRefreshToken(refreshTokenClaims.getJWTID());
            return Response.ok();
        } catch (MangooJwtException e) {
            return Response.unauthorized();
        }
    }
}
