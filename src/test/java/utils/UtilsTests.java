package utils;

import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

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
        String uuid = "0f37d26a-d9ce-423b-b320-f95483f77e1dds+";

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

    @Test
    public void testTrashRetentionLabelUsesDaysForWholeDays() {
        //given the configured retention, which defaults to 72 hours
        int hours = Utils.getTrashRetention();

        //when
        Map<String, String> label = Utils.getTrashRetentionLabel();

        //then a whole number of days is shown as days, anything else as hours
        if (hours >= 24 && hours % 24 == 0) {
            assertThat(label.get("value"), equalTo(String.valueOf(hours / 24)));
            assertThat(label.get("unit"), equalTo(hours / 24 == 1 ? "day" : "days"));
        } else {
            assertThat(label.get("value"), equalTo(String.valueOf(hours)));
            assertThat(label.get("unit"), equalTo(hours == 1 ? "hour" : "hours"));
        }
    }

    @Test
    public void testTrashRetentionLabelNamesAnExistingTranslationKey() {
        //the template builds the key as dashboard.trash.unit.<unit>, so the
        //unit has to be one of the four that exist
        String unit = Utils.getTrashRetentionLabel().get("unit");

        assertThat(List.of("hour", "hours", "day", "days").contains(unit), equalTo(true));
    }
}
