package controllers;

import io.mangoo.routing.Response;

public class AuthenticationController {

    public Response login() {
        return Response.ok().render();
    }

    public Response authenticate() {
        return Response.ok();
    }
}
