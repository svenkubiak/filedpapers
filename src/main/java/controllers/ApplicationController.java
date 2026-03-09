package controllers;

import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.routing.Response;
import jakarta.inject.Inject;

import java.util.Objects;

public class ApplicationController {
    private final Datastore datastore;

    @Inject
    public ApplicationController(Datastore datastore) {
        this.datastore = Objects.requireNonNull(datastore, "datastore must not be null");
    }

    public Response health() {
        String status = datastore.isHealthy() ? "ok" : "error";
        return Response.ok().bodyText(status);
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