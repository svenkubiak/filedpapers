package controllers;

import constants.Required;
import io.mangoo.routing.Response;
import io.undertow.util.Headers;
import jakarta.inject.Inject;
import services.MediaService;

import java.util.Objects;

public class MediaController {
    private final MediaService mediaService;

    @Inject
    public MediaController(MediaService mediaService) {
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
    }

    public Response media(String uid) {
        return mediaService.retrieve(uid)
                .map(data -> Response.ok()
                        .header(Headers.X_CONTENT_TYPE_OPTIONS_STRING, "nosniff")
                        .header(Headers.CACHE_CONTROL_STRING, "Cache-Control: public, max-age=31536000, immutable").
                        bodyBinary(data))
                .orElse(Response.notFound());
    }
}
