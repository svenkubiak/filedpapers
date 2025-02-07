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
import org.apache.commons.lang3.StringUtils;
import services.DataService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class ItemsControllerV1 {
    private final DataService dataService;

    @Inject
    public ItemsControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response add(Request request, boolean async, HashMap<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String url = data.get("url");
        String category = data.get("category");

        if (StringUtils.isNoneBlank(url, category)) {
            if (async) {
                Thread.ofVirtual().start(() -> dataService.addItem(userUid, url, category));
                return Response.ok();
            } else {
                if (dataService.addItem(userUid, url, category)) {
                    return Response.ok();
                }
            }
        }

        return Response.badRequest();
    }

    public Response list(Request request, String categoryUid) {
        String userUid = request.getAttribute(Const.USER_UID);
        String ifNoneMatch = request.getHeader("If-None-Match");

        if (StringUtils.isNotBlank(categoryUid)) {
            return dataService.findItems(userUid, categoryUid)
                    .map(items -> {
                        String json = JsonUtils.toJson(Map.of("items", items));
                        String hash = CodecUtils.hexSHA512(json);

                        if (hash.equals(ifNoneMatch)) {
                            return Response.notModified();
                        } else {
                            return Response.ok().header("ETag", hash).bodyJson(json);
                        }
                    }).orElseGet(Response::badRequest);
        }

        return Response.badRequest();
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (StringUtils.isNotBlank(uid) && dataService.deleteItem(uid, userUid)) {
            return Response.ok();
        }

        return Response.badRequest();
    }

    public Response trash(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (dataService.emptyTrash(userUid)) {
            return Response.ok();
        }

        return Response.badRequest();
    }

    public Response move(Request request, HashMap<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String categoryUid = data.get("category");
        String uid = data.get("uid");

        if (StringUtils.isNoneBlank(categoryUid, uid) && dataService.moveItem(uid, userUid, categoryUid)) {
            return Response.ok();
        }

        return Response.badRequest();
    }
}
