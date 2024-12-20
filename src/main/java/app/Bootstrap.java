package app;

import constants.Const;
import controllers.ApplicationController;
import controllers.AuthenticationController;
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
    private final Datastore datastore;
    
    @Inject
    public Bootstrap(Datastore datastore) {
        this.datastore = Objects.requireNonNull(datastore, "datastore can not be null");
    }
    
    @Override
    public void initializeRoutes() {
        Bind.controller(ApplicationController.class).withRoutes(
                On.get().to("/").respondeWith("index"),
                On.get().to("/dashboard").respondeWith("dashboard"),
                On.get().to("/dashboard/category/{uid}").respondeWith("category"),
                On.get().to("/health").respondeWith("health")
        );

        Bind.controller(AuthenticationController.class).withRoutes(
                On.get().to("/auth/login").respondeWith("login"),
                On.post().to("/auth/authenticate").respondeWith("authenticate")
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
        //FIXME: Remove test data
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);
        datastore.dropCollection(User.class);

        User user = new User("foo", CodecUtils.hashArgon2("bar"), "foo@bar.com");
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid()));
        datastore.save(new Category(Const.TRASH, user.getUid()));
    }

    @Override
    public void applicationStopped() {
        // TODO Auto-generated method stub
    }
}