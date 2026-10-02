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
        String uuid = "1f0088c32e226350913fb0b6f26ac0ac";

        boolean valid = Utils.isValidRandom(uuid);

        assertThat(valid, equalTo(true));
    }

    @Test
    public void testInvalidUuid() {
        String uuid = "0f37d26a-d9ce-423b-b320-f95483f77e1dds+";

        boolean valid = Utils.isValidRandom(uuid);

        assertThat(valid, equalTo(false));
    }

    @Test
    public void testValidOtp() {
        String mfa = "123456";

        boolean valid = Utils.isValidOtp(mfa);

        assertThat(valid, equalTo(true));
    }

    @Test
    public void testInvalidOtp() {
        String mfa = "0f37d26a";

        boolean valid = Utils.isValidOtp(mfa);

        assertThat(valid, equalTo(false));
    }

    @Test
    public void testTrashRetentionLabelUsesDaysForWholeDays() {
        int hours = Utils.getTrashRetention();

        Map<String, String> label = Utils.getTrashRetentionLabel();

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
        // the template looks up dashboard.trash.unit.<unit>
        String unit = Utils.getTrashRetentionLabel().get("unit");

        assertThat(List.of("hour", "hours", "day", "days").contains(unit), equalTo(true));
    }
}
