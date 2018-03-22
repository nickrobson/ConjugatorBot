package xyz.nickr.telegram.conjugatorbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.chat.ChatMemberStatus;
import com.jtelegram.api.chat.ChatType;
import com.jtelegram.api.commands.Command;
import com.jtelegram.api.commands.filters.MentionFilter;
import com.jtelegram.api.commands.filters.TextFilter;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.events.message.TextMessageEvent;
import com.jtelegram.api.inline.input.InputTextMessageContent;
import com.jtelegram.api.inline.result.InlineResultArticle;
import com.jtelegram.api.inline.result.InlineResultPhoto;
import com.jtelegram.api.inline.result.framework.InlineResult;
import com.jtelegram.api.message.input.file.LocalInputFile;
import com.jtelegram.api.requests.chat.GetChatMember;
import com.jtelegram.api.requests.inline.AnswerInlineQuery;
import com.jtelegram.api.requests.message.framework.ParseMode;
import com.jtelegram.api.requests.message.send.SendPhoto;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.update.PollingUpdateProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.jsoup.HttpStatusException;
import xyz.nickr.telegram.conjugatorbot.extractor.DaExtractor;
import xyz.nickr.telegram.conjugatorbot.extractor.Extractor;
import xyz.nickr.telegram.conjugatorbot.extractor.SvExtractor;

/**
 * @author Nick Robson
 */
public class ConjugatorBot {

    private static AtomicInteger queryId = new AtomicInteger(0);
    private static TelegramBot bot;
    private static final Gson GSON = new Gson();
    private static String IMGUR_TOKEN = System.getenv("IMGUR_TOKEN");

    private static final Map<String, String> groupLanguageBindings = new LinkedHashMap<>();

    private static final Map<String, Extractor> extractors = new LinkedHashMap<String, Extractor>() {{
        put("sv", new SvExtractor());
        put("svenska", get("sv"));
        put("da", new DaExtractor());
        put("dansk", get("da"));
    }};

    public static void main(String[] args) {
        TelegramBotRegistry registry = TelegramBotRegistry.builder()
                .updateProvider(new PollingUpdateProvider())
                .build();
        registry.registerBot(System.getenv("AUTH_TOKEN"), (theBot, err) -> {
            if (err != null) {
                throw new IllegalArgumentException("Invalid bot token in env var: AUTH_TOKEN");
            }
            bot = theBot;
            bot.getCommandRegistry().registerCommand(
                    new MentionFilter(
                            new TextFilter("lookup", false, ConjugatorBot::lookupCommand),
                            new TextFilter("bind", false, ConjugatorBot::bindCommand)
                    )
            );

            try (FileReader reader = new FileReader("groups.json")) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                json.entrySet().forEach(e -> groupLanguageBindings.put(e.getKey(), e.getValue().getAsString()));
            } catch (FileNotFoundException ignored) {
                // noop
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            bot.getEventRegistry().registerEvent(InlineQueryEvent.class, ConjugatorBot::onInlineQueryReceived);
            System.out.println("Logged in as " + bot.getBotInfo().getUsername());
        });

    }

    private static boolean bindCommand(TextMessageEvent event, Command command) {
        if (event.getMessage().getChat().getType() != ChatType.PRIVATE && event.getMessage().getSender().getId() != 112972102L) {
            AtomicBoolean isAdmin = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            bot.perform(GetChatMember.builder()
                    .chatId(event.getMessage().getChat().getChatId())
                    .userId(event.getMessage().getSender().getId())
                    .callback(member -> {
                        if (member.getStatus().ordinal() <= ChatMemberStatus.ADMINISTRATOR.ordinal()) {
                            isAdmin.set(true);
                        }
                        latch.countDown();
                    })
                    .errorHandler(e -> latch.countDown())
                    .build());
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            if (!isAdmin.get()) {
                reply(event, "You're not a chat admin.", ParseMode.NONE);
                return true;
            }
        }
        List<String> args = command.getArgs();
        if (args.isEmpty()) {
            String oldLang = groupLanguageBindings.remove(event.getMessage().getChat().getChatId().toString());
            if (oldLang != null) {
                try (FileWriter writer = new FileWriter("groups.json")) {
                    GSON.toJson(groupLanguageBindings, writer);
                } catch (IOException ignored) {}
                reply(event, "Unbound from " + oldLang + "!", ParseMode.NONE);
            } else {
                reply(event, "You need to specify a language to bind to: /bind (lang)", ParseMode.NONE);
            }
        } else {
            String lang = args.get(0).toLowerCase();
            if (extractors.containsKey(lang)) {
                groupLanguageBindings.put(event.getMessage().getChat().getChatId().toString(), lang);
                try (FileWriter writer = new FileWriter("groups.json")) {
                    GSON.toJson(groupLanguageBindings, writer);
                } catch (IOException ignored) {
                }
                reply(event, "Language bound to: " + lang, ParseMode.NONE);
            } else {
                reply(event, "That language isn't supported! Try one of: " + String.join(", ", extractors.keySet()), ParseMode.NONE);
            }
        }
        return true;
    }

    private static boolean lookupCommand(TextMessageEvent event, Command command) {
        try {
            Extractor.ExtractResult[] results = lookup(event.getMessage().getChat().getChatId().toString(), command.getArgs().toArray(new String[0]));
            for (Extractor.ExtractResult result : results) {
                if (result.img != null)
                    reply(event, result.img, result.caption);
                else
                    reply(event, result.caption, ParseMode.NONE);
            }
            if (results.length == 0) {
                reply(event, "No results found!", ParseMode.NONE);
            }
        } catch (Exception ex) {
            reply(event, ex.getMessage(), ParseMode.NONE);
        }
        return true;
    }

    private static boolean onInlineQueryReceived(InlineQueryEvent event) {
        List<InlineResult> resultList = new CopyOnWriteArrayList<>();
        try {
            String[] args = event.getQuery().getQuery().split(" ");
            Extractor.ExtractResult[] results = lookup(String.valueOf(event.getQuery().getFrom().getId()), args);
            if (results.length > 0) {
                ExecutorService executor = Executors.newFixedThreadPool(results.length);
                for (Extractor.ExtractResult result : results) {
                    if (result.img != null) {
                        executor.submit(() -> {
                            try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(result.img, "png", baos);
                                URL url = Imgur.uploadSingle(IMGUR_TOKEN, "image.png", baos.toByteArray());
                                String urlString = url != null ? url.toExternalForm() : null;
                                resultList.add(InlineResultPhoto.builder()
                                        .id(Integer.toString(queryId.getAndIncrement(), 36))
                                        .thumbUrl(urlString)
                                        .url(urlString)
                                        .width(result.img.getWidth())
                                        .height(result.img.getHeight())
                                        .caption(result.caption)
                                        .description(result.caption)
                                        .build());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                    } else {
                        resultList.add(InlineResultArticle.builder()
                                .id(Integer.toString(queryId.getAndIncrement(), 36))
                                .title(result.caption)
                                .description(result.caption)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(result.caption)
                                        .build())
                                .build());
                    }
                }
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } else {
                resultList.add(InlineResultArticle.builder()
                        .id(Integer.toString(queryId.getAndIncrement(), 36))
                        .title("No results found!")
                        .description("No results found!")
                        .inputMessageContent(InputTextMessageContent.builder()
                                .messageText("No results found!")
                                .build())
                        .build());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            resultList.clear();
            resultList.add(InlineResultArticle.builder()
                    .id(Integer.toString(queryId.getAndIncrement(), 36))
                    .title(ex.getMessage())
                    .description(ex.getMessage())
                    .inputMessageContent(InputTextMessageContent.builder()
                            .messageText(ex.getMessage())
                            .build())
                    .build());
        }
        bot.perform(AnswerInlineQuery.builder()
                .queryId(event.getQuery().getId())
                .addAllResults(resultList)
                .isPersonal(false)
                .cacheTime(60 * 60 * 2)
                .errorHandler(Exception::printStackTrace)
                .build());
        return true;
    }

    private static void reply(TextMessageEvent event, String text, ParseMode parseMode) {
        bot.perform(SendText.builder()
                .text(text)
                .parseMode(parseMode)
                .chatId(event.getMessage().getChat().getChatId())
                .replyToMessageID(event.getMessage().getMessageId())
                .build()
        );
    }

    private static void reply(TextMessageEvent event, BufferedImage img, String caption) {
        try {
            File file = File.createTempFile("conjugator_image", ".png");
            ImageIO.write(img, "png", file);
            bot.perform(SendPhoto.builder()
                            .photo(new LocalInputFile(file))
                            .caption(caption)
                            .chatId(event.getMessage().getChat().getChatId())
                            .replyToMessageId(event.getMessage().getMessageId())
                            .build()
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Extractor.ExtractResult[] lookup(String chatId, String[] args) {
        if (args.length == 0) {
            throw new RuntimeException("Not enough arguments!");
        }

        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].toLowerCase();
        }

        String language;
        String keyword;
        if (args.length == 1) {
            language = groupLanguageBindings.get(chatId);
            keyword = args[0];
            if (language == null) {
                throw new RuntimeException("PM @ConjugatorBot with /bind OR use (lang) (word)");
            }
        } else {
            language = args[0];
            keyword = args[1];
        }
        Extractor extractor = extractors.get(language);
        if (extractor == null) {
            throw new RuntimeException("Invalid language! Supported: " + String.join(", ", extractors.keySet()));
        }

        try {
            return extractor.extract(keyword);
        } catch (HttpStatusException ex) {
            throw new RuntimeException("No such word!");
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException("An error occurred while retrieving the data.");
        }
    }

}
