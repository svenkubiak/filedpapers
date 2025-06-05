package utils;

import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith({TestRunner.class})
public class UtilsTests {

    @Test
    public void testValidUuid() {
        //given
        String uuid = "1f0088c32e226350913fb0b6f26ac0ac";

        //when
        boolean valid = Utils.isValidRandom(uuid);

        //then
        assertThat(valid, equalTo(true));
    }

    @Test
    public void testInvalidUuid() {
        //given
        String uuid = "0f37d26a-d9ce-423b-b320-f95483f77e1d";

        //when
        boolean valid = Utils.isValidRandom(uuid);

        //then
        assertThat(valid, equalTo(false));
    }

    @Test
    public void testValidOtp() {
        //given
        String mfa = "123456";

        //when
        boolean valid = Utils.isValidOtp(mfa);

        //then
        assertThat(valid, equalTo(true));
    }

    @Test
    public void testInvalidOtp() {
        //given
        String mfa = "0f37d26a";

        //when
        boolean valid = Utils.isValidOtp(mfa);

        //then
        assertThat(valid, equalTo(false));
    }
}
