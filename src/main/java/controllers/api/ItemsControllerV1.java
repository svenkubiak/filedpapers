package controllers.api;

import constants.Const;
import filters.ApiFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.JsonUtils;
import jakarta.inject.Inject;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class ItemsControllerV1 {
    private final DataService dataService;

    @Inject
    public ItemsControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService can not be null");
    }

    public Response add(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);
        String json = request.getBody();

        Map<String, String> data = JsonUtils.toFlatMap(json);
        String url = data.get("url");
        dataService.addItem(userUid, url);

        return Response.ok();
    }

    public Response list(Request request, String categoryUid) {
        String userUid = request.getAttribute(Const.USER_UID);

        return dataService.findItems(userUid, categoryUid)
                .map(items -> Response.ok().bodyJson(Map.of("items", items)))
                .orElse(Response.badRequest());
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);
        dataService.deleteItem(uid, userUid);

        return Response.ok();
    }

    public Response trash(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        dataService.emptyTrash(userUid);
        return Response.ok();
    }

    public Response move(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);
        String json = request.getBody();

        Map<String, String> data = JsonUtils.toFlatMap(json);

        String categoryUid = data.get("category");
        String uid = data.get("uid");

        dataService.moveItem(uid, userUid, categoryUid);

        return Response.ok();
    }
}
