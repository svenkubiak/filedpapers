package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiFilter.class)
public class CategoriesControllerV1 {
    private static final Logger LOG = LogManager.getLogger(CategoriesControllerV1.class);
    private final DataService dataService;

    @Inject
    public CategoriesControllerV1(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response list(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            return dataService.findCategories(userUid)
                    .map(categories -> Response.ok().bodyJson(Map.of("categories", categories)))
                    .orElseGet(Response::badRequest);
        } catch (NullPointerException e) {
            LOG.error("Error getting categories list", e);
            return Response.badRequest();
        }
    }

    public Response add(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            dataService.addCategory(userUid, data.get("name"));
            return Response.ok();
        } catch (NullPointerException e) {
            LOG.error("Error adding new category", e);
            return Response.badRequest();
        }
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            dataService.deleteCategory(userUid, uid);
            return Response.ok();
        } catch (NullPointerException e) {
            LOG.error("Error deleting category", e);
            return Response.badRequest();
        }
    }
}
