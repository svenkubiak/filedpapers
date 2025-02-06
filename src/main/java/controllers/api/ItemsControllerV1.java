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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class ItemsControllerV1 {
    private static final Logger LOG = LogManager.getLogger(ItemsControllerV1.class);
    private final DataService dataService;

    @Inject
    public ItemsControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response add(Request request, boolean async, HashMap<String, String> data) {
        if (async) {
            Thread.ofVirtual().start(() -> addItem(request, data));
        } else {
            try {
                addItem(request, data);
            } catch (Exception e) {
                LOG.error("Failed to add item", e);

                return Response.badRequest();
            }
        }

        return Response.ok();
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
                            return Response.ok().header("ETag", hash).bodyJson(json);
                        }
                    })
                    .orElseGet(Response::badRequest);
        } catch (NullPointerException e) {
            LOG.error("Failed to find items", e);
            return Response.badRequest();
        }
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            dataService.deleteItem(uid, userUid);
            return Response.ok();
        } catch (NullPointerException e) {
            LOG.error("Failed to delete item", e);
            return Response.badRequest();
        }
    }

    public Response trash(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            dataService.emptyTrash(userUid);
            return Response.ok();
        } catch (NullPointerException e) {
            LOG.error("Failed to empty trash", e);
            return Response.badRequest();
        }
    }

    public Response move(Request request, HashMap<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            String categoryUid = data.get("category");
            String uid = data.get("uid");

            dataService.moveItem(uid, userUid, categoryUid);
            return Response.ok();
        } catch (NullPointerException e) {
            LOG.error("Failed to move item", e);
            return Response.badRequest();
        }
    }

    private void addItem(Request request, HashMap<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String url = data.get("url");
        String category = data.get("category");

        dataService.addItem(userUid, url, category);
    }
}
