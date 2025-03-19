package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class CategoriesControllerV1 {
    private final DataService dataService;

    @Inject
    public CategoriesControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response list(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return dataService.findCategories(userUid)
                .map(categories -> Response.ok().bodyJson(Map.of("categories", categories)))
                .orElseGet(Response::badRequest);
    }

    public Response poll(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty()
                && !StringUtils.isAnyBlank(data.get("category"), data.get("count"))) {
            long count = Long.parseLong(data.get("count"));
            long items = dataService.countItems(userUid, data.get("category"));

            if (items > 0 && items != count) {
                return Response.ok();
            }
        }

        return Response.notModified();
    }

    public Response add(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty()
                && StringUtils.isNotBlank(data.get("name"))
                && dataService.addCategory(userUid, data.get("name"))) {
            return Response.ok();
        }

        return Response.badRequest();
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (StringUtils.isNotBlank(uid) && dataService.deleteCategory(userUid, uid)) {
            return Response.ok();
        }

        return Response.badRequest();
    }
}
