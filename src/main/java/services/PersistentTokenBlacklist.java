package services;

import com.mongodb.client.model.UpdateOptions;
import constants.Const;
import constants.Required;
import io.mangoo.interfaces.TokenBlacklist;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.utils.Argument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.Token;
import models.User;
import org.apache.commons.lang3.StringUtils;
import utils.Utils;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.max;

// Revocations survive restarts: single tokens live in the tokens collection until they expire,
// revoking a subject moves the user's sessionsValidFrom.
@Singleton
public class PersistentTokenBlacklist implements TokenBlacklist {
    private final Datastore datastore;

    @Inject
    public PersistentTokenBlacklist(Datastore datastore) {
        this.datastore = Objects.requireNonNull(datastore, Required.DATASTORE);
    }

    @Override
    public void revoke(String jwtId, Instant expiresAt) {
        Argument.requireNonBlank(jwtId, Required.ID);
        Objects.requireNonNull(expiresAt, Required.EXPIRES_AT);

        if (expiresAt.isAfter(Instant.now())) {
            // Upsert instead of insert: two revocations of the same token must not collide on the unique uid.
            datastore.query(Token.class).updateOne(
                    eq(Const.UID, jwtId),
                    max(Const.EXPIRES_AT, Date.from(expiresAt)),
                    new UpdateOptions().upsert(true));
        }
    }

    @Override
    public void revokeSubject(String subject, Instant since) {
        Argument.requireNonBlank(subject, Required.SUBJECT);
        Objects.requireNonNull(since, Required.SINCE);

        datastore.query(User.class).updateOne(
                eq(Const.UID, subject),
                max(Const.SESSIONS_VALID_FROM, since.getEpochSecond()));
    }

    @Override
    public boolean isRevoked(String jwtId, String subject, Instant issuedAt) {
        if (StringUtils.isNotBlank(jwtId)
                && datastore.query(Token.class).find(eq(Const.UID, jwtId)).first() != null) {
            return true;
        }

        if (StringUtils.isNotBlank(subject)) {
            // Stricter than mangoo's default: an unknown user or a token without iat counts as revoked.
            if (!Utils.isValidRandom(subject) || issuedAt == null) {
                return true;
            }

            var user = datastore.find(User.class, eq(Const.UID, subject));
            return user == null || issuedAt.getEpochSecond() < user.getSessionsValidFrom();
        }

        return false;
    }
}
