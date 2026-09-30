package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiAccessFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import services.DataService;
import utils.ResultHandler;

import java.util.Map;
import java.util.Objects;

@FilterWith(ApiAccessFilter.class)
public class CategoriesControllerV1 {
    public static final String MISSING_DATA = "Missing data";
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
                    .orElse(Response.internalServerError().bodyJson(Const.GENERAL_ERROR));
        } catch (IllegalArgumentException e) {
            return Response.badRequest().bodyJsonError(e.getMessage());
        }
    }

    public Response add(Request request, @NotNull @NotEmpty Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        return ResultHandler.handle(() -> dataService.addCategory(userUid, data.get("name")));
    }

    public Response edit(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty()) {
            return ResultHandler.handle(() -> dataService.updateCategory(userUid, data.get("uid"), data.get("name")));
        }

        return Response.badRequest().bodyJsonError(MISSING_DATA);
    }

    public Response delete(Request request, @NotEmpty String uid) {
        String userUid = request.getAttribute(Const.USER_UID);
        return ResultHandler.handle(() -> dataService.deleteCategory(userUid, uid));
    }
}
