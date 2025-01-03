package controllers.api;

import controllers.TestExtension;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CodecUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({TestExtension.class})
public class ItemsControllerV1Tests {

    @Test
    void testListUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/api/v1/items/" + CodecUtils.uuid())
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testTrashUnauthorized() {
        //when
        TestResponse response = TestRequest.delete("/api/v1/items/trash")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testDeleteUnauthorized() {
        //when
        TestResponse response = TestRequest.put("/api/v1/items/" + CodecUtils.uuid())
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testAddUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/api/v1/items")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testMoveUnauthorized() {
        //when
        TestResponse response = TestRequest.put("/api/v1/items")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }
}
