package controllers;

import constants.Required;
import io.mangoo.routing.Response;
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
                .map(data -> Response.ok().bodyBinary(data))
                .orElse(Response.notFound());
    }
}
