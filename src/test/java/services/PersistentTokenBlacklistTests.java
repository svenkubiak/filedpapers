package services;

import io.mangoo.core.Application;
import io.mangoo.interfaces.TokenBlacklist;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import models.Token;
import models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.time.Duration;
import java.time.Instant;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({TestRunner.class})
public class PersistentTokenBlacklistTests {
    private Datastore datastore;
    private TokenBlacklist blacklist;

    @BeforeEach
    void init() {
        datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(Token.class);
        blacklist = Application.getInstance(TokenBlacklist.class);
    }

    private User user(long sessionsValidFrom) {
        User user = new User(Utils.randomString() + "@example.com");
        user.setSessionsValidFrom(sessionsValidFrom);
        datastore.save(user);
        return user;
    }

    @Test
    void testTheApplicationUsesThePersistentBlacklist() {
        assertThat(blacklist).isInstanceOf(PersistentTokenBlacklist.class);
    }

    @Test
    void testARevocationSurvivesANewInstance() {
        String jwtId = Utils.randomString();
        blacklist.revoke(jwtId, Instant.now().plus(Duration.ofHours(1)));

        TokenBlacklist afterRestart = new PersistentTokenBlacklist(datastore);

        assertThat(afterRestart.isRevoked(jwtId, null, null)).isTrue();
        assertThat(afterRestart.isRevoked(Utils.randomString(), null, null)).isFalse();
    }

    @Test
    void testAnExpiredTokenIsNotStored() {
        String jwtId = Utils.randomString();
        blacklist.revoke(jwtId, Instant.now().minusSeconds(1));

        assertThat(datastore.query(Token.class).find(eq("uid", jwtId)).first()).isNull();
    }

    @Test
    void testRevokingTwiceKeepsTheLaterExpiry() {
        String jwtId = Utils.randomString();
        Instant later = Instant.now().plus(Duration.ofDays(2));
        blacklist.revoke(jwtId, later);
        blacklist.revoke(jwtId, Instant.now().plus(Duration.ofHours(1)));

        Token token = datastore.query(Token.class).find(eq("uid", jwtId)).first();

        assertThat(token).isNotNull();
        assertThat(token.getExpiresAt().toInstant().getEpochSecond()).isEqualTo(later.getEpochSecond());
        assertThat(datastore.query(Token.class).countDocuments(eq("uid", jwtId))).isEqualTo(1);
    }

    @Test
    void testRevokingASubjectRejectsOnlyEarlierTokens() {
        User user = user(0L);
        Instant now = Instant.now();

        blacklist.revokeSubject(user.getUid(), now);

        assertThat(blacklist.isRevoked(null, user.getUid(), now.minusSeconds(60))).isTrue();
        assertThat(blacklist.isRevoked(null, user.getUid(), now.plusSeconds(1))).isFalse();
    }

    @Test
    void testRevokingASubjectNeverMovesTheCutoffBack() {
        long cutoff = Instant.now().getEpochSecond();
        User user = user(cutoff);

        blacklist.revokeSubject(user.getUid(), Instant.ofEpochSecond(cutoff - 3600));

        assertThat(blacklist.isRevoked(null, user.getUid(), Instant.ofEpochSecond(cutoff - 60))).isTrue();
    }

    @Test
    void testUnknownUsersAndTokensWithoutIssueTimeCountAsRevoked() {
        User user = user(0L);

        assertThat(blacklist.isRevoked(null, Utils.randomString(), Instant.now())).isTrue();
        assertThat(blacklist.isRevoked(null, user.getUid(), null)).isTrue();
        assertThat(blacklist.isRevoked(null, user.getUid(), Instant.now())).isFalse();
    }
}
