package utils;

import io.mangoo.test.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.preview.LinkPreview;
import utils.preview.LinkPreviewFetcher;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith({TestRunner.class})
public class PreviewTests {

    @Test
    public void testPreview1() throws IOException {
        //when
        LinkPreview preview = LinkPreviewFetcher.fetch("https://www.youtube.com/watch?v=jE0Q8zIrXwU");

        //then
        assertThat(preview.title(), equalTo("The Silence Was So Loud"));
        assertThat(preview.imageUrl(), equalTo("https://i.ytimg.com/vi/jE0Q8zIrXwU/maxresdefault.jpg"));
    }

    @Test
    public void testPreview2() throws IOException {
        //when
        LinkPreview preview = LinkPreviewFetcher.fetch("https://www.mariowiki.com/Mario_Kart_8_Deluxe#Drivers.27_statistics");

        //then
        assertThat(preview.title(), equalTo("Mario Kart 8 Deluxe"));
        assertThat(preview.imageUrl(), equalTo("https://mario.wiki.gallery/images/thumb/9/9b/MK8_Deluxe_-_Box_NA.png/250px-MK8_Deluxe_-_Box_NA.png"));
    }
}
