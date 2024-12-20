package controllers.api;

import constants.Const;
import filters.ApiFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.JsonUtils;
import jakarta.inject.Inject;
import org.apache.fury.util.StringUtils;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class CategoriesControllerV1 {
    private final DataService dataService;

    @Inject
    public CategoriesControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService can not be null");
    }

    public Response list(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return dataService.findCategories(userUid)
                .map(categories -> Response.ok().bodyJson(Map.of("categories", categories)))
                .orElseGet(Response::badRequest);
    }

    public Response add(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);
        String json = request.getBody();
        if (StringUtils.isNotBlank(json)) {
            Map<String, String> data = JsonUtils.toFlatMap(json);
            dataService.addCategory(userUid, data.get("name"));

            return Response.ok();
        }

        return Response.badRequest();
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        dataService.deleteCategory(userUid, uid);
        return Response.ok();
    }

}
