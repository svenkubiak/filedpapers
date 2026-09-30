package controllers.api;

import constants.Const;
import constants.Required;
import filters.ApiAccessFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JsonUtils;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import services.DataService;
import utils.ResultHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@FilterWith(ApiAccessFilter.class)
public class ItemsControllerV1 {
    private static final String INVALID_BODY = "Expected a JSON body with a uids array";
    private static final int DEFAULT_SEARCH_LIMIT = 25;
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
            Thread.ofVirtual().start(() -> ResultHandler.handle(() -> dataService.addItem(userUid, url, category)));
            return Response.ok();
        } else {
            return ResultHandler.handle(() -> dataService.addItem(userUid, url, category));
        }
    }

    public Response archive(Request request, @NotEmpty String uid) {
        String userUid = request.getAttribute(Const.USER_UID);
        Thread.ofVirtual().start(() -> ResultHandler.handle(() -> dataService.archive(uid, userUid)));

        return Response.ok();
    }

    public Response list(Request request, @NotEmpty String categoryUid) {
        String userUid = request.getAttribute(Const.USER_UID);
        String ifNoneMatch = request.getHeader("If-None-Match");

        try {
            return dataService.findItems(userUid, categoryUid)
                    .map(items -> {
                        String json = JsonUtils.toJson(Map.of("items", items));
                        String hash = CommonUtils.hexSHA512(json);

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

    public Response delete(Request request, @NotEmpty String uid) {
        String userUid = request.getAttribute(Const.USER_UID);
        return ResultHandler.handle(() -> dataService.deleteItem(uid, userUid));
    }

    public Response trash(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);
        return ResultHandler.handle(() -> dataService.emptyTrash(userUid));
    }

    /**
     * Free text search across all categories, used by the command palette. Kept
     * off the /items path on purpose - a search term must never be mistaken for
     * a category uid by the router.
     */
    public Response search(Request request, String q) {
        String userUid = request.getAttribute(Const.USER_UID);

        try {
            return dataService.searchItems(userUid, q, DEFAULT_SEARCH_LIMIT)
                    .map(items -> Response.ok().bodyJson(Map.of("items", items)))
                    .orElse(Response.ok().bodyJson(Map.of("items", List.of())));
        } catch (IllegalArgumentException e) {
            return Response.badRequest().bodyJsonError(e.getMessage());
        }
    }

    public Response bulkMove(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return bulk(request).map(bulk ->
                ResultHandler.handle(() -> dataService.moveItems(bulk.uids(), userUid, bulk.category())))
                .orElseGet(() -> Response.badRequest().bodyJsonError(INVALID_BODY));
    }

    public Response bulkDelete(Request request) {
        String userUid = request.getAttribute(Const.USER_UID);

        return bulk(request).map(bulk ->
                ResultHandler.handle(() -> dataService.deleteItems(bulk.uids(), userUid)))
                .orElseGet(() -> Response.badRequest().bodyJsonError(INVALID_BODY));
    }

    private Optional<Bulk> bulk(Request request) {
        try {
            Bulk bulk = JsonUtils.toObject(request.getBody(), Bulk.class);
            return bulk == null || bulk.uids() == null ? Optional.empty() : Optional.of(bulk);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record Bulk(List<String> uids, String category) {}

    public Response move(Request request, @NotNull @NotEmpty Map<String, String> data) {
        String userUid = request.getAttribute(Const.USER_UID);
        String categoryUid = data.get("category");
        String uid = data.get("uid");

        return ResultHandler.handle(() -> dataService.moveItem(uid, userUid, categoryUid));
    }
}
