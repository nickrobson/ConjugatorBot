package xyz.nickr.telegram.conjugatorbot.extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Nick Robson
 */
public class DaExtractor implements Extractor {

    public static final String URL_FORMAT = "https://da.wiktionary.org/wiki/%s";

    @Override
    public ExtractResult[] extract(String searchTerm) throws IOException {
        String[] search = searchTerm.split("#");
        String searchType = search.length > 1 ? search[1] : null;

        Elements elements = this.loadWikipediaPage(String.format(URL_FORMAT, search[0]), "Dansk");
        List<ExtractResult> resultList = new ArrayList<>();
        ExtractResult result = new ExtractResult();

        String caption = null;
        int definition = 1;
        for (Element element : elements) {
            boolean finished = false;
            if (element.tagName().equals("h3")) {
                caption = element.getElementsByClass("mw-headline").first().text();
                if (caption.equalsIgnoreCase("kilder") || (searchType != null && !searchType.equalsIgnoreCase(caption)))
                    caption = null;
                continue;
            }

            if (caption != null) {
                if (element.tagName().equalsIgnoreCase("p") && element.child(0).text().equalsIgnoreCase(search[0])) {
                    Element ol = element.nextElementSibling();
                    if (ol.tagName().equals("ol")) {
                        for (Element li : ol.getElementsByTag("li")) {
                            caption += "\n\n" + definition + ". ";
                            caption += li.text();
                            definition++;
                        }
                    }
                    result.caption = caption;
                }
                if (element.tagName().equalsIgnoreCase("h4") && element.text().equalsIgnoreCase("bøjning")) {
                    Element table = element.nextElementSibling();
                    if (table.tagName().equals("table")) {
                        result.img = toImage(table);
                    }
                }
                if (element.tagName().equalsIgnoreCase("h4") && element.text().equalsIgnoreCase("oversættelser")) {
                    finished = true;
                }
            }

            if (finished || (result.img != null && result.caption != null)) {
                resultList.add(result);
                result = new ExtractResult();
                caption = null;
                definition = 1;
            }
        }
        return resultList.toArray(new ExtractResult[0]);
    }

}
