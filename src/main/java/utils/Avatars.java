package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// Uploads are untrusted: decode, redraw onto a fresh canvas and re-encode so no bytes or
// metadata of the original survive. Only the EXIF orientation is honoured.
public final class Avatars {
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    public static final List<String> MIME_TYPES = List.of("image/png", "image/jpeg");
    static final int SIZE = 256;
    private static final Logger LOG = LogManager.getLogger(Avatars.class);
    private static final Set<String> FORMATS = Set.of("png", "jpeg");
    private static final int MAX_DIMENSION = 8000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final float QUALITY = 0.85f;
    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TYPE_SHORT = 3;

    private Avatars() {
    }

    public static Optional<byte[]> normalize(byte[] upload) {
        if (upload == null || upload.length == 0 || upload.length > MAX_UPLOAD_BYTES) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(decode(upload))
                    .map(Avatars::square)
                    .map(Avatars::encode);
        } catch (IOException | RuntimeException e) {
            LOG.debug("Rejected avatar upload", e);
            return Optional.empty();
        }
    }

    // Dimensions are checked from the header before decoding (decompression bombs),
    // and large images are subsampled to bound memory.
    private static BufferedImage decode(byte[] upload) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ENGLISH);
                if (!FORMATS.contains(format)) {
                    return null;
                }

                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    return null;
                }

                // Keep at least twice the target size for smooth final scaling.
                int step = Math.max(1, Math.min(width, height) / (SIZE * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);

                BufferedImage image = reader.read(0, param);

                return "jpeg".equals(format) ? orient(image, exifOrientation(upload)) : image;
            } finally {
                reader.dispose();
            }
        }
    }

    // Anything malformed yields 1 (as stored) rather than rejecting the upload.
    static int exifOrientation(byte[] jpeg) {
        try {
            if (u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) {
                return 1;
            }

            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if (u8(jpeg, pos) != 0xFF) {
                    return 1;
                }

                int marker = u8(jpeg, pos + 1);
                if (marker == 0xFF) {
                    // fill byte
                    pos++;
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) {
                    // SOS or EOI: no EXIF segment before the image data
                    return 1;
                }

                int length = u16(jpeg, pos + 2, true);
                if (length < 2) {
                    return 1;
                }

                int start = pos + 4;
                int end = Math.min(pos + 2 + length, jpeg.length);
                if (marker == 0xE1 && isExifHeader(jpeg, start)) {
                    return tiffOrientation(jpeg, start + 6, end);
                }

                pos += 2 + length;
            }
        } catch (IndexOutOfBoundsException e) {
            // Truncated file: treat as unrotated.
        }

        return 1;
    }

    private static boolean isExifHeader(byte[] data, int pos) {
        return pos + 6 <= data.length
                && data[pos] == 'E' && data[pos + 1] == 'x' && data[pos + 2] == 'i' && data[pos + 3] == 'f'
                && data[pos + 4] == 0 && data[pos + 5] == 0;
    }

    private static int tiffOrientation(byte[] data, int tiff, int end) {
        boolean bigEndian;
        if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            bigEndian = true;
        } else if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            bigEndian = false;
        } else {
            return 1;
        }

        if (u16(data, tiff + 2, bigEndian) != 42) {
            return 1;
        }

        long offset = u32(data, tiff + 4, bigEndian);
        if (offset < 8 || tiff + offset + 2 > end) {
            return 1;
        }

        int directory = tiff + (int) offset;
        int entries = u16(data, directory, bigEndian);
        for (int i = 0; i < entries; i++) {
            int entry = directory + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }

            if (u16(data, entry, bigEndian) == TAG_ORIENTATION) {
                if (u16(data, entry + 2, bigEndian) != TYPE_SHORT) {
                    return 1;
                }

                // A SHORT value is left-aligned in the four-byte value field.
                int value = u16(data, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }

        return 1;
    }

    private static int u8(byte[] data, int pos) {
        return data[pos] & 0xFF;
    }

    private static int u16(byte[] data, int pos, boolean bigEndian) {
        return bigEndian
                ? (u8(data, pos) << 8) | u8(data, pos + 1)
                : u8(data, pos) | (u8(data, pos + 1) << 8);
    }

    private static long u32(byte[] data, int pos, boolean bigEndian) {
        return bigEndian
                ? ((long) u16(data, pos, true) << 16) | u16(data, pos + 2, true)
                : u16(data, pos, false) | ((long) u16(data, pos + 2, false) << 16);
    }

    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        boolean swap = orientation >= 5;
        int[] source = image.getRGB(0, 0, width, height, null, 0, width);
        int targetWidth = swap ? height : width;
        int[] target = new int[source.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int tx;
                int ty;
                switch (orientation) {
                    case 2 -> { tx = width - 1 - x;  ty = y; }
                    case 3 -> { tx = width - 1 - x;  ty = height - 1 - y; }
                    case 4 -> { tx = x;              ty = height - 1 - y; }
                    case 5 -> { tx = y;              ty = x; }
                    case 6 -> { tx = height - 1 - y; ty = x; }
                    case 7 -> { tx = height - 1 - y; ty = width - 1 - x; }
                    default -> { tx = y;             ty = width - 1 - x; }
                }
                target[ty * targetWidth + tx] = source[y * width + x];
            }
        }

        var result = new BufferedImage(targetWidth, swap ? width : height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, targetWidth, swap ? width : height, target, 0, targetWidth);

        return result;
    }

    // Halving step by step is noticeably smoother than one bicubic step from a large image.
    private static BufferedImage square(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        BufferedImage current = image.getSubimage(
                (image.getWidth() - side) / 2,
                (image.getHeight() - side) / 2,
                side, side);

        while (side / 2 >= SIZE) {
            side /= 2;
            current = scale(current, side);
        }

        return scale(current, SIZE);
    }

    // JPEG has no alpha, so transparency is flattened onto white.
    private static BufferedImage scale(BufferedImage image, int size) {
        var canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }

        return canvas;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var output = new ByteArrayOutputStream();
             ImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();

            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode avatar", e);
        } finally {
            writer.dispose();
        }
    }
}
