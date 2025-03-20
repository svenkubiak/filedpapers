package app;

import controllers.ApplicationController;
import controllers.AuthenticationController;
import controllers.DashboardController;
import controllers.api.CategoriesControllerV1;
import controllers.api.ItemsControllerV1;
import controllers.api.UserControllerV1;
import io.mangoo.interfaces.MangooBootstrap;
import io.mangoo.routing.Bind;
import io.mangoo.routing.On;
import jakarta.inject.Singleton;

@Singleton
public class Bootstrap implements MangooBootstrap {

    @Override
    public void initializeRoutes() {
        Bind.controller(ApplicationController.class).withRoutes(
                On.get().to("/").respondeWith("index"),
                On.get().to("/error").respondeWith("error"),
                On.get().to("/success").respondeWith("success"),
                On.get().to("/health").respondeWith("health")
        );

        Bind.controller(DashboardController.class).withAuthentication().withRoutes(
                On.get().to("/dashboard").respondeWith("dashboard"),
                On.get().to("/dashboard/{categoryUid}").respondeWith("dashboard"),
                On.get().to("/dashboard/profile").respondeWith("profile"),
                On.get().to("/dashboard/about").respondeWith("about"),
                On.post().to("/dashboard/profile/change-username").respondeWith("doChangeUsername"),
                On.post().to("/dashboard/profile/change-password").respondeWith("doChangePassword"),
                On.post().to("/dashboard/profile/delete-account").respondeWith("doDeleteAccount"),
                On.post().to("/dashboard/profile/enable-mfa").respondeWith("doMfa"),
                On.post().to("/dashboard/profile/logout-devices").respondeWith("doLogoutDevices"),
                On.post().to("/dashboard/profile/language").respondeWith("doLanguage"),
                On.get().to("/dashboard/profile/confirm-email").respondeWith("confirmEmail"),
                On.get().to("/dashboard/io").respondeWith("io"),
                On.post().to("/dashboard/io/importer").respondeWith("importer"),
                On.post().to("/dashboard/io/exporter").respondeWith("exporter")
        );

        Bind.controller(AuthenticationController.class).withRoutes(
                On.get().to("/auth/login").respondeWith("login"),
                On.post().to("/auth/login").respondeWith("doLogin"),
                On.get().to("/auth/mfa").respondeWith("mfa"),
                On.post().to("/auth/mfa").respondeWith("doMfa"),
                On.get().to("/auth/logout").respondeWith("logout"),
                On.get().to("/auth/signup").respondeWith("signup"),
                On.post().to("/auth/signup").respondeWith("doSignup"),
                On.get().to("/auth/forgot").respondeWith("forgot"),
                On.post().to("/auth/forgot").respondeWith("doForgot"),
                On.get().to("/auth/confirm/{token}").respondeWith("confirm"),
                On.get().to("/auth/reset-password/{token}").respondeWith("resetPassword"),
                On.post().to("/auth/reset-password/{token}").respondeWith("doResetPassword")
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
                On.post().to("/api/v1/categories/poll").respondeWith("poll"),
                On.post().to("/api/v1/categories").respondeWith("add"),
                On.delete().to("/api/v1/categories/{uid}").respondeWith("delete")
        );

        Bind.controller(UserControllerV1.class).withRoutes(
                On.post().to("/api/v1/users/login").respondeWith("login"),
                On.post().to("/api/v1/users/mfa").respondeWith("mfa"),
                On.post().to("/api/v1/users/refresh").respondeWith("refresh")
        );
        
        Bind.pathResource().to("/assets/");
        Bind.fileResource().to("/robots.txt");
        Bind.fileResource().to("/favicon.ico");
        Bind.fileResource().to("/favicon-16x16.png");
        Bind.fileResource().to("/favicon-32x32.png");
    }
    
    @Override
    public void applicationInitialized() {
    }

    @Override
    public void applicationStarted() {
    }

    @Override
    public void applicationStopped() {
    }
}