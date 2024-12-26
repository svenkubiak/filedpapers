package utils.io;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;

public class Exporter {
    public void export(List<Leaf> bookmarks, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
            writer.write("<!-- This is an automatically generated file.\n");
            writer.write("     It will be read and overwritten.\n");
            writer.write("     DO NOT EDIT! -->\n");
            writer.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
            writer.write("<TITLE>Bookmarks</TITLE>\n");
            writer.write("<H1>Bookmarks</H1>\n");
            writer.write("<DL><p>\n");

            // Export all bookmarks
            exportBookmarks(bookmarks, writer, 1);

            writer.write("</DL><p>\n");
        }
    }

    private void exportBookmarks(List<Leaf> bookmarks, FileWriter writer, int indent) throws IOException {
        String indentation = "    ".repeat(indent);

        for (Leaf bookmark : bookmarks) {
            writer.write(indentation + "<DT>");

            if (bookmark.isFolder()) {
                // Write folder
                writer.write("<H3");
                if (bookmark.getAddDate() != null) {
                    writer.write(" ADD_DATE=\"" +
                            bookmark.getAddDate().atZone(ZoneOffset.UTC).toEpochSecond() + "\"");
                }
                if (bookmark.getLastModified() != null) {
                    writer.write(" LAST_MODIFIED=\"" +
                            bookmark.getLastModified().atZone(ZoneOffset.UTC).toEpochSecond() + "\"");
                }
                if (bookmark.getDataCover() != null) {
                    writer.write(" DATA-COVER=\"" + bookmark.getDataCover() + "\"");
                }
                writer.write(">" + escapeHtml(bookmark.getTitle()) + "</H3>\n");

                // Write folder contents
                writer.write(indentation + "<DL><p>\n");
                exportBookmarks(bookmark.getChildren(), writer, indent + 1);
                writer.write(indentation + "</DL><p>\n");
            } else {
                // Write bookmark
                writer.write("<A HREF=\"" + bookmark.getUrl() + "\"");
                if (bookmark.getAddDate() != null) {
                    writer.write(" ADD_DATE=\"" +
                            bookmark.getAddDate().atZone(ZoneOffset.UTC).toEpochSecond() + "\"");
                }
                if (bookmark.getLastModified() != null) {
                    writer.write(" LAST_MODIFIED=\"" +
                            bookmark.getLastModified().atZone(ZoneOffset.UTC).toEpochSecond() + "\"");
                }
                if (bookmark.getDataCover() != null) {
                    writer.write(" DATA-COVER=\"" + bookmark.getDataCover() + "\"");
                }
                writer.write(">" + escapeHtml(bookmark.getTitle()) + "</A>\n");
            }
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    }
