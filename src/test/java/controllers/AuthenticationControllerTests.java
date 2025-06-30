package controllers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CodecUtils;
import io.undertow.util.StatusCodes;
import models.Category;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith({TestRunner.class})
public class AuthenticationControllerTests {

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(User.class);

        User user = new User("foo@bar.com");
        user.setPassword(CodecUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));
        datastore.save(new Category(Const.TRASH, user.getUid(), Role.TRASH));
    }

    @Test
    public void testLogin() {
        //given
        ListMultimap<String, String> form = ArrayListMultimap.create();
        form.put("username", "foo@bar.com");
        form.put("password", "bar");

        //when
        TestResponse response = TestRequest.post("/auth/login")
                .withForm(form)
                .execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Logout"));
        assertThat(response.getContent(), not(containsString("undefined")));
    }
}
