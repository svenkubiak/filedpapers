package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.JsonUtils;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class ItemsControllerV1 {
    private static final Logger LOG = LogManager.getLogger(ItemsControllerV1.class);
    public static final String FAILED_TO_ADD_ITEM_WITH_URL = "Failed to add item with URL: {}";
    private final DataService dataService;

    @Inject
    public ItemsControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response add(Request request, boolean async, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String url = data.get("url");
        String category = data.get("category");

        if (async) {
            Thread.ofVirtual().start(() -> dataService.addItem(userUid, url, category).orElseGet(() -> {
                LOG.error(FAILED_TO_ADD_ITEM_WITH_URL, url);
                return false;
            }));
            return Response.ok();
        } else {
            try {
                return dataService.addItem(userUid, url, category)
                        .filter(success -> success)
                        .map(success -> Response.ok())
                        .orElseGet(() -> {
                            LOG.error(FAILED_TO_ADD_ITEM_WITH_URL, url);
                            return Response.internalServerError().bodyJson(Const.API_ERROR);
                        });
            } catch (IllegalArgumentException e) {
                LOG.error(FAILED_TO_ADD_ITEM_WITH_URL, url);
                return Response.badRequest().bodyJsonError("Invalid user, category or url");
            }
        }
    }

    public Response list(Request request, String categoryUid) {
        String userUid = request.getAttribute(Const.USER_UID);
        String ifNoneMatch = request.getHeader("If-None-Match");

        try {
            return dataService.findItems(userUid, categoryUid)
                    .map(items -> {
                        String json = JsonUtils.toJson(Map.of("items", items));
                        String hash = CodecUtils.hexSHA512(json);

                        if (hash.equals(ifNoneMatch)) {
                            return Response.notModified();
                        } else {
                            return Response.ok()
                                    .header("ETag", hash)
                                    .bodyJson(json);
                        }
                    })
                    .orElse(Response.badRequest().bodyJsonError("Invalid user or category"));
        } catch (IllegalArgumentException e) {
            return Response.badRequest().bodyJsonError("Invalid user or category");
        }
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        return dataService.deleteItem(uid, userUid)
                .map(success -> Response.ok())
                .orElse(Response.badRequest().bodyJsonError("Invalid user or item"));
    }

    public Response trash(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return dataService.emptyTrash(userUid)
                .map(success -> Response.ok())
                .orElse(Response.badRequest().bodyJsonError("Invalid user"));
    }

    public Response move(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String categoryUid = data.get("category");
        String uid = data.get("uid");

        return dataService.moveItem(uid, userUid, categoryUid)
                .map(success -> success ? Response.ok() : Response.internalServerError().bodyJson(Const.API_ERROR))
                .orElse(Response.badRequest().bodyJsonError("Invalid user or category"));
    }
}
