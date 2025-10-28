package utils.io;

import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

public final class IOUtils {
    public static final int MAX_ELEMENTS = 10000;
    private static final long MAX_CONTENT_LENGTH = 5L * 1024 * 1024; // 5MB for content
    private static final int MAX_BOOKMARKS_PER_IMPORT = 1000;

    private IOUtils() {
    }

    public static String exportItems(List<Leaf> bookmarks) {
        var buffer = new StringBuilder();
        buffer.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
        buffer.append("<!-- This is an automatically generated file.\n");
        buffer.append("     It will be read and overwritten.\n");
        buffer.append("     DO NOT EDIT! -->\n");
        buffer.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
        buffer.append("<TITLE>Bookmarks</TITLE>\n");
        buffer.append("<H1>Bookmarks</H1>\n");
        buffer.append("<DL><p>\n");
        exportBookmarks(bookmarks, 1, buffer);
        buffer.append("</DL><p>\n");

        return buffer.toString();
    }

    private static void exportBookmarks(List<Leaf> bookmarks, int indent, StringBuilder buffer) {
        String indentation = "    ".repeat(indent);

        for (Leaf bookmark : bookmarks) {
            buffer.append(indentation).append("<DT>");

            if (bookmark.isFolder()) {
                buffer.append("<H3");
                if (bookmark.getAddDate() != null) {
                    buffer.append(" ADD_DATE=\"")
                            .append(bookmark.getAddDate().atZone(ZoneOffset.UTC).toEpochSecond())
                            .append("\"");
                }
                if (bookmark.getLastModified() != null) {
                    buffer.append(" LAST_MODIFIED=\"")
                            .append(bookmark.getLastModified().atZone(ZoneOffset.UTC).toEpochSecond()).
                            append("\"");
                }
                if (bookmark.getDataCover() != null) {
                    buffer.append(" DATA-COVER=\"")
                            .append(bookmark.getDataCover())
                            .append("\"");
                }
                buffer.append(">")
                        .append(escapeHtml(bookmark.getTitle()))
                        .append("</H3>\n");

                buffer.append(indentation).append("<DL><p>\n");
                exportBookmarks(bookmark.getChildren(), indent + 1, buffer);
                buffer.append(indentation).append("</DL><p>\n");
            } else {
                buffer.append("<A HREF=\"")
                        .append(bookmark.getUrl())
                        .append("\"");

                if (bookmark.getAddDate() != null) {
                    buffer.append(" ADD_DATE=\"")
                            .append(bookmark.getAddDate().atZone(ZoneOffset.UTC).toEpochSecond())
                            .append("\"");
                }
                if (bookmark.getLastModified() != null) {
                    buffer.append(" LAST_MODIFIED=\"")
                            .append(bookmark.getLastModified().atZone(ZoneOffset.UTC).toEpochSecond())
                            .append("\"");
                }
                if (bookmark.getDataCover() != null) {
                    buffer.append(" DATA-COVER=\"")
                            .append(bookmark.getDataCover())
                            .append("\"");
                }
                buffer.append(">")
                        .append(escapeHtml(bookmark.getTitle()))
                        .append("</A>\n");
            }
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static List<Leaf> importItems(String input) {
        // 1. Input length validation
        if (input == null || input.length() > MAX_CONTENT_LENGTH) {
            throw new SecurityException("Content too large");
        }

        // 2. Basic HTML structure validation
        if (!input.contains("<") || !input.contains(">")) {
            throw new SecurityException("Invalid HTML content");
        }

        // 3. Check for potentially dangerous content while preserving bookmark structure
        if (containsDangerousContent(input)) {
            throw new SecurityException("Content contains potentially dangerous elements");
        }

        Document doc = Jsoup.parse(input, StandardCharsets.UTF_8.name());

        // 4. Limit the number of elements to prevent DoS
        if (doc.getAllElements().size() > MAX_ELEMENTS) {
            throw new SecurityException("Too many HTML elements");
        }

        // 5. Validate bookmark-specific structure
        validateBookmarkStructure(doc);

        // The root bookmark that will contain all others
        var root = new Leaf();
        root.setFolder(true);
        root.setTitle("Root");

        // Process all DL elements (bookmark folders)
        var dls = doc.getElementsByTag("dl");
        if (!dls.isEmpty()) {
            processDL(Objects.requireNonNull(dls.first()), root);
        }

        return root.getChildren();
    }

    public static String readContent(InputStream file) {
        try (file) {
            return org.apache.commons.io.IOUtils.toString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            //Intentionally left blank
        }

        return Strings.EMPTY;
    }

    private static boolean containsDangerousContent(String input) {
        String lowerInput = input.toLowerCase();

        return lowerInput.contains("<script") ||
                lowerInput.contains("javascript:") ||
                lowerInput.contains("vbscript:") ||
                lowerInput.contains("onload=") ||
                lowerInput.contains("onerror=") ||
                lowerInput.contains("onclick=") ||
                lowerInput.contains("onmouseover=") ||
                lowerInput.contains("onfocus=") ||
                lowerInput.contains("onblur=") ||
                lowerInput.contains("onchange=") ||
                lowerInput.contains("onsubmit=");
    }

    private static void validateBookmarkStructure(Document doc) {
        Elements dls = doc.getElementsByTag("dl");
        Elements links = doc.getElementsByTag("a");

        if (dls.isEmpty() && links.isEmpty()) {
            throw new SecurityException("No bookmark structure found");
        }

        if (links.size() > MAX_BOOKMARKS_PER_IMPORT) {
            throw new SecurityException("Too many bookmarks");
        }
    }

    private static void processDL(Element dl, Leaf parent) {
        Elements items = dl.children();

        Leaf currentFolder = null;

        for (Element item : items) {
            if (item.is("dt")) {
                Element firstChild = item.children().first();

                if (firstChild != null) {
                    if (firstChild.is("h3")) {
                        // This is a folder
                        currentFolder = new Leaf();
                        currentFolder.setFolder(true);
                        currentFolder.setTitle(firstChild.text());

                        // Parse dates if available
                        String addDate = firstChild.attr("add_date");
                        if (!addDate.isEmpty()) {
                            currentFolder.setAddDate(Instant.ofEpochSecond(Long.parseLong(addDate)));
                        }

                        String lastModified = firstChild.attr("last_modified");
                        if (!lastModified.isEmpty()) {
                            currentFolder.setLastModified(Instant.ofEpochSecond(Long.parseLong(lastModified)));
                        }

                        // Parse data-cover if available
                        String dataCover = firstChild.attr("data-cover");
                        if (!dataCover.isEmpty()) {
                            currentFolder.setDataCover(dataCover);
                        }

                        parent.addChild(currentFolder);

                        // Process nested DL if it exists
                        Element nextDL = item.getElementsByTag("dl").first();
                        if (nextDL != null) {
                            processDL(nextDL, currentFolder);
                        }
                    } else if (firstChild.is("a")) {
                        // This is a bookmark
                        var bookmark = new Leaf();
                        bookmark.setFolder(false);
                        bookmark.setTitle(firstChild.text());
                        bookmark.setUrl(firstChild.attr("href"));

                        // Parse dates if available
                        String addDate = firstChild.attr("add_date");
                        if (!addDate.isEmpty()) {
                            bookmark.setAddDate(Instant.ofEpochSecond(Long.parseLong(addDate)));
                        }

                        String lastModified = firstChild.attr("last_modified");
                        if (!lastModified.isEmpty()) {
                            bookmark.setLastModified(Instant.ofEpochSecond(Long.parseLong(lastModified)));
                        }

                        // Parse data-cover if available
                        String dataCover = firstChild.attr("data-cover");
                        if (!dataCover.isEmpty()) {
                            bookmark.setDataCover(dataCover);
                        }

                        parent.addChild(bookmark);
                    }
                }
            }
        }
    }
}
