package controllers;

import constants.Required;
import io.mangoo.constants.Header;
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
    /**
     * An archive is a snapshot of an untrusted third party page that is served
     * from the origin of this application. The sandbox directive puts the
     * document into an opaque origin and withholds script execution, so nothing
     * inside it can reach the session, the cookies or the api. Route headers are
     * applied after the global ones and replace them, so this overrides the
     * server wide policy for this response only.
     */
    private static final String ARCHIVE_CSP =
            "sandbox; default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:";
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
        // The route is not bound with withAuthentication(), so the authentication
        // state has to be enforced here. isValid() covers a pending second factor.
        if (authentication == null || !authentication.isValid()) {
            return Response.unauthorized();
        }

        String userUid = authentication.getSubject();
        var item = dataService.findItem(uid, userUid);
        var archive = dataService.findArchive(item).orElseThrow();
        var string = new String(archive, StandardCharsets.UTF_8);

        return Response.ok()
                .header(Header.CONTENT_SECURITY_POLICY.toString(), ARCHIVE_CSP)
                .render("archive", new String(CommonUtils.decodeFromBase64(string), StandardCharsets.UTF_8));
    }
}
