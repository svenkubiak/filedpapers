package controllers;

import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import jakarta.inject.Inject;
import services.DataService;

import java.util.Objects;

public class ApplicationController {

    public Response health() {
        return Response.ok().bodyText("OK");
    }

    public Response index() {
        return Response.redirect("/dashboard");
    }
}