package controllers;

import io.mangoo.routing.Response;

public class ApplicationController {

    public Response health() {
        return Response.ok().bodyText("OK");
    }

    public Response error() {
        return Response.ok().render();
    }

    public Response success() {
        return Response.ok().render();
    }

    public Response index() {
        return Response.redirect("/dashboard");
    }
}