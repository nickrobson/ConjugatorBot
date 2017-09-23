package xyz.nickr.telegram.conjugatorbot.extractor;

import gui.ava.html.image.generator.HtmlImageGenerator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Nick Robson
 */
public interface Extractor {

    ExtractResult[] extract(String searchTerm) throws IOException;

    default Elements loadWikipediaPage(String url, String language) throws IOException {
        Document document = Jsoup.parse(new URL(url), 1000);
        Elements content = document.select("#mw-content-text .mw-parser-output").first().children();
        boolean valid = false;
        Elements elements = new Elements();
        for (Element element : content) {
            if ("h2".equals(element.tagName())) {
                valid = language.equals(element.child(0).text());
            }
            if (valid) {
                elements.add(element);
            }
        }
        return elements;
    }

    default BufferedImage toImage(Element table) {
        HtmlImageGenerator generator = new HtmlImageGenerator();
        generator.loadHtml(table.toString());
        return generator.getBufferedImage();
    }

    class ExtractResult {

        public BufferedImage img;
        public String caption;

        public ExtractResult() {}

        public ExtractResult(BufferedImage img, String caption) {
            this.img = img;
            this.caption = caption;
        }

    }

}
