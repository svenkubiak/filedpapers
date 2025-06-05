package services;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import constants.Const;
import constants.Required;
import de.svenkubiak.http.Http;
import io.mangoo.cache.Cache;
import io.mangoo.persistence.interfaces.Datastore;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import utils.Utils;

import java.util.Objects;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class MediaService {
    private static final Logger LOG = LogManager.getLogger(MediaService.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final String BUCKET_NAME = "filedpapers";
    private static final String FILEDPAPERS_FILES = "filedpapers.files";
    private static final String METADATA_UID = "metadata.uid";
    private static final String METADATA_USER_UID = "metadata.userUid";
    private final Cache cache;
    private final GridFSBucket bucket;

    @Inject
    public MediaService(Datastore datastore, Cache cache) {
        this.bucket = GridFSBuckets.create(datastore.getMongoDatabase(), BUCKET_NAME);
        this.cache = Objects.requireNonNull(cache, Required.CACHE);

        datastore.query(FILEDPAPERS_FILES).createIndex(Indexes.ascending(METADATA_UID), new IndexOptions().unique(true));
        datastore.query(FILEDPAPERS_FILES).createIndex(Indexes.ascending(METADATA_USER_UID));
        datastore.query(FILEDPAPERS_FILES).createIndex(Indexes.compoundIndex(
                Indexes.ascending(METADATA_UID),
                Indexes.ascending(METADATA_USER_UID)
        ), new IndexOptions().unique(true));
    }

    public String store(byte[] data, String userUid) {
        Objects.requireNonNull(data, Required.DATA);
        Objects.requireNonNull(userUid, Required.USER_UID);

        String uid = Utils.randomString();

        GridFSUploadOptions options = new GridFSUploadOptions()
                .metadata(new Document(Const.UID, uid).append(Const.USER_UID, userUid));
        try (GridFSUploadStream uploadStream = bucket.openUploadStream(uid, options)) {
            uploadStream.write(data);
            uploadStream.flush();
        } catch (Exception e) {
            uid = null;
            LOG.error("Failed to store data in GridFS", e);
        }

        return uid;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Optional<byte[]> retrieve(String uid) {
        Objects.requireNonNull(uid, Required.MEDIA_UID);

        Object object = cache.get(Const.IMAGE_CACHE_PREFIX + uid);
        if (object != null) {
            return Optional.of((byte[]) object);
        }

        byte[] data = null;
        GridFSFile gridFSFile = bucket
                .find(eq(METADATA_UID, uid))
                .first();

        if (gridFSFile != null) {
            try (GridFSDownloadStream downloadStream = bucket.openDownloadStream(gridFSFile.getObjectId())) {
                int fileLength = (int) downloadStream.getGridFSFile().getLength();
                data = new byte[fileLength];
                downloadStream.read(data);

                cache.put(Const.IMAGE_CACHE_PREFIX + uid, data);
            }
        }

        return Optional.ofNullable(data);
    }

    public void delete(String uid, String userUid) {
        Objects.requireNonNull(uid, Required.MEDIA_UID);
        Objects.requireNonNull(uid, Required.USER_UID);

        GridFSFile gridFSFile = bucket
                .find(and(eq(METADATA_UID, uid), eq(METADATA_USER_UID, userUid)))
                .first();

        if (gridFSFile != null) {
            bucket.delete(gridFSFile.getObjectId());
        }
    }

    public void delete(String uid) {
        Objects.requireNonNull(uid, Required.MEDIA_UID);

        GridFSFile gridFSFile = bucket
                .find(eq(METADATA_UID, uid))
                .first();

        if (gridFSFile != null) {
            bucket.delete(gridFSFile.getObjectId());
        }
    }

    public Optional<String> fetchAndStore(String url, String userUid) {
        Objects.requireNonNull(url, Required.URL);
        Objects.requireNonNull(userUid, Required.USER_UID);

        String uid = null;
        var result = Http.get(url)
                .withHeader("User-Agent", USER_AGENT)
                .binaryResponse()
                .send();

        if (result.isValid()) {
            uid = store(result.binaryBody(), userUid);
        } else {
            LOG.error("Failed to fetch and store image");
        }

        return Optional.ofNullable(uid);
    }

    public void clean(String mediaUid, String userUid) {
        if (StringUtils.isNotBlank(mediaUid) && StringUtils.isNotBlank(userUid)) {
            delete(mediaUid, userUid);
        }
    }
}
