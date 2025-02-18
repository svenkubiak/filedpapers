package controllers;

import constants.Const;
import constants.Required;
import io.mangoo.constants.Hmac;
import io.mangoo.i18n.Messages;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.routing.bindings.Flash;
import io.mangoo.routing.bindings.Form;
import io.mangoo.routing.bindings.Session;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.totp.TotpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Action;
import models.Item;
import models.User;
import models.enums.Type;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import services.DataService;
import services.NotificationService;
import utils.IOUtils;
import utils.Utils;
import utils.io.Leaf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static constants.Const.TOAST_ERROR;

public class DashboardController {
    private final DataService dataService;
    private final NotificationService notificationService;
    private final Messages messages;
    private final String authRedirect;

    @Inject
    public DashboardController(DataService dataService,
                               NotificationService notificationService,
                               Messages messages,
                               @Named("authentication.redirect.login") String loginRedirect) {
        this.notificationService = Objects.requireNonNull(notificationService, Required.NOTIFICATION_SERVICE);
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.messages = Objects.requireNonNull(messages, Required.MESSAGES);
        this.authRedirect = Objects.requireNonNull(loginRedirect, Required.LOGIN_REDIRECT);

    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Response dashboard(Authentication authentication, Optional<String> categoryUid) {
        String userUid = authentication.getSubject();
        var category = categoryUid
                .map(uid -> dataService.findCategory(uid, userUid))
                .orElseGet(() -> dataService.findInbox(userUid));

        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);
        Optional<List<Map<String, Object>>> items = dataService.findItems(userUid, category.getUid());

        categories.ifPresent(Utils::sortCategories);

        return Response.ok()
                .render("active", category.getName().toLowerCase(Locale.ENGLISH))
                .render("breadcrumb", category.getName())
                .render("categories", categories.orElseThrow())
                .render("categoryUid", category.getUid())
                .render("items", items.orElseThrow() );
    }

    public Response profile(Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);

        var user = dataService.findUserByUid(userUid);
        String qrCode = null;
        if (user.isMfa()) {
            qrCode = TotpUtils.getQRCode(user.getUsername(), "Filed Papers", user.getMfaSecret(), Hmac.SHA512, "6", "30");
        }

        String fallback = null;
        if (StringUtils.isNotBlank(flash.get(Const.MFA_FALLBACK))) {
            fallback = flash.get(Const.MFA_FALLBACK);
            flash.put(Const.MFA_FALLBACK, Strings.EMPTY);
        }

        return Response.ok()
                .render("mfaFallback", fallback)
                .render("username", user.getUsername())
                .render("confirmed", user.isConfirmed())
                .render("mfa", user.isMfa())
                .render("qrCode", qrCode)
                .render("active", "profile")
                .render("categories", categories.orElseThrow());
    }

    public Response doMfa(Authentication authentication, Form form, Flash flash) {
        String userUid = authentication.getSubject();
        String mfa = form.get("mfa");

        String fallback = dataService.changeMfa(userUid, ("on").equals(mfa));
        flash.put(Const.TOAST_SUCCESS, messages.get("toast.mfa.success", ("on").equals(mfa) ? messages.get("toast.mfa.enabled") : messages.get("toast.mfa.disabled")));
        flash.put(Const.MFA_FALLBACK, fallback);

        return Response.redirect("/dashboard/profile");
    }

    public Response io(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);

        return Response.ok()
                .render("active", "io")
                .render("categories", categories.orElseThrow());
    }

    public Response confirmEmail(Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();

        var user = dataService.findUserByUid(userUid);
        if (user != null && !user.isConfirmed()) {
            var token = MangooUtils.randomString(64);
            dataService.save(new Action(userUid, token, Type.CONFIRM_EMAIL));
            notificationService.confirmEmail(user.getUsername(), token);

            flash.put(Const.TOAST_SUCCESS, messages.get("toast.confirm.email.success"));
        } else {
            flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
        }

        return Response.redirect("/dashboard/profile");
    }

    public Response importer(Form form, Authentication authentication) {
        String userUid = authentication.getSubject();

        var content = Optional.ofNullable(form.getFile())
                .flatMap(file -> file.map(IOUtils::readContent))
                .orElse(Strings.EMPTY);

        try {
            List<Leaf> leafs = IOUtils.importItems(content);

            for (Leaf leaf : leafs) {
                if (leaf.isFolder()) {
                    var category = dataService.findCategoryByName(leaf.getTitle(), userUid);
                    if (category == null) {
                        dataService.addCategory(userUid, leaf.getTitle());
                        category = dataService.findCategoryByName(leaf.getTitle(), userUid);
                    }

                    for (Leaf child : leaf.getChildren()) {
                        if (!child.isFolder()) {
                            var item = new Item();
                            item.setTitle(child.getTitle());
                            item.setUrl(child.getUrl());
                            item.setCategoryUid(category.getUid());
                            item.setUserUid(userUid);
                            item.setUid(CodecUtils.uuid());
                            item.setImage(child.getDataCover());
                            item.setTimestamp(child.getAddDate().atZone(ZoneId.systemDefault()).toLocalDateTime());

                            dataService.save(item);

                            category.setCount(category.getCount() + 1);
                            dataService.save(category);
                        }
                    }
                }
            }
        } catch (IOException e) {
            //Intentionally left blank
        }

        return Response.redirect("/dashboard");
    }

    public Response doDeleteAccount(Authentication authentication, Form form, Session session, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectValue("confirmPassword");

        if (form.isValid() && dataService.deleteAccount(form.get("confirmPassword"), userUid)) {
            authentication.logout();
            session.clear();

            return Response.redirect(authRedirect);
        }

        flash.put(TOAST_ERROR, messages.get("toast.error"));

        return Response.redirect("/dashboard/profile");
    }

    public Response exporter(Authentication authentication) {
        String userUid = authentication.getSubject();
        List<Map<String, Object>> categories = dataService.findCategories(userUid).orElse(List.of());

        List<Leaf> leafs = categories.stream()
                .filter(category -> category.containsKey("uid"))
                .map(category -> {
                    String uid = (String) category.get("uid");
                    String name = (String) category.get("name");

                    List<Map<String, Object>> items = dataService.findItems(userUid, uid).orElse(List.of());

                    Leaf folderLeaf = new Leaf();
                    folderLeaf.setFolder(true);
                    folderLeaf.setTitle(name);

                    items.stream()
                            .map(item -> {
                                String itemUid = (String) item.get("uid");
                                Item dataItem = dataService.findItem(itemUid, userUid);

                                Leaf itemLeaf = new Leaf();
                                itemLeaf.setFolder(false);
                                itemLeaf.setUrl(dataItem.getUrl());
                                itemLeaf.setDataCover(dataItem.getImage());
                                itemLeaf.setTitle(dataItem.getTitle());
                                itemLeaf.setAddDate(dataItem.getTimestamp().toInstant(ZoneOffset.UTC));

                                return itemLeaf;
                            }).forEach(folderLeaf::addChild);

                    return folderLeaf;
                }).toList();

        String export = IOUtils.exportItems(leafs);

        return Response.ok()
                .bodyText(export)
                .header("Content-Length", String.valueOf(export.getBytes(StandardCharsets.UTF_8).length))
                .header("Cache-Control", "no-cache")
                .header("Content-Disposition", "attachment; filename=\"filed-papers-export.html\"");
    }

    public Response doChangeUsername(Form form, Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectValue("username", messages.get("validation.required.username"));
        form.expectEmail("username", messages.get("validation.required.email"));
        form.expectMaxLength("username", 256, messages.get("validation.max.length.username"));
        form.expectValue("password", messages.get("validation.required.password"));
        form.expectMinLength("password", 12, messages.get("validation.min.length.password"));
        form.expectMaxLength("password", 256, messages.get("validation.max.length.password"));

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");

            User user = dataService.findUserByUid(userUid);
            if (user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
                user.setUsername(username);
                user.setConfirmed(false);
                dataService.save(user);

                var token = MangooUtils.randomString(64);
                dataService.save(new Action(user.getUid(), token, Type.CONFIRM_EMAIL));
                notificationService.confirmEmail(username, token);

                flash.put(Const.TOAST_SUCCESS, messages.get("toast.username.success"));
            } else {
                flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }

    public Response doChangePassword(Form form, Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectValue("password", messages.get("validation.required.current.password"));
        form.expectValue("new-password", messages.get("validation.required.new.password"));
        form.expectValue("confirm-password", messages.get("validation.required.password.confirm"));
        form.expectMinLength("new-password", 12, messages.get("validation.min.length.password"));
        form.expectMaxLength("new-password", 256, messages.get("validation.max.length.password"));
        form.expectMinLength("confirm-password", 12, messages.get("validation.min.length.confirm.password"));
        form.expectMaxLength("confirm-password", 256, messages.get("validation.max.length.confirm.password"));
        form.expectExactMatch("new-password", "confirm-password", messages.get("validation.password.match"));

        if (form.isValid()) {
            String password = form.get("password");
            String newPassword = form.get("new-password");

            var user = dataService.findUserByUid(userUid);
            if (user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
                user.setPassword(CodecUtils.hashArgon2(newPassword, user.getSalt()));
                dataService.save(user);
                notificationService.passwordChanged(user.getUsername());

                flash.put(Const.TOAST_SUCCESS, messages.get("toast.password.success"));
            } else {
                flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }
}