package controllers;

import io.mangoo.routing.Response;

public class ApplicationController {

    public Response health() {
        return Response.ok().bodyText("OK");
    }

    public Response index() {
        return Response.redirect("/dashboard");
    }

    public Response dashboard() {
        return Response.ok().render();
    }

    public Response profile() {
        return Response.ok().render();
    }

    public Response category(String uid) {
        return Response.ok().render();
    }
}