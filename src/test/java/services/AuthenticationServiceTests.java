package services;

import com.nimbusds.jwt.JWTClaimsSet;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({TestRunner.class})
public class AuthenticationServiceTests {

    private AuthenticationService authenticationService() {
        return Application.getInstance(AuthenticationService.class);
    }

    private JWTClaimsSet challenge() {
        User user = new User(Utils.randomString() + "@example.com");
        user.setSessionsValidFrom(0L);
        Application.getInstance(Datastore.class).save(user);

        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .jwtID(Utils.randomString())
                .subject(user.getUid())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
    }

    @Test
    void testAChallengeInUseIsRefusedToAParallelAttempt() {
        JWTClaimsSet challenge = challenge();

        assertThat(authenticationService().beginChallenge(challenge)).isTrue();
        assertThat(authenticationService().beginChallenge(challenge)).isFalse();

        authenticationService().endChallenge(challenge, false);
    }

    @Test
    void testAFailedAttemptReleasesTheChallenge() {
        JWTClaimsSet challenge = challenge();

        assertThat(authenticationService().beginChallenge(challenge)).isTrue();
        authenticationService().endChallenge(challenge, false);

        assertThat(authenticationService().beginChallenge(challenge)).isTrue();
        authenticationService().endChallenge(challenge, false);
    }

    @Test
    void testARedeemedChallengeCannotBeUsedAgain() {
        JWTClaimsSet challenge = challenge();

        assertThat(authenticationService().beginChallenge(challenge)).isTrue();
        authenticationService().endChallenge(challenge, true);

        assertThat(authenticationService().isRevoked(challenge)).isTrue();
        assertThat(authenticationService().beginChallenge(challenge)).isFalse();
    }
}
