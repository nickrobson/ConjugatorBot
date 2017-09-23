package xyz.nickr.telegram.conjugatorbot.extractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Nick Robson
 */
public class SvExtractor implements Extractor {

    public static final String URL_FORMAT = "https://sv.wiktionary.org/wiki/%s";

    @Override
    public ExtractResult[] extract(String searchTerm) throws IOException {
        String[] search = searchTerm.split("#");
        String searchType = search.length > 1 ? search[1] : null;

        Elements elements = this.loadWikipediaPage(String.format(URL_FORMAT, search[0]), "Svenska");
        List<ExtractResult> resultList = new ArrayList<>();
        BufferedImage img = null;
        String caption = null;
        int definition = 1;
        for (Element element : elements) {
            if ("h3".equals(element.tagName())) {
                if (img != null) {
                    resultList.add(new ExtractResult(img, caption));
                    img = null;
                    definition = 1;
                }
                String type = element.getElementsByClass("mw-headline").first().text();
                if (searchType == null || searchType.equalsIgnoreCase(type)) {
                    caption = type;
                } else {
                    caption = null;
                }
            }
            if (caption != null) {
                if ("ol".equals(element.tagName())) {
                    for (Element li : element.children()) {
                        Element firstChild = li.child(0);
                        if ("i".equals(firstChild.tagName()) && "böjningsform av".equals(firstChild.text())) {
                            return extract(li.child(1).attr("href").substring(6));
                        }
                        li.getElementsByTag("dl").remove();
                        caption += "\n\n" + definition + ". ";
                        caption += li.text();
                        definition++;
                    }
                }
                if ("table".equals(element.tagName())) {
                    img = toImage(element);
                }
            }
        }
        if (img != null)
            resultList.add(new ExtractResult(img, caption));
        return resultList.toArray(new ExtractResult[0]);
    }

}
