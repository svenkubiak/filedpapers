package utils.io;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class Importer {

    public List<Leaf> parse(String input) throws IOException {
        Document doc = Jsoup.parse(input, "UTF-8");

        // The root bookmark that will contain all others
        Leaf root = new Leaf();
        root.setFolder(true);
        root.setTitle("Root");

        // Process all DL elements (bookmark folders)
        Elements dls = doc.getElementsByTag("dl");
        if (!dls.isEmpty()) {
            processDL(dls.first(), root);
        }

        return root.getChildren();
    }

    private void processDL(Element dl, Leaf parent) {
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
                        Leaf bookmark = new Leaf();
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