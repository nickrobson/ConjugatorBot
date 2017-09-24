package xyz.nickr.telegram.conjugatorbot.extractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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

        String wikiUrl = String.format(URL_FORMAT, search[0]);
        Elements elements = this.loadWikipediaPage(wikiUrl, "Dansk");
        List<ExtractResult> resultList = new ArrayList<>();

        String type = null;
        int definition = 1;
        LinkedList<String> captions = new LinkedList<>();
        LinkedList<BufferedImage> tables = new LinkedList<>();
        for (Element element : elements) {
            if (element.tagName().equals("h3")) {
                definition = 1;
                type = element.getElementsByClass("mw-headline").first().text();
                if (type.equalsIgnoreCase("kilder") || (searchType != null && !searchType.equalsIgnoreCase(type)))
                    type = null;
                else
                    while (captions.size() != tables.size())
                        (captions.size() < tables.size() ? captions : tables).add(null);
            } else if (type != null) {
                if (element.tagName().equalsIgnoreCase("p") && element.child(0).text().equalsIgnoreCase(search[0])) {
                    String caption = type + " (" + element.text() + ")";
                    String oldCaption = caption;
                    Element ol = element.nextElementSibling();
                    if (ol.tagName().equals("ul")) {
                        for (Element li : ol.getElementsByTag("li")) {
                            caption += "\n" + li.text();
                        }
                        ol = ol.nextElementSibling();
                    }
                    if (ol.tagName().equals("ol")) {
                        for (Element li : ol.getElementsByTag("li")) {
                            li.getElementsByTag("dl").remove();
                            caption += "\n\n" + definition + ". ";
                            caption += li.text();
                            definition++;
                        }
                    }
                    if (!oldCaption.equals(caption)) {
                        String url = "\n" + wikiUrl;
                        if (caption.length() + url.length() > 200) {
                            caption = caption.substring(0, 199 - url.length()) + "…" + url;
                        }
                        captions.add(caption);
                    }
                }
                if (element.tagName().equalsIgnoreCase("h4") && element.text().equalsIgnoreCase("bøjning")) {
                    Element table = element.nextElementSibling();
                    if (table.tagName().equals("table")) {
                        tables.add(toImage(table));
                    }
                }
                if (element.tagName().equalsIgnoreCase("h4") && element.text().equalsIgnoreCase("oversættelser")) {
                    if (tables.size() < captions.size()) {
                        tables.add(null);
                    } else if (tables.size() > captions.size()) {
                        captions.add(null);
                    }
                }
            }

            while (!captions.isEmpty() && !tables.isEmpty()) {
                ExtractResult result = new ExtractResult();
                result.caption = captions.pollFirst();
                result.img = tables.pollFirst();
                resultList.add(result);
                definition = 1;
            }
        }
        return resultList.toArray(new ExtractResult[0]);
    }

}
