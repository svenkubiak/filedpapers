package controllers.api;

import constants.Const;
import constants.Required;
import filters.TokenFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import services.DataService;

import java.util.Map;
import java.util.Objects;

@FilterWith(TokenFilter.class)
public class CategoriesControllerV1 {
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
                    .orElse(Response.internalServerError().bodyJson(Const.API_ERROR));
        } catch (IllegalArgumentException e) {
            return Response.badRequest().bodyJsonError("Invalid user");
        }
    }

    public Response poll(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty() && StringUtils.isNotBlank(data.get(Const.COUNT))) {
            var count = Long.parseLong(data.get(Const.COUNT));
            long items = 0;

            String categoryUid = data.get("category");
            if (dataService.findCategory(categoryUid, userUid) != null) {
                try {
                    items = dataService.countItems(userUid, categoryUid);
                } catch (IllegalArgumentException e) {
                    return Response.badRequest().bodyJsonError("Invalid user");
                }

                if (items >= 0 && items != count) {
                    return Response.ok();
                } else if (items < 0) {
                    return Response.internalServerError().bodyJson(Const.API_ERROR);
                }
            } else {
                return Response.badRequest().bodyJsonError("Invalid category");
            }
        }

        return Response.notModified();
    }

    public Response add(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty() && StringUtils.isNotBlank(data.get("name"))) {
            try {
                return dataService.addCategory(userUid, data.get("name"))
                        .map(success -> Response.ok())
                        .orElse(Response.internalServerError().bodyJsonError(Const.API_ERROR));
            } catch (IllegalArgumentException e) {
                return Response.badRequest().bodyJsonError("Invalid user or category");
            }
        }

        return Response.badRequest().bodyJsonError("Missing data");
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
           return dataService.deleteCategory(userUid, uid)
                    .map(success -> success ? Response.ok() : Response.internalServerError().bodyJsonError(Const.API_ERROR))
                    .orElse(Response.badRequest().bodyJsonError("Invalid user or categories"));
        } catch (IllegalArgumentException e) {
            return Response.badRequest().bodyJsonError("Invalid user or categories");
        }
    }
}
