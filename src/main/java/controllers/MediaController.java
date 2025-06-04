package controllers;

import constants.Const;
import constants.Required;
import filters.TokenFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Request;
import jakarta.inject.Inject;
import services.MediaService;

import java.util.Objects;

@FilterWith(TokenFilter.class)
public class MediaController {
    private final MediaService mediaService;

    @Inject
    public MediaController(MediaService mediaService) {
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
    }

    public Response media(Request request, String uid) {
        String userUid = request.getAttributeAsString(Const.USER_UID);

        return mediaService.retrieve(uid, userUid)
                .map(data -> Response.ok().bodyBinary(data))
                .orElse(Response.notFound());
    }
}
