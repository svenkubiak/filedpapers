package controllers;

import constants.Required;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.utils.CommonUtils;
import io.undertow.util.Headers;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import services.DataService;
import services.MediaService;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MediaController {
    private final MediaService mediaService;
    private final DataService dataService;

    @Inject
    public MediaController(MediaService mediaService, DataService dataService) {
        this.mediaService = Objects.requireNonNull(mediaService, Required.MEDIA_SERVICE);
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    public Response image(String uid) {
        return mediaService.retrieve(uid)
                .map(data -> Response.ok()
                        .header(Headers.CACHE_CONTROL_STRING, "Cache-Control: public, max-age=31536000, immutable")
                        .bodyBinary(data))
                .orElse(Response.notFound());
    }

    public Response archive(Authentication authentication, @NotEmpty String uid) {
        String userUid = authentication.getSubject();
        var item = dataService.findItem(uid, userUid);
        var archive = dataService.findArchive(item).orElseThrow();
        var string = new String(archive, StandardCharsets.UTF_8);

        return Response.ok().render("archive", new String(CommonUtils.decodeFromBase64(string), StandardCharsets.UTF_8));
    }
}
