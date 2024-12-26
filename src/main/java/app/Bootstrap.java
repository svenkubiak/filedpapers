package app;

import constants.Const;
import controllers.ApplicationController;
import controllers.AuthenticationController;
import controllers.DashboardController;
import controllers.api.CategoriesControllerV1;
import controllers.api.ItemsControllerV1;
import controllers.api.UserControllerV1;
import io.mangoo.interfaces.MangooBootstrap;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.routing.Bind;
import io.mangoo.routing.On;
import io.mangoo.utils.CodecUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.Category;
import models.Item;
import models.User;

import java.util.Objects;

@Singleton
public class Bootstrap implements MangooBootstrap {

    @Override
    public void initializeRoutes() {
        Bind.controller(ApplicationController.class).withRoutes(
                On.get().to("/").respondeWith("index"),
                On.get().to("/health").respondeWith("health")
        );

        Bind.controller(DashboardController.class).withAuthentication().withRoutes(
                On.get().to("/dashboard").respondeWith("dashboard"),
                On.get().to("/dashboard/{categoryUid}").respondeWith("dashboard"),
                On.get().to("/dashboard/profile").respondeWith("profile"),
                On.post().to("/dashboard/profile/change-username").respondeWith("changeUsername"),
                On.post().to("/dashboard/profile/change-password").respondeWith("changePassword"),
                On.get().to("/dashboard/io").respondeWith("io"),
                On.get().to("/dashboard/about").respondeWith("about"),
                On.post().to("/dashboard/importer").respondeWith("importer"),
                On.get().to("/dashboard/exporter").respondeWith("exporter")
        );

        Bind.controller(AuthenticationController.class).withRoutes(
                On.get().to("/auth/login").respondeWith("login"),
                On.post().to("/auth/login").respondeWith("doLogin"),
                On.get().to("/auth/logout").respondeWith("logout"),
                On.get().to("/auth/signup").respondeWith("signup"),
                On.post().to("/auth/signup").respondeWith("doSignup"),
                On.get().to("/auth/forgot").respondeWith("forgot"),
                On.post().to("/auth/forgot").respondeWith("doForgot")
        );

        Bind.controller(ItemsControllerV1.class).withRoutes(
                On.post().to("/api/v1/items").respondeWith("add"),
                On.put().to("/api/v1/items").respondeWith("move"),
                On.get().to("/api/v1/items/{categoryUid}").respondeWith("list"),
                On.put().to("/api/v1/items/{uid}").respondeWith("delete"),
                On.delete().to("/api/v1/items/trash").respondeWith("trash")
        );

        Bind.controller(CategoriesControllerV1.class).withRoutes(
                On.get().to("/api/v1/categories").respondeWith("list"),
                On.post().to("/api/v1/categories").respondeWith("add"),
                On.delete().to("/api/v1/categories/{uid}").respondeWith("delete")
        );

        Bind.controller(UserControllerV1.class).withRoutes(
                On.post().to("/api/v1/users/login").respondeWith("login"),
                On.post().to("/api/v1/users/refresh").respondeWith("refresh")
        );
        
        Bind.pathResource().to("/assets/");
        Bind.fileResource().to("/robots.txt");
    }
    
    @Override
    public void applicationInitialized() {
        // TODO Auto-generated method stub
    }

    @Override
    public void applicationStarted() {
        // TODO Auto-generated method stub
    }

    @Override
    public void applicationStopped() {
        // TODO Auto-generated method stub
    }
}