package controllers;

import constants.Const;
import constants.Required;
import filters.AuthenticationFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.core.Config;
import io.mangoo.i18n.Messages;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.*;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.totp.TotpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Action;
import models.Category;
import models.User;
import models.enums.Role;
import models.enums.Type;
import services.DataService;
import services.NotificationService;
import utils.Utils;

import java.util.Objects;

public class AuthenticationController {
    private final DataService dataService;
    private final NotificationService notificationService;
    private final Config config;
    private final Messages messages;
    private final String authRedirect;
    private final boolean registration;

    @Inject
    public AuthenticationController(DataService dataService,
                                    NotificationService notificationService,
                                    Config config,
                                    Messages messages,
                                    @Named("application.registration") boolean registration,
                                    @Named("authentication.redirect.login") String authRedirect) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.notificationService = Objects.requireNonNull(notificationService, Required.NOTIFICATION_SERVICE);
        this.config = Objects.requireNonNull(config, Required.CONFIG);
        this.messages = Objects.requireNonNull(messages, Required.MESSAGES);
        this.registration = registration;
        this.authRedirect = Objects.requireNonNull(authRedirect, Required.LOGIN_REDIRECT);
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

        return Response.redirect(authRedirect).disposeCookie(config.getI18nCookieName());
    }

    public Response doLogin(Flash flash, Form form, Authentication authentication) {
        form.expectValue("username", messages.get("validation.required.username"));
        form.expectValue("password", messages.get("validation.required.password"));
        form.expectEmail("username", messages.get("validation.required.email"));
        form.expectMaxLength("username", 256, messages.get("validation.max.length.username"));
        form.expectMaxLength("password", 256, messages.get("validation.max.length.password"));
        form.expectMaxLength("rememberme", 2);

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");
            Boolean rememberme = form.getBoolean("rememberme").orElse(Boolean.FALSE);

            var user = dataService.findUser(username);
            if (user != null && authentication.validLogin(user.getUid(), password, user.getSalt(), user.getPassword())) {
                authentication.login(user.getUid());
                authentication.rememberMe(rememberme);
                authentication.twoFactorAuthentication(user.isMfa());

                var cookie = Utils.getLanguageCookie(Utils.language(user), config, authentication.isRememberMe());

                return Response
                        .redirect("/dashboard")
                        .cookie(cookie);
            }
        }

        flash.setError(messages.get("validation.login.flash.error"));
        form.keep();

        return Response.redirect("/auth/login");
    }

    public Response doMfa(Flash flash, Form form, Authentication authentication) {
        form.expectValue("mfa", messages.get("validation.required.mfa"));

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

        flash.setError(messages.get("validation.mfa.flash.error"));
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

    @FilterWith(AuthenticationFilter.class)
    public Response resetPassword(Request request) {
        Action action = request.getAttribute(Const.ACTION);
        return Response.ok().render("token", action.getToken());
    }

    @FilterWith(AuthenticationFilter.class)
    public Response doResetPassword(Request request, Form form) {
        Action action = request.getAttribute(Const.ACTION);
        form.expectValue("password", messages.get("validation.required.current.password"));
        form.expectValue("confirm-password", messages.get("validation.required.password.confirm"));
        form.expectMinLength("confirm-password", 12, messages.get("validation.min.length.password"));
        form.expectMaxLength("confirm-password", 256, messages.get("validation.max.length.password"));

        if (form.isValid()) {
            dataService.setPassword(action.getUserUid(), form.get("password"));
            dataService.deleteAction(action);
            return Response.redirect("/success");
        }

        return Response.redirect("/auth/reset-password/" + action.getToken());
    }

    @FilterWith(AuthenticationFilter.class)
    public Response confirm(Request request) {
        Action action = request.getAttribute(Const.ACTION);
        dataService.confirmEmail(action.getUserUid());
        dataService.deleteAction(action);

        return Response.redirect("/success");
    }

    public Response doForgot(Form form, Flash flash) {
        form.expectValue("username", messages.get("validation.required.username"));
        form.expectEmail("username", messages.get("validation.required.email"));
        form.expectMaxLength("username", 256, messages.get("validation.max.length.password"));

        if (form.isValid()) {
            var user = dataService.findUser(form.get("username"));
            if (user != null) {
                var token = Utils.randomString();
                dataService.save(new Action(user.getUid(), token, Type.RESET_PASSWORD));
                notificationService.forgotPassword(form.get("username"), token);
            }
            flash.setSuccess(messages.get("validation.forgot.flash.success"));
        }

        form.keep();

        return Response.redirect("/auth/forgot");
    }

    public Response doSignup(Form form, Flash flash) {
        if (!registration) {
            return Response.redirect("/auth/login");
        }

        form.expectValue("username", messages.get("validation.required.username"));
        form.expectValue("password", messages.get("validation.required.password"));
        form.expectValue("confirm-password", messages.get("validation.required.password.confirm"));
        form.expectEmail("username", messages.get("validation.required.email"));
        form.expectMinLength("password", 12, messages.get("validation.min.length.password"));
        form.expectMaxLength("username", 256, messages.get("validation.max.length.username"));
        form.expectMaxLength("password", 256, messages.get("validation.max.length.password"));
        form.expectMaxLength("confirm-password", 256, messages.get("validation.max.length.confirm.password"));
        form.expectExactMatch("password", "confirm-password", messages.get("validation.password.match"));

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");

            var user = dataService.findUser(username);
            if (user == null) {
                user = new User(username);
                user.setPassword(CodecUtils.hashArgon2(password, user.getSalt()));
                dataService.save(user);
                dataService.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));
                dataService.save(new Category(Const.TRASH, user.getUid(), Role.TRASH));

                var token = Utils.randomString();
                dataService.save(new Action(user.getUid(), token, Type.CONFIRM_EMAIL));
                notificationService.confirmEmail(username, token);

                flash.setSuccess(messages.get("validation.signup.flash.success"));

                return Response.redirect("/auth/login");
            }
        }

        form.keep();

        return Response.redirect("/auth/signup");
    }
}
