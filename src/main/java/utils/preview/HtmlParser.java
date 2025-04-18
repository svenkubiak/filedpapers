package utils.preview;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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
                        return Optional.of(new URL(href));
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
                        return Optional.of(new URL(content));
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
        // Try Open Graph image first
        Element ogImageElement = document.select("meta[property=og:image]").first();
        if (ogImageElement != null) {
            String ogImageUrl = ogImageElement.attr("content");
            if (ogImageUrl.isEmpty()) {
                return Optional.empty();
            }
            
            String resolvedUrl;
            try {
                // Always resolve URL against baseUrl to handle relative paths
                resolvedUrl = new URL(baseUrl, ogImageUrl).toString();
                
                // Validate image dimensions and size
                ImageDimensions dimensions = getImageDimensions(resolvedUrl);
                if (dimensions != null) {
                    return Optional.of(resolvedUrl);
                }
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        }

        // Try Twitter Card image second
        Optional<String> twitterImage = Optional.ofNullable(document.select("meta[name=twitter:image]").first())
                .map(element -> element.attr("content"))
                .filter(content -> !content.isEmpty())
                .map(content -> {
                    try {
                        return new URL(baseUrl, content).toString();
                    } catch (MalformedURLException e) {
                        return null;
                    }
                })
                .filter(url -> {
                    ImageDimensions dimensions = getImageDimensions(url);
                    return dimensions != null;
                });

        if (twitterImage.isPresent()) {
            return twitterImage;
        }

        // Try image_src link third
        Optional<String> imageSrc = Optional.ofNullable(document.select("link[rel=image_src]").first())
                .map(element -> element.attr("href"))
                .filter(href -> !href.isEmpty())
                .map(href -> {
                    try {
                        return new URL(baseUrl, href).toString();
                    } catch (MalformedURLException e) {
                        return null;
                    }
                })
                .filter(url -> {
                    ImageDimensions dimensions = getImageDimensions(url);
                    return dimensions != null;
                });

        if (imageSrc.isPresent()) {
            return imageSrc;
        }

        // Try to find images in the document body
        // Look for absolute URLs in img tags, limit to first 5
        return document.select("img").stream()
                .limit(5)
                .map(element -> element.attr("src"))
                .filter(src -> src.startsWith("http://") || src.startsWith("https://"))
                .filter(url -> {
                    ImageDimensions dimensions = getImageDimensions(url);
                    return dimensions != null;
                })
                .findFirst();
    }

    /**
     * Checks if an image is within the maximum size limit
     *
     * @param imageUrl The URL of the image to check
     * @return true if the image is within the size limit, false otherwise
     */
    private static boolean isImageWithinSizeLimit(String imageUrl) {
        ImageDimensions dimensions = getImageDimensions(imageUrl);
        if (dimensions == null) {
            return false;
        }

        // Check minimum dimensions (50px as mentioned in the article)
        if (dimensions.width() <= 50 || dimensions.height() <= 50) {
            return false;
        }

        // Check aspect ratio (should not be greater than 3:1)
        double aspectRatio = dimensions.width() > dimensions.height() 
            ? (double) dimensions.width() / dimensions.height()
            : (double) dimensions.height() / dimensions.width();
            
        return aspectRatio <= 3.0;
    }

    private static Optional<String> findBestImage(Document document, URL baseUrl) {
        try {
            Elements imgElements = document.select("img");
            if (imgElements.isEmpty()) {
                return Optional.empty();
            }

            // Filter and transform image elements
            List<ImageCandidate> imageCandidates = imgElements.stream()
                    .map(img -> {
                        try {
                            String src = img.attr("src");
                            if (src.isEmpty()) {
                                return null;
                            }

                            // Resolve URL
                            String resolvedUrl = resolveUrl(src, baseUrl);
                            if (resolvedUrl == null) {
                                return null;
                            }

                            // Try to get actual image dimensions
                            ImageDimensions dimensions = getImageDimensions(resolvedUrl);
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

            if (imageCandidates.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(imageCandidates.getFirst().url());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get the actual dimensions of an image by reading its metadata
     *
     * @param imageUrl The URL of the image
     * @return The dimensions of the image, or null if they couldn't be determined
     */
    private static ImageDimensions getImageDimensions(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            
            // Add consistent headers
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Connection", "keep-alive");

            // Check if the URL returns an image content type
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return null;
            }

            // Check content length (max 1MB)
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 1024 * 1024) { // 1MB
                return null;
            }

            // If we can't process the image, return default dimensions
            // This allows the image to be used even if we can't verify its dimensions
            return new ImageDimensions(250, 250);
        } catch (Exception e) {
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
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            // If URL is already absolute, return it
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }

            // Use Java's built-in URL resolution
            return new URL(baseUrl, url).toString();
        } catch (MalformedURLException e) {
            return url; // Return original URL if resolution fails
        }
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