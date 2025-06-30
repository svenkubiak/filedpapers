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
import utils.ResultHandler;
import utils.Utils;

import java.util.Map;
import java.util.Objects;

@FilterWith(TokenFilter.class)
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

    public Response poll(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty() && StringUtils.isNotBlank(data.get(Const.COUNT))) {
            var count = Long.parseLong(data.get(Const.COUNT));
            long items;

            String categoryUid = data.get("category");
            if (StringUtils.isBlank(categoryUid) || !Utils.isValidRandom(categoryUid)) {
                categoryUid = dataService.findInbox(userUid).getUid();
            }

            if (dataService.findCategory(categoryUid, userUid) != null) {
                try {
                    items = dataService.countItems(userUid, categoryUid);
                } catch (IllegalArgumentException e) {
                    return Response.badRequest().bodyJsonError(e.getMessage());
                }

                if (items >= 0 && items != count) {
                    return Response.ok();
                } else if (items < 0) {
                    return Response.internalServerError().bodyJson(Const.GENERAL_ERROR);
                }
            } else {
                return Response.badRequest().bodyJsonError("Invalid category");
            }
        }

        return Response.notModified();
    }

    public Response add(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty()) {
            return ResultHandler.handle(() -> dataService.addCategory(userUid, data.get("name")));
        }

        return Response.badRequest().bodyJsonError(MISSING_DATA);
    }

    public Response edit(Request request, Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);

        if (!data.isEmpty()) {
            return ResultHandler.handle(() -> dataService.updateCategory(userUid, data.get("uid"), data.get("name")));
        }

        return Response.badRequest().bodyJsonError(MISSING_DATA);
    }

    public Response delete(Request request, String uid) {
        String userUid = request.getAttribute(Const.USER_UID);
        return ResultHandler.handle(() -> dataService.deleteCategory(userUid, uid));
    }
}
