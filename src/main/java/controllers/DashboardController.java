package controllers;

import constants.Const;
import constants.Required;
import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.routing.bindings.Flash;
import io.mangoo.routing.bindings.Form;
import io.mangoo.routing.bindings.Session;
import io.mangoo.utils.CodecUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.Item;
import models.User;
import org.apache.logging.log4j.util.Strings;
import services.DataService;
import utils.IOUtils;
import utils.Utils;
import utils.io.Leaf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static constants.Const.SOMETHING_WENT_WRONG;
import static constants.Const.TOAST_ERROR;

public class DashboardController {
    private final DataService dataService;
    private final String authRedirect;

    @Inject
    public DashboardController(DataService dataService, @Named("authentication.redirect") String authRedirect) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.authRedirect = Objects.requireNonNull(authRedirect, Required.AUTH_REDIRECT);

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

    public Response profile(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);

        var user = dataService.findUserByUid(userUid);

        return Response.ok()
                .render("username", user.getUsername())
                .render("active", "profile")
                .render("categories", categories.orElseThrow());
    }

    public Response io(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        categories.ifPresent(Utils::sortCategories);

        return Response.ok()
                .render("active", "io")
                .render("categories", categories.orElseThrow());
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

        flash.put(TOAST_ERROR, SOMETHING_WENT_WRONG);

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
        form.expectValue("username", "Please enter an email address");
        form.expectEmail("username", "Please enter a valid email address");
        form.expectMaxLength("username", 256, "Email address must not be longer than 256 characters");
        form.expectValue("password", "Please enter your current password");
        form.expectMinLength("password", 12, "Password must be at least 12 characters");
        form.expectMaxLength("password", 256, "Password must not be longer than 256 characters");

        if (form.isValid()) {
            String username = form.get("username");
            String password = form.get("password");

            User user = dataService.findUserByUid(userUid);
            if (user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
                user.setUsername(username);
                dataService.save(user);

                flash.put(Const.TOAST_SUCCESS, "Username successfully changed");
            } else {
                flash.put(Const.TOAST_ERROR, SOMETHING_WENT_WRONG);
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }

    public Response doChangePassword(Form form, Authentication authentication, Flash flash) {
        String userUid = authentication.getSubject();
        form.expectValue("password", "Please enter your current password");
        form.expectValue("new-password", "Please enter a new password");
        form.expectValue("confirm-password", "Please confirm your password");
        form.expectMinLength("new-password", 12, "Password must be at least 12 characters");
        form.expectMaxLength("new-password", 256, "Password must not be longer than 256 characters");
        form.expectMinLength("confirm-password", 12, "Password must be at least 12 characters");
        form.expectMaxLength("confirm-password", 256, "Password must not be longer than 256 characters");
        form.expectExactMatch("new-password", "confirm-password", "Passwords do not match");

        if (form.isValid()) {
            String password = form.get("password");
            String newPassword = form.get("new-password");

            User user = dataService.findUserByUid(userUid);
            if (user.getPassword().equals(CodecUtils.hashArgon2(password, user.getSalt()))) {
                user.setPassword(CodecUtils.hashArgon2(newPassword, user.getSalt()));
                dataService.save(user);

                flash.put(Const.TOAST_SUCCESS, "Password successfully changed");
            } else {
                flash.put(Const.TOAST_ERROR, Const.SOMETHING_WENT_WRONG);
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }
}