package utils;

import io.mangoo.routing.Response;

public class ResultHandler {

    @FunctionalInterface
    public interface ResultSupplier {
        Result.Of get() throws IllegalArgumentException;
    }

    public static Response handle(ResultSupplier action) {
        try {
            return switch (action.get()) {
                case Result.Success success ->
                        Response.ok();
                case Result.Failure(Result.UserError error) ->
                        Response.badRequest().bodyJsonError(error.message());
                case Result.Failure(Result.ServerError error) ->
                        Response.internalServerError().bodyJsonError(error.message());
            };
        } catch (IllegalArgumentException e) {
            return Response.badRequest()
                    .bodyJsonError(e.getMessage());
        }
    }
}