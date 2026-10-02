package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class AvatarsTests {

    private static byte[] image(int width, int height, String format, int type) throws IOException {
        var image = new BufferedImage(width, height, type);
        var graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillOval(0, 0, width, height);
        graphics.dispose();

        var output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static BufferedImage read(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** A square jpeg, red in the top half and blue in the bottom half. */
    private static byte[] halves() throws IOException {
        var image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 400, 200);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 200, 400, 200);
        graphics.dispose();

        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    /** Puts an exif block with just the orientation tag right behind the start of image marker. */
    private static byte[] withOrientation(byte[] jpeg, int orientation, boolean bigEndian) {
        byte[] tiff = bigEndian
                ? new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1,
                             0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                             0, 0, 0, 0}
                : new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0,
                             0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0,
                             0, 0, 0, 0};
        int length = 2 + 6 + tiff.length;

        var output = new ByteArrayOutputStream();
        output.write(0xFF);
        output.write(0xD8);
        output.write(0xFF);
        output.write(0xE1);
        output.write(length >> 8);
        output.write(length & 0xFF);
        output.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        output.writeBytes(tiff);
        output.write(jpeg, 2, jpeg.length - 2);

        return output.toByteArray();
    }

    private static boolean isRed(BufferedImage image, int x, int y) {
        var color = new Color(image.getRGB(x, y));
        return color.getRed() > 180 && color.getBlue() < 80;
    }

    private static boolean isBlue(BufferedImage image, int x, int y) {
        var color = new Color(image.getRGB(x, y));
        return color.getBlue() > 180 && color.getRed() < 80;
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    public void testAcceptedFormatsBecomeASquareJpeg(String format) throws IOException {
        byte[] stored = Avatars.normalize(image(1200, 700, format, BufferedImage.TYPE_INT_RGB)).orElseThrow();

        assertThat(stored[0] & 0xff, equalTo(0xFF));
        assertThat(stored[1] & 0xff, equalTo(0xD8));
        assertThat(read(stored).getWidth(), equalTo(Avatars.SIZE));
        assertThat(read(stored).getHeight(), equalTo(Avatars.SIZE));
    }

    @Test
    public void testTransparencyAndSmallPicturesAreHandled() throws IOException {
        assertThat(Avatars.normalize(image(800, 600, "png", BufferedImage.TYPE_INT_ARGB)).isPresent(), equalTo(true));
        assertThat(read(Avatars.normalize(image(40, 40, "png", BufferedImage.TYPE_INT_RGB)).orElseThrow()).getWidth(),
                equalTo(Avatars.SIZE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gif", "bmp"})
    public void testOtherImageFormatsAreRejected(String format) throws IOException {
        assertThat(Avatars.normalize(image(300, 300, format, BufferedImage.TYPE_INT_RGB)).isPresent(), equalTo(false));
    }

    @Test
    public void testAnythingAppendedToThePictureIsDropped() throws IOException {
        byte[] png = image(300, 300, "png", BufferedImage.TYPE_INT_RGB);
        byte[] payload = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = Arrays.copyOf(png, png.length + payload.length);
        System.arraycopy(payload, 0, polyglot, png.length, payload.length);

        byte[] stored = Avatars.normalize(polyglot).orElseThrow();

        assertThat(new String(stored, StandardCharsets.ISO_8859_1), not(containsString("script")));
    }

    @Test
    public void testBrokenAndForeignContentIsRejected() throws IOException {
        byte[] png = image(300, 300, "png", BufferedImage.TYPE_INT_RGB);

        assertThat(Avatars.normalize(null).isPresent(), equalTo(false));
        assertThat(Avatars.normalize(new byte[0]).isPresent(), equalTo(false));
        assertThat(Avatars.normalize(Arrays.copyOf(png, 60)).isPresent(), equalTo(false));
        assertThat(Avatars.normalize("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)).isPresent(),
                equalTo(false));
        assertThat(Avatars.normalize(new byte[Avatars.MAX_UPLOAD_BYTES + 1]).isPresent(), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExifOrientationIsRead(boolean bigEndian) throws IOException {
        byte[] jpeg = halves();

        assertThat(Avatars.exifOrientation(jpeg), equalTo(1));
        for (int orientation = 1; orientation <= 8; orientation++) {
            assertThat(Avatars.exifOrientation(withOrientation(jpeg, orientation, bigEndian)), equalTo(orientation));
        }
    }

    @Test
    public void testBrokenExifCountsAsUpright() throws IOException {
        byte[] jpeg = withOrientation(halves(), 6, true);

        assertThat(Avatars.exifOrientation(Arrays.copyOf(jpeg, 20)), equalTo(1));
        assertThat(Avatars.exifOrientation(withOrientation(halves(), 9, true)), equalTo(1));
        assertThat(Avatars.exifOrientation(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 0}), equalTo(1));
        assertThat(Avatars.exifOrientation(new byte[0]), equalTo(1));
    }

    @Test
    public void testPictureIsTurnedUpright() throws IOException {
        // exif 6: turned 90 clockwise, the red top half ends up on the right
        var clockwise = read(Avatars.normalize(withOrientation(halves(), 6, true)).orElseThrow());
        assertThat(isBlue(clockwise, 40, 128), equalTo(true));
        assertThat(isRed(clockwise, 216, 128), equalTo(true));

        // exif 8: counter-clockwise, red ends up on the left
        var counterClockwise = read(Avatars.normalize(withOrientation(halves(), 8, false)).orElseThrow());
        assertThat(isRed(counterClockwise, 40, 128), equalTo(true));
        assertThat(isBlue(counterClockwise, 216, 128), equalTo(true));

        // exif 3: upside down
        var upsideDown = read(Avatars.normalize(withOrientation(halves(), 3, true)).orElseThrow());
        assertThat(isBlue(upsideDown, 128, 40), equalTo(true));
        assertThat(isRed(upsideDown, 128, 216), equalTo(true));

        var upright = read(Avatars.normalize(halves()).orElseThrow());
        assertThat(isRed(upright, 128, 40), equalTo(true));
        assertThat(isBlue(upright, 128, 216), equalTo(true));
    }

    @Test
    public void testOrientationMapsEveryCorner() {
        var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                image.setRGB(x, y, 0xFF000000 | (y * 3 + x + 1));
            }
        }

        // indexed by exif orientation, pixels row by row
        int[][] expected = {
                null,
                {1, 2, 3, 4, 5, 6},
                {3, 2, 1, 6, 5, 4},
                {6, 5, 4, 3, 2, 1},
                {4, 5, 6, 1, 2, 3},
                {1, 4, 2, 5, 3, 6},
                {4, 1, 5, 2, 6, 3},
                {6, 3, 5, 2, 4, 1},
                {3, 6, 2, 5, 1, 4}
        };

        for (int orientation = 1; orientation <= 8; orientation++) {
            var result = Avatars.orient(image, orientation);
            int[] pixels = result.getRGB(0, 0, result.getWidth(), result.getHeight(), null, 0, result.getWidth());
            int[] values = Arrays.stream(pixels).map(p -> p & 0xFFFFFF).toArray();

            assertThat("orientation " + orientation, values, equalTo(expected[orientation]));
        }
    }
}
