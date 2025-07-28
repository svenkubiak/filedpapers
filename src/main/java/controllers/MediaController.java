package controllers;

import constants.Required;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.utils.CodecUtils;
import io.undertow.util.Headers;
import jakarta.inject.Inject;
import services.DataService;
import services.MediaService;

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

    public Response archive(Authentication authentication, String uid) {
        String userUid = authentication.getSubject();
        var item = dataService.findItem(uid, userUid);
        var archive = dataService.findArchive(item).orElseThrow();
        String str = new String(archive);

        return Response.ok().render("archive", new String(CodecUtils.decodeFromBase64(str)));
    }
}
