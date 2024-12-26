package controllers;

import io.mangoo.routing.Response;
import io.mangoo.routing.bindings.Authentication;
import io.mangoo.routing.bindings.Flash;
import io.mangoo.routing.bindings.Form;
import io.mangoo.utils.CodecUtils;
import jakarta.inject.Inject;
import models.Category;
import models.Item;
import models.User;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.util.Strings;
import services.DataService;
import utils.Utils;
import utils.io.Exporter;
import utils.io.Importer;
import utils.io.Leaf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

public class DashboardController {
    private final DataService dataService;

    @Inject
    public DashboardController(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService can not be null");
    }
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Response dashboard(Authentication authentication, Optional<String> categoryUid) {
        String userUid = authentication.getSubject();
        Category category = categoryUid
                .map(uid -> dataService.findCategory(uid, userUid))
                .orElseGet(() -> dataService.findInbox(userUid));

        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);
        Optional<List<Map<String, Object>>> items = dataService.findItems(userUid, category.getUid());

        Utils.sortCategories(categories);

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

        Utils.sortCategories(categories);

        User user = dataService.findUserByUid(userUid);

        return Response.ok()
                .render("username", user.getUsername())
                .render("active", "profile")
                .render("categories", categories.orElseThrow());
    }

    public Response io(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        Utils.sortCategories(categories);

        return Response.ok()
                .render("active", "io")
                .render("categories", categories.orElseThrow());
    }

    public Response importer(Form form, Authentication authentication) throws IOException {
        String userUid = authentication.getSubject();
        var content = Strings.EMPTY;
        Optional<InputStream> formFile = form.getFile();
        if (formFile.isPresent()) {
            InputStream file = formFile.orElseThrow();
            try {
                content = IOUtils.toString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Importer importer = new Importer();
        try {
            List<Leaf> leafs = importer.parse(content);
            for (Leaf leaf : leafs) {
                if (leaf.isFolder()) {
                    Category category = dataService.findCategoryByName(leaf.getTitle(), userUid);
                    if (category == null) {
                        dataService.addCategory(userUid, leaf.getTitle());
                    }

                    category = dataService.findCategoryByName(leaf.getTitle(), userUid);

                    for (Leaf child : leaf.getChildren()) {
                        if (!child.isFolder()) {
                            Item item = new Item();
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
            System.err.println("Error reading the bookmarks file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error parsing bookmarks: " + e.getMessage());
            e.printStackTrace();
        }

        return Response.redirect("/dashboard");
    }

    public Response about(Authentication authentication) {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        Utils.sortCategories(categories);

        return Response.ok()
                .render("active", "about")
                .render("categories", categories.orElseThrow());
    }

    public Response exporter(Authentication authentication) throws IOException {
        String userUid = authentication.getSubject();
        Optional<List<Map<String, Object>>> categories = dataService.findCategories(userUid);

        List<Map<String, Object>> maps = categories.orElseThrow();
        List<Leaf> leafs = new ArrayList<>();

        for (Map<String, Object> map : maps) {
            if (map.containsKey("uid")) {
                String uid = (String) map.get("uid");
                String name = (String) map.get("name");

                Optional<List<Map<String, Object>>> items = dataService.findItems(userUid, uid);
                List<Map<String, Object>> i = items.orElseThrow();

                Leaf leaf = new Leaf();
                leaf.setFolder(true);
                leaf.setTitle(name);

                for (Map<String, Object> item : i) {
                    Item k = dataService.findItem((String) item.get("uid"), userUid);
                    Leaf s = new Leaf();
                    s.setFolder(false);
                    s.setUrl(k.getUrl());
                    s.setDataCover(k.getImage());
                    s.setTitle(k.getTitle());
                    s.setAddDate(k.getTimestamp().toInstant(ZoneOffset.UTC));
                    leaf.addChild(s);
                }

                leafs.add(leaf);
            }
        }

        Exporter exporter = new Exporter();
        try {
            exporter.export(leafs, "/Users/sven.kubiak/Desktop/filed-papers-export.html");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path path = Paths.get("/Users/sven.kubiak/Desktop/filed-papers-export.html");

        String read = Files.readAllLines(path).get(0);

        return Response.ok()
                .bodyText(read)
                .header("Content-Length", String.valueOf(path.toFile().length()))
                .header("Cache-Control", "no-cache")
                .header("Content-Disposition", "attachment; filename=\"example.txt\"");
    }

    public Response changeUsername(Form form, Authentication authentication, Flash flash) throws InterruptedException {
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

                flash.put("toastsuccess", "Username successfully changed");
            } else {
                flash.put("toasterror", "Ops, something went wrong");
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }

    public Response changePassword(Form form, Authentication authentication, Flash flash) {
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

                flash.put("toastsuccess", "Password successfully changed");
            } else {
                flash.put("toasterror", "Ops, something went wrong");
            }
        }

        form.keep();

        return Response.redirect("/dashboard/profile");
    }
}