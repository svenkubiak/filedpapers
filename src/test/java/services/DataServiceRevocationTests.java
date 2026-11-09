package services;

import io.mangoo.core.Application;
import io.mangoo.interfaces.TokenBlacklist;
import io.mangoo.persistence.MongoTokenBlacklist;
import io.mangoo.interfaces.persistence.Datastore;
import io.mangoo.test.TestRunner;
import models.User;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.mongodb.client.model.Filters.eq;
import static constants.Const.UID;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({TestRunner.class})
public class DataServiceRevocationTests {
    private Datastore datastore;
    private DataService dataService;
    private TokenBlacklist blacklist;

    @BeforeEach
    void init() {
        datastore = Application.getInstance(Datastore.class);
        dataService = Application.getInstance(DataService.class);
        blacklist = Application.getInstance(TokenBlacklist.class);
    }

    private User user(long sessionsValidFrom) {
        User user = new User(Utils.randomString() + "@example.com");
        user.setSessionsValidFrom(sessionsValidFrom);
        datastore.save(user);
        return user;
    }

    private User stored(User user) {
        return datastore.find(User.class, eq(UID, user.getUid()));
    }

    @Test
    void testTheApplicationUsesMangooPersistentBlacklist() {
        assertThat(blacklist).isInstanceOf(MongoTokenBlacklist.class);
    }

    @Test
    void testARevokedTokenStaysRevoked() {
        String jwtId = Utils.randomString();
        blacklist.revoke(jwtId, Instant.now().plus(Duration.ofHours(1)));

        assertThat(blacklist.isRevoked(jwtId, null, Instant.now())).isTrue();
        assertThat(blacklist.isRevoked(Utils.randomString(), null, Instant.now())).isFalse();
    }

    @Test
    void testRevokeSessionsMovesTheCutoffForward() {
        User user = user(0L);
        long before = Instant.now().getEpochSecond();

        assertThat(dataService.revokeSessions(user.getUid())).isTrue();

        assertThat(stored(user).getSessionsValidFrom()).isGreaterThanOrEqualTo(before);
        assertThat(blacklist.isRevoked(null, user.getUid(), Instant.ofEpochSecond(before - 60))).isTrue();
    }

    @Test
    void testRevokeSessionsDoesNotMoveTheCutoffBack() {
        long future = Instant.now().getEpochSecond() + 3600;
        User user = user(future);

        dataService.revokeSessions(user.getUid());

        assertThat(stored(user).getSessionsValidFrom()).isEqualTo(future);
    }

    @Test
    void testExistingTokenRevocationsAreCopied() {
        String jwtId = Utils.randomString();
        datastore.getMongoDatabase().getCollection("tokens").insertOne(new Document(UID, jwtId)
                .append("expiresAt", Date.from(Instant.now().plus(Duration.ofHours(2)))));

        dataService.upgrade();

        assertThat(blacklist.isRevoked(jwtId, null, Instant.now())).isTrue();
        boolean tokensLeft = false;
        for (String name : datastore.getMongoDatabase().listCollectionNames()) {
            if ("tokens".equals(name)) {
                tokensLeft = true;
            }
        }
        assertThat(tokensLeft).isFalse();
    }

    @Test
    void testSetPasswordRevokesEarlierSessions() {
        User user = user(0L);
        long before = Instant.now().getEpochSecond();

        dataService.setPassword(user.getUid(), "a-new-password");

        assertThat(stored(user).getSessionsValidFrom()).isGreaterThanOrEqualTo(before);
    }
}
