package controllers;

import constants.Const;
import constants.Required;
import filters.TokenFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.*;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.totp.TotpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Action;
import models.Category;
import models.User;
import models.enums.Type;
import services.DataService;
import services.NotificationService;

import java.util.Objects;

public class AuthenticationController {
    private final DataService dataService;
    private final NotificationService notificationService;
    private final boolean registration;
    private final String authRedirect;

    @Inject
    public AuthenticationController(DataService dataService,
                                    NotificationService notificationService,
                                    @Named("application.registration") boolean registration,
                                    @Named("authentication.redirect.login") String authRedirect) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.notificationService = Objects.requireNonNull(notificationService, Required.NOTIFICATION_SERVICE);
        this.registration = registration;
        this.authRedirect = Objects.requireNonNull(authRedirect, Required.AUTH_REDIRECT);
    }

    public Response login() {
        return Response.ok().render("registration", registration);
    }

    public Response mfa() {
        return Response.ok().render();
    }

    public Response logout(Authentication authentication, Session session) {
        authentication.logout();
        session.clear();

        return Response.redirect(authRedirect);
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
            boolean rememberme = form.getBoolean("rememberme").orElseGet(() -> false);

            var user = dataService.findUser(username);
            if (user != null && authentication.validLogin(user.getUid(), password, user.getSalt(), user.getPassword())) {
                authentication.login(user.getUid());
                authentication.rememberMe(rememberme);
                authentication.twoFactorAuthentication(user.isMfa());

                return Response.redirect("/dashboard");
            }
        }

        flash.setError("Invalid Username or Password!");
        form.keep();

        return Response.redirect("/auth/login");
    }

    public Response doMfa(Flash flash, Form form, Authentication authentication) {
        form.expectValue("mfa", "Please enter a TOTP value");

        if (form.isValid()) {
            String userUid = authentication.getSubject();
            String mfa = form.get("mfa");

            var user = dataService.findUserByUid(userUid);
            if (user != null && ( TotpUtils.verifiedTotp(user.getMfaSecret(), mfa) || CodecUtils.matchArgon2(mfa, user.getSalt(), user.getMfaFallback())) ) {
                authentication.twoFactorAuthentication(false);
                authentication.update();

                return Response.redirect("/dashboard");
            }
        }

        flash.setError("Invalid TOTP!");
        form.keep();

        return Response.redirect("/auth/mfa");
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

    @FilterWith(TokenFilter.class)
    public Response resetPassword(Request request) {
        Action action = request.getAttribute(Const.ACTION);
        return Response.ok().render("token", action.getToken());
    }

    @FilterWith(TokenFilter.class)
    public Response doResetPassword(Request request, Form form) {
        Action action = request.getAttribute(Const.ACTION);
        form.expectValue("password", "Please enter your current password");
        form.expectValue("confirm-password", "Please confirm your password");
        form.expectMinLength("confirm-password", 12, "Password must be at least 12 characters");
        form.expectMaxLength("confirm-password", 256, "Password must not be longer than 256 characters");

        if (form.isValid()) {
            dataService.setPassword(action.getUserUid(), form.get("password"));
            dataService.deleteAction(action);
            return Response.redirect("/success");
        }

        return Response.redirect("/auth/reset-password/" + action.getToken());
    }

    @FilterWith(TokenFilter.class)
    public Response confirm(Request request) {
        Action action = request.getAttribute(Const.ACTION);
        dataService.confirmEmail(action.getUserUid());
        dataService.deleteAction(action);

        return Response.redirect("/success");
    }

    public Response doForgot(Form form, Flash flash) {
        form.expectValue("username", "Please enter an email address");
        form.expectEmail("username", "Please enter a valid email address");
        form.expectMaxLength("username", 256, "Email address must not be longer than 256 characters");

        if (form.isValid()) {
            User user = dataService.findUser(form.get("username"));
            if (user != null) {
                String token = MangooUtils.randomString(64);
                dataService.save(new Action(user.getUid(), token, Type.RESET_PASSWORD));
                notificationService.forgotPassword(form.get("username"), token);
            }
            flash.setSuccess("If your email address exists, you will receive an email shortly.");
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

            var user = dataService.findUser(username);
            if (user == null) {
                user = new User(username);
                user.setPassword(CodecUtils.hashArgon2(password, user.getSalt()));
                dataService.save(user);
                dataService.save(new Category(Const.INBOX, user.getUid()));
                dataService.save(new Category(Const.TRASH, user.getUid()));

                String token = MangooUtils.randomString(64);
                dataService.save(new Action(user.getUid(), token, Type.CONFIRM_EMAIL));
                notificationService.confirmEmail(username, token);

                flash.setSuccess("Your account has been created! You will receive an email shortly.");

                return Response.redirect("/auth/login");
            }
        }

        form.keep();

        return Response.redirect("/auth/signup");
    }
}
