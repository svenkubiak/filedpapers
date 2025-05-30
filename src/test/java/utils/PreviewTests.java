package utils;

import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
@ExtendWith({TestRunner.class})
public class PreviewTests {
    private static final String IMAGE_NAME = System.getProperty("metadata.service.image", "metascraper:latest");

    @Container
    private static final GenericContainer<?> metadataService = new GenericContainer<>(IMAGE_NAME)
            .withExposedPorts(3000);

    @BeforeAll
    public static void setUp() {
        var url = String.format("http://%s:%d",
                metadataService.getHost(),
                metadataService.getMappedPort(3000));

        System.setProperty("application.metascraper.url", url);
    }

    @Test
    public void testPreview1() throws IOException, URISyntaxException {
        //when
        LinkPreview preview = LinkPreviewFetcher.fetch("https://www.youtube.com/watch?v=jE0Q8zIrXwU", "en");

        //then
        assertThat(preview.title(), equalTo("The Silence Was So Loud"));
        assertThat(preview.image(), equalTo("https://i.ytimg.com/vi/jE0Q8zIrXwU/maxresdefault.jpg"));
    }

    @Test
    public void testPreview2() throws IOException, URISyntaxException {
        //when
        LinkPreview preview = LinkPreviewFetcher.fetch("https://www.mariowiki.com/Mario_Kart_8_Deluxe#Drivers.27_statistics", "en");

        //then
        assertThat(preview.title(), equalTo("Mario Kart 8 Deluxe"));
        assertThat(preview.image(), equalTo("https://mario.wiki.gallery/images/thumb/9/9b/MK8_Deluxe_-_Box_NA.png/250px-MK8_Deluxe_-_Box_NA.png"));
    }

    @Test
    public void testPreview3() throws IOException, URISyntaxException {
        //when
        LinkPreview preview = LinkPreviewFetcher.fetch("https://www.google.de/maps/place/Al-lord+Arabische+S%C3%BC%C3%9Figkeiten/@51.5212403,7.1043508,5547m/data=!3m2!1e3!5s0x47b8e6fe6ebb7f5d:0xb4201f4ae33f6cd2!4m6!3m5!1s0x47b8e7cdd297860d:0xe80458e9d78a93f4!8m2!3d51.5129816!4d7.0943446!16s%2Fg%2F11j33m9_3d?entry=ttu&g_ep=EgoyMDI1MDUyNy4wIKXMDSoASAFQAw%3D%3D", "en");

        //then
        assertThat(preview.title(), equalTo("Al-lord Arabische Süßigkeiten · Hansemannstraße 23, 45879 Gelsenkirchen"));
        assertThat(preview.image(), equalTo("https://lh3.googleusercontent.com/p/AF1QipMhSZ68K3lODLzmFL0arjx5fwh0KsizwUjLZGOz=w900-h900-p-k-no"));
    }
}
