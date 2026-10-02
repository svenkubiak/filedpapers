package controllers;

import constants.Const;
import constants.Required;
import filters.SessionRevocationFilter;
import io.mangoo.annotations.FilterWith;
import io.mangoo.core.Config;
import io.mangoo.filters.CsrfFilter;
import io.mangoo.i18n.Messages;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.routing.bindings.Flash;
import io.mangoo.routing.bindings.Form;
import io.mangoo.routing.bindings.Session;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.TotpUtils;
import io.undertow.util.Headers;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotEmpty;
import models.Action;
import models.Item;
import models.enums.Role;
import models.enums.Type;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import services.DataService;
import services.NotificationService;
import utils.Avatars;
import utils.Utils;
import utils.io.IOUtils;
import utils.io.Leaf;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.IntStream;

import static constants.Const.GENERAL_ERROR;
import static constants.Const.TOAST_ERROR;

@FilterWith(SessionRevocationFilter.class)
public class DashboardController {
    private static final int MAX_FILE_SIZE_BYTES = 10485760; // 10MB
    private static final String AVATAR = "avatar";
    private final DataService dataService;
    private final NotificationService notificationService;
    private final Config config;
    private final Messages messages;
    private final String authRedirect;

    @Inject
    public DashboardController(DataService dataService,
                               NotificationService notificationService,
                               Config config,
                               Messages messages,
                               @Named("authentication.redirect.login") String loginRedirect) {
        this.notificationService = Objects.requireNonNull(notificationService, Required.NOTIFICATION_SERVICE);
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.config = Objects.requireNonNull(config, Required.CONFIG);
        this.messages = Objects.requireNonNull(messages, Required.MESSAGES);
        this.authRedirect = Objects.requireNonNull(loginRedirect, Required.LOGIN_REDIRECT);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Response dashboard(Authentication authentication, Optional<String> categoryUid) {
        String userUid = authentication.getSubject();
        var category = categoryUid
                .filter(Utils::isValidRandom)
                .map(uid -> dataService.findCategory(uid, userUid))
                .orElseGet(() -> dataService.findInbox(userUid));

        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);
        Optional<List<Map<String, Object>>> items = dataService.findItems(userUid, category.getUid());

        categories.ifPresent(Utils::sortCategories);
        var user = dataService.findUserByUid(userUid);

        return Response.ok()
                .render("active", category.getName().toLowerCase(Locale.ENGLISH))
                .render("breadcrumb", category.getName())
                .render("categories", categories.orElseThrow())
                .render("categoryUid", category.getUid())
                .render("items", Utils.convertItems(items.orElseThrow()))
                .render("username", user.getUsername())
                .render("avatar", user.getAvatar())
                .render("version", Utils.getVersion())
                .render("assetVersion", Utils.getAssetVersion())
                .render("trashRetention", Utils.getTrashRetentionLabel());
    }

    public Response item(Authentication authentication, @NotEmpty String uid) {
        String userUid = authentication.getSubject();

        try {
            return dataService.findItemForDisplay(uid, userUid)
                    .map(item -> {
                        String categoryUid = String.valueOf(item.get(Const.CATEGORY_UID));
                        var category = dataService.findCategory(categoryUid, userUid);

                        return Response.ok()
                                .render("item", Utils.convertItems(List.of(item)).getFirst())
                                .render("categoryUid", categoryUid)
                                .render("isTrash", category != null && category.getRole() == Role.TRASH);
                    })
                    .orElse(Response.notFound());
        } catch (IllegalArgumentException e) {
            return Response.badRequest();
        }
    }

    public Response profile(Authentication authentication, Flash flash, String mfa) {
        var userUid = authentication.getSubject();

        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);
        categories.ifPresent(Utils::sortCategories);

        var user = dataService.findUserByUid(userUid);
        String qrCode = null;
        if (StringUtils.isNotBlank(mfa)) {
            switch (mfa.toLowerCase(Locale.ENGLISH)) {
                case "enable" -> {
                    if (!user.isMfa()) {
                        var secret = TotpUtils.createSecret();
                        user.setMfaSecret(secret);
                        dataService.save(user);

                        qrCode = TotpUtils.getQRCode(user.getUsername(), "Filed Papers", secret);
                    }
                }
                case "disable" -> {
                    if (user.isMfa()) {
                        user.setMfa(false);
                        dataService.save(user);

                        flash.put(Const.TOAST_SUCCESS, messages.get("toast.mfa.disabled"));
                        notificationService.accountChanged(
                                user.getUsername(),
                                messages.get("email.account.changes.mfa.disabled")
                        );
                    }
                }
            }
        }

        String fallback = null;
        if (StringUtils.isNotBlank(flash.get(Const.MFA_FALLBACK))) {
            fallback = flash.get(Const.MFA_FALLBACK);
            flash.remove(Const.MFA_FALLBACK);
        }

        return Response.ok()
                .render("mfaFallback", fallback)
                .render("username", user.getUsername())
                .render("avatar", user.getAvatar())
                .render("maxAvatarBytes", Avatars.MAX_UPLOAD_BYTES)
                .render("confirmed", user.isConfirmed())
                .render("mfa", user.isMfa())
                .render("enrollMfa", !user.isMfa() && ("enable").equals(mfa))
                .render("languages", Utils.getLanguages())
                .render("language", Utils.language(user))
                .render("qrCode", qrCode)
                .render("active", "profile")
                .render("version", Utils.getVersion())
                .render("assetVersion", Utils.getAssetVersion())
                .render("categories", categories.orElseThrow());
    }

    @FilterWith(CsrfFilter.class)
    public Response doMfa(Authentication authentication, Form form, Flash flash) {
        String userUid = authentication.getSubject();
        IntStream.rangeClosed(1, 6)
                .mapToObj(i -> "otp-" + i)
                .forEach(key -> {
                    form.expectNumeric(key);
                    form.expectRangeLength(key, 1, 1);
                });

        if (form.isValid()) {
            String otp = java.util.stream.IntStream.rangeClosed(1, 6)
                    .mapToObj(i -> form.get("otp-" + i))
                    .collect(java.util.stream.Collectors.joining());

            var user = dataService.findUserByUid(userUid);
            if (authentication.isValidSecondFactor(user.getUid(), user.getMfaSecret(), otp)) {
                String fallback = dataService.enableMfa(userUid);
                if (StringUtils.isNotBlank(fallback)) {
                    flash.put(Const.TOAST_SUCCESS, messages.get("toast.mfa.enabled"));
                    flash.put(Const.MFA_FALLBACK, fallback);

                    notificationService.accountChanged(user.getUsername(), messages.get("email.account.changes.mfa.enabled"));
                } else {
                    flash.put(Const.TOAST_ERROR, GENERAL_ERROR);
                }
            }
        } else {
            flash.put(Const.TOAST_ERROR, GENERAL_ERROR);
        }

        return Response.redirect("/dashboard/profile");
    }

    // Cached as immutable: the url carries the avatar version, so a new upload is a new url.
    public Response avatar(Authentication authentication) {
        return dataService.findAvatar(authentication.getSubject())
                .map(data -> Response.ok()
                        .contentType("image/jpeg")
                        .header(Headers.CACHE_CONTROL_STRING, "private, max-age=31536000, immutable")
                        .bodyBinary(data))
                .orElse(Response.notFound());
    }

    @FilterWith(CsrfFilter.class)
    public Response doAvatar(Form form, Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectFile(AVATAR);
        form.expectFileMaxSize(AVATAR, Avatars.MAX_UPLOAD_BYTES);
        // Sniffed from the content, not the request; Avatars re-checks the format when decoding.
        form.expectFileMimeType(AVATAR, Avatars.MIME_TYPES);

        Optional<byte[]> avatar = form.isValid()
                ? form.getFile(AVATAR).flatMap(Avatars::normalize)
                : Optional.empty();

        if (avatar.isPresent() && dataService.saveAvatar(userUid, avatar.orElseThrow())) {
            flash.put(Const.TOAST_SUCCESS, messages.get("toast.avatar.success"));
        } else {
            flash.put(TOAST_ERROR, messages.get("toast.avatar.invalid"));
        }

        return Response.redirect("/dashboard/profile");
    }

    @FilterWith(CsrfFilter.class)
    public Response doDeleteAvatar(Authentication authentication, Flash flash) {
        dataService.deleteAvatar(authentication.getSubject());
        flash.put(Const.TOAST_SUCCESS, messages.get("toast.avatar.removed"));

        return Response.redirect("/dashboard/profile");
    }

    @FilterWith(CsrfFilter.class)
    public Response doLogoutDevices(Authentication authentication) {
        String userUid = authentication.getSubject();

        if (dataService.revokeSessions(userUid)) {
            // The revocation also invalidates the current cookie; re-issue it.
            authentication.update();

            return Response.ok();
        } else {
            return Response.badRequest();
        }
    }

    public Response resync(Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        Thread.ofVirtual().start(() -> dataService.resync(userUid));
        flash.put(Const.TOAST_SUCCESS, messages.get("toast.resync.success"));
        return Response.redirect("/dashboard");
    }

    @FilterWith(CsrfFilter.class)
    public Response doLanguage(Authentication authentication, Form form, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectValue("language");
        form.expectTrue("language", "de".equals(form.get("language")) || "en".equals(form.get("language")));

        if (form.isValid() && dataService.updateLanguage(userUid, form.get("language"))) {
            flash.put(Const.TOAST_SUCCESS, messages.get("toast.language.success"));

            var cookie = Utils.getLanguageCookie(form.get("language"), config, authentication.isRememberMe());
            return Response
                    .redirect("/dashboard/profile")
                    .cookie(cookie);
        } else {
            flash.put(TOAST_ERROR, messages.get("toast.error"));
        }

        return Response.redirect("/dashboard/profile");
    }

    public Response io(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);
        var user = dataService.findUserByUid(userUid);

        return Response.ok()
                .render("active", "io")
                .render("username", user.getUsername())
                .render("avatar", user.getAvatar())
                .render("version", Utils.getVersion())
                .render("assetVersion", Utils.getAssetVersion())
                .render("categories", categories.orElseThrow());
    }

    public Response about(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);
        var user = dataService.findUserByUid(userUid);

        return Response.ok()
                .render("active", "about")
                .render("username", user.getUsername())
                .render("avatar", user.getAvatar())
                .render("version", Utils.getVersion())
                .render("assetVersion", Utils.getAssetVersion())
                .render("categories", categories.orElseThrow());
    }

    public Response confirmEmail(Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();

        var user = dataService.findUserByUid(userUid);
        if (user != null && !user.isConfirmed()) {
            var token = Utils.randomString();
            dataService.save(new Action(userUid, token, Type.CONFIRM_EMAIL));
            notificationService.confirmEmail(user.getUsername(), token);

            flash.put(Const.TOAST_SUCCESS, messages.get("toast.confirm.email.success"));
        } else {
            flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
        }

        return Response.redirect("/dashboard/profile");
    }

    @FilterWith(CsrfFilter.class)
    public Response importer(Form form, Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectFile("importfile");
        form.expectFileMaxSize("importfile", MAX_FILE_SIZE_BYTES);
        form.expectFileMimeType("importfile", List.of("text/html"));

        if (form.isValid()) {
            var content = form.getFile("importfile")
                    .map(String::new)
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
                            // Untrusted input: skip unsafe entries instead of failing the whole import.
                            if (!child.isFolder() && Utils.isSafeLinkUrl(child.getUrl())) {
                                String cover = Utils.isSafeLinkUrl(child.getDataCover()) ? child.getDataCover() : null;

                                var item = Item.create()
                                        .withTitle(child.getTitle())
                                        .withUrl(child.getUrl())
                                        .withCategoryUid(category.getUid())
                                        .withUserUid(userUid)
                                        .withImage(cover);

                                item.setTimestamp(child.getAddDate().atZone(ZoneId.systemDefault()).toLocalDateTime());

                                dataService.save(item);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //Intentionally left blank
            }
        } else {
            flash.put(TOAST_ERROR, messages.get("toast.error"));
        }

        return Response.redirect("/dashboard");
    }

    @FilterWith(CsrfFilter.class)
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

    @FilterWith(CsrfFilter.class)
    public Response exporter(Authentication authentication) {
        String userUid = authentication.getSubject();
        List<Map<String, Object>> categories = dataService.findCategories(userUid).orElse(List.of());

        List<Leaf> leafs = categories.stream()
                .filter(category -> category.containsKey("uid"))
                .map(category -> {
                    String uid = (String) category.get("uid");
                    String name = (String) category.get("name");

                    List<Map<String, Object>> items = dataService.findItems(userUid, uid).orElse(List.of());

                    var folderLeaf = new Leaf();
                    folderLeaf.setFolder(true);
                    folderLeaf.setTitle(name);

                    items.stream()
                            .map(item -> {
                                String itemUid = (String) item.get("uid");
                                var dataItem = dataService.findItem(itemUid, userUid);

                                var itemLeaf = new Leaf();
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

    @FilterWith(CsrfFilter.class)
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

            var user = dataService.findUserByUid(userUid);
            if (user.getPassword().equals(CommonUtils.hashArgon2(password, user.getSalt()))) {
                user.setUsername(username);
                user.setConfirmed(false);
                dataService.save(user);

                var token = Utils.randomString();
                dataService.save(new Action(user.getUid(), token, Type.CONFIRM_EMAIL));
                notificationService.confirmEmail(username, token);

                notificationService.accountChanged(user.getUsername(), messages.get("email.account.changes.username"));
                flash.put(Const.TOAST_SUCCESS, messages.get("toast.username.success"));
            } else {
                flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }

    @FilterWith(CsrfFilter.class)
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
            if (user.getPassword().equals(CommonUtils.hashArgon2(password, user.getSalt()))) {
                user.setPassword(CommonUtils.hashArgon2(newPassword, user.getSalt()));
                // Revokes all sessions; authentication.update() re-issues the current one.
                user.setSessionsValidFrom(Instant.now().getEpochSecond());
                dataService.save(user);
                authentication.update();

                notificationService.accountChanged(user.getUsername(), messages.get("email.account.changes.password"));
                flash.put(Const.TOAST_SUCCESS, messages.get("toast.password.success"));
            } else {
                flash.put(Const.TOAST_ERROR, messages.get("toast.error"));
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }
}