package utils.preview;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class HtmlParser {
    /**
     * Extracts the title from HTML document with fallback options:
     * 1. Open Graph title (og:title)
     * 2. Twitter Card title
     * 3. Meta title
     * 4. H1 tag
     * 5. H2 tag
     *
     * @param document The parsed HTML document
     * @return Optional containing the title if found
     */
    public static Optional<String> extractTitle(Document document) {
        // Try Open Graph title
        Optional<String> ogTitle = Optional.ofNullable(document.select("meta[property=og:title]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty());
        if (ogTitle.isPresent()) {
            return ogTitle;
        }

        // Try Twitter Card title
        Optional<String> twitterTitle = Optional.ofNullable(document.select("meta[name=twitter:title]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty());
        if (twitterTitle.isPresent()) {
            return twitterTitle;
        }

        // Try meta title tag
        Optional<String> metaTitle = Optional.of(document.title())
                .filter(title -> !title.isEmpty());
        if (metaTitle.isPresent()) {
            return metaTitle;
        }

        // Try h1 tag
        Optional<String> h1Title = Optional.ofNullable(document.select("h1").first())
                .map(Element::text)
                .filter(text -> !text.isEmpty());
        if (h1Title.isPresent()) {
            return h1Title;
        }

        // Try h2 tag
        return Optional.ofNullable(document.select("h2").first())
                .map(Element::text)
                .filter(text -> !text.isEmpty());
    }

    /**
     * Extracts the description from HTML document with fallback options:
     * 1. Open Graph description
     * 2. Twitter Card description
     * 3. Meta description
     * 4. First visible paragraph
     *
     * @param document The parsed HTML document
     * @return Optional containing the description if found
     */
    public static Optional<String> extractDescription(Document document) {
        // Try Open Graph description
        Optional<String> ogDescription = Optional.ofNullable(document.select("meta[property=og:description]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty());
        if (ogDescription.isPresent()) {
            return ogDescription;
        }

        // Try Twitter Card description
        Optional<String> twitterDescription = Optional.ofNullable(document.select("meta[name=twitter:description]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty());
        if (twitterDescription.isPresent()) {
            return twitterDescription;
        }

        // Try meta description
        Optional<String> metaDescription = Optional.ofNullable(document.select("meta[name=description]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty());
        if (metaDescription.isPresent()) {
            return metaDescription;
        }

        // Try first paragraph with content
        return document.select("p")
                .stream()
                .map(Element::text)
                .filter(text -> !text.isEmpty())
                .findFirst();
    }

    /**
     * Extracts the domain name from HTML document with fallback options:
     * 1. Canonical link
     * 2. Open Graph URL
     * 3. Original URL
     *
     * @param document The parsed HTML document
     * @param originalUrl The original URL as fallback
     * @return The domain name
     */
    public static String extractDomainName(Document document, URL originalUrl) {
        // Try canonical link
        Optional<URL> canonicalUrl = Optional.ofNullable(document.select("link[rel=canonical]").first())
                .map(element -> element.attr("href"))
                .filter(href -> !href.isEmpty())
                .flatMap(href -> {
                    try {
                        return Optional.of(URI.create(href).toURL());
                    } catch (MalformedURLException e) {
                        return Optional.empty();
                    }
                });

        if (canonicalUrl.isPresent()) {
            return getDomainName(canonicalUrl.orElseThrow());
        }

        // Try Open Graph URL
        Optional<URL> ogUrl = Optional.ofNullable(document.select("meta[property=og:url]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty())
                .flatMap(content -> {
                    try {
                        return Optional.of(URI.create(content).toURL());
                    } catch (MalformedURLException e) {
                        return Optional.empty();
                    }
                });

        if (ogUrl.isPresent()) {
            return getDomainName(ogUrl.orElseThrow());
        }

        // Use original URL as fallback
        return getDomainName(originalUrl);
    }

    private static String getDomainName(URL url) {
        String host = url.getHost();
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * Extracts image URL from HTML document with fallback options:
     * 1. Open Graph image
     * 2. Twitter Card image
     * 3. Image_src link
     * 4. Best image from document body
     * Only images below MAX_IMAGE_SIZE will be considered.
     *
     * @param document The parsed HTML document
     * @param baseUrl The base URL for resolving relative URLs
     * @return Optional containing the image URL if found and within size limit
     */
    public static Optional<String> extractImageUrl(Document document, URL baseUrl) {
        // Try Open Graph image
        Optional<String> ogImage = Optional.ofNullable(document.select("meta[property=og:image]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty())
                .map(content -> resolveUrl(content, baseUrl));

        if (ogImage.isPresent()) {
            String imageUrl = ogImage.orElseThrow();
            ImageDimensions dimensions = getImageDimensions(imageUrl);
            if (dimensions != null) {
                return Optional.of(imageUrl);
            }
        }

        // Try Twitter Card image
        Optional<String> twitterImage = Optional.ofNullable(document.select("meta[name=twitter:image]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty())
                .map(content -> resolveUrl(content, baseUrl));

        if (twitterImage.isPresent()) {
            String imageUrl = twitterImage.orElseThrow();
            ImageDimensions dimensions = getImageDimensions(imageUrl);
            if (dimensions != null) {
                return Optional.of(imageUrl);
            }
        }

        // Try image_src link
        Optional<String> imageSrc = Optional.ofNullable(document.select("link[rel=image_src]").first())
                .map(element -> element.attr("href"))
                .filter(href -> !href.isEmpty())
                .map(href -> resolveUrl(href, baseUrl));

        if (imageSrc.isPresent()) {
            String imageUrl = imageSrc.orElseThrow();
            ImageDimensions dimensions = getImageDimensions(imageUrl);
            if (dimensions != null) {
                return Optional.of(imageUrl);
            }
        }

        // Find best image from document body
        Elements imgElements = document.select("img");

        // Return first valid image found
        for (Element img : imgElements) {
            String src = img.attr("src");
            if (src.isEmpty()) {
                continue;
            }

            String resolvedUrl = resolveUrl(src, baseUrl);

            try {
                ImageDimensions dimensions = getImageDimensions(resolvedUrl);
                if (dimensions != null) {
                    return Optional.of(resolvedUrl);
                }
            } catch (Exception e) {
                // Ignore errors and continue with next image
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if an image is within the maximum size limit
     *
     * @param imageUrl The URL of the image to check
     * @return true if the image is within the size limit, false otherwise
     */
    private static boolean isImageWithinSizeLimit(String imageUrl) {
        try {
            URL url = URI.create(imageUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return false;
            }

            String contentType = connection.getContentType();
            return contentType != null && contentType.startsWith("image/");
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<String> findBestImage(Document document, URL baseUrl) {
        Elements imgElements = document.select("img");

        // Filter and transform image elements
        List<ImageCandidate> imageCandidates = imgElements.stream()
                .map(img -> {
                    String src = img.attr("src");
                    if (src.isEmpty()) {
                        return null;
                    }

                    // Resolve URL
                    String resolvedUrl = resolveUrl(src, baseUrl);

                    try {
                        // Try to get actual image dimensions
                        ImageDimensions dimensions = getImageDimensions(resolvedUrl);

                        // If we couldn't get actual dimensions, skip this image
                        if (dimensions == null) {
                            return null;
                        }

                        // Calculate area for sorting
                        int area = dimensions.width() * dimensions.height();
                        return new ImageCandidate(resolvedUrl, area);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .sorted((img1, img2) -> Integer.compare(img2.area(), img1.area())) // Sort by area (largest first)
                .toList();

        return imageCandidates.stream()
                .findFirst()
                .map(ImageCandidate::url);
    }

    /**
     * Get the actual dimensions of an image by reading its metadata
     *
     * @param imageUrl The URL of the image
     * @return The dimensions of the image, or null if they couldn't be determined
     */
    private static ImageDimensions getImageDimensions(String imageUrl) {
        try {
            URL url = URI.create(imageUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Check if the URL returns an image content type
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return null;
            }

            // Read the image to get actual dimensions
            try (ImageInputStream in = ImageIO.createImageInputStream(connection.getInputStream())) {
                if (in == null) {
                    return null;
                }

                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) {
                    return null;
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    // Validate minimum dimensions (from article)
                    if (width < 50 || height < 50) {
                        return null;
                    }

                    // Check aspect ratio (from article)
                    double aspectRatio = width > height ?
                            (double) width / height :
                            (double) height / width;
                    if (aspectRatio > 3.0) {
                        return null;
                    }

                    // Check if area exceeds limit (500x500 = 250,000)
                    if (width * height > 250000) {
                        return null;
                    }

                    return new ImageDimensions(width, height);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static int parseIntAttribute(Element element, String attributeName) {
        try {
            return Integer.parseInt(element.attr(attributeName));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolveUrl(String url, URL baseUrl) {
        if (url.startsWith("//")) {
            return baseUrl.getProtocol() + ":" + url;
        } else if (url.startsWith("/")) {
            return baseUrl.getProtocol() + "://" + baseUrl.getHost() + url;
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return baseUrl.getProtocol() + "://" + baseUrl.getHost() + "/" + url;
        }
        return url;
    }

    /**
     * Record to hold image candidate information for sorting
     */
    private record ImageCandidate(String url, int area) {}

    /**
     * Record to hold image dimensions
     */
    private record ImageDimensions(int width, int height) {}
} 