package controllers;

import constants.Const;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.routing.bindings.Flash;
import io.mangoo.routing.bindings.Form;
import io.mangoo.routing.bindings.Session;
import io.mangoo.utils.CodecUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Category;
import models.User;
import services.DataService;

import java.util.Objects;

public class AuthenticationController {
    private final DataService dataService;
    private final boolean registration;

    @Inject
    public AuthenticationController(DataService dataService, @Named("application.registration") boolean registration) {
        this.dataService = Objects.requireNonNull(dataService, "dataService can not be null");
        this.registration = registration;
    }

    public Response login() {
        return Response.ok().render("registration", registration);
    }

    public Response logout(Authentication authentication, Session session) {
        authentication.logout();
        session.clear();

        return Response.redirect("/auth/login");
    }

    public Response doLogin(Flash flash, Form form, Authentication authentication) {
        form.expectValue("username", "Please enter an email address");
        form.expectValue("password", "Please enter a password");
        form.expectEmail("username", "Please enter a valid email address");
        form.expectMaxLength("username", 256, "Email address must not be longer than 256 characters");
        form.expectMaxLength("password", 256, "Password must not be longer than 256 characters");
        form.expectMaxLength("rememberme", 2);

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");
            boolean remember = form.getBoolean("rememberme").orElseGet(() -> false);

            User user = dataService.findUser(username);
            if (user != null && authentication.validLogin(user.getUid(), password, user.getSalt(), user.getPassword())) {
                authentication.login(user.getUid());
                authentication.rememberMe(remember);

                return Response.redirect("/dashboard");
            }
            flash.put("login", "error");
        }

        flash.put("login", "error");
        form.keep();

        return Response.redirect("/auth/login");
    }

    public Response signup() {
        if (registration) {
            return Response.ok().render();
        }

        return Response.redirect("/auth/login");
    }

    public Response forgot() {
        return Response.ok().render();
    }

    public Response doForgot(Form form, Flash flash) {
        form.expectValue("username", "Please enter an email address");
        form.expectEmail("username", "Please enter a valid email address");
        form.expectMaxLength("username", 256, "Email address must not be longer than 256 characters");

        if (form.isValid()) {
            flash.put("forgot", "success");
        }

        form.keep();

        return Response.redirect("/auth/forgot");
    }

    public Response doSignup(Form form, Flash flash) {
        if (!registration) {
            return Response.redirect("/auth/login");
        }

        form.expectValue("username", "Please enter an email address");
        form.expectValue("password", "Please enter a password");
        form.expectValue("confirm-password", "Please confirm your password");
        form.expectEmail("username", "Please enter a valid email address");
        form.expectMinLength("password", 12, "Password must be at least 12 characters");
        form.expectMaxLength("username", 256, "Email address must not be longer than 256 characters");
        form.expectMaxLength("password", 256, "Password must not be longer than 256 characters");
        form.expectMaxLength("confirm-password", 256, "Password confirmation must not be longer than 256 characters");
        form.expectExactMatch("password", "confirm-password", "Passwords do not match");

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");

            User user = dataService.findUser(username);
            if (user == null) {
                user = new User(username);
                user.setPassword(CodecUtils.hashArgon2(password, user.getSalt()));
                dataService.save(user);
                dataService.save(new Category(Const.INBOX, user.getUid()));
                dataService.save(new Category(Const.TRASH, user.getUid()));

                flash.put("signup", "success");

                return Response.redirect("/auth/login");
            }
        }

        form.keep();

        return Response.redirect("/auth/signup");
    }
}
