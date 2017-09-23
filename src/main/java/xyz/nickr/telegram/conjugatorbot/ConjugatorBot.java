package xyz.nickr.telegram.conjugatorbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.jsoup.HttpStatusException;
import pro.zackpollard.telegrambot.api.TelegramBot;
import pro.zackpollard.telegrambot.api.chat.ChatMember;
import pro.zackpollard.telegrambot.api.chat.ChatMemberStatus;
import pro.zackpollard.telegrambot.api.chat.ChatType;
import pro.zackpollard.telegrambot.api.chat.inline.send.InlineQueryResponse;
import pro.zackpollard.telegrambot.api.chat.inline.send.content.InputTextMessageContent;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResult;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResultArticle;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResultPhoto;
import pro.zackpollard.telegrambot.api.chat.message.Message;
import pro.zackpollard.telegrambot.api.chat.message.send.InputFile;
import pro.zackpollard.telegrambot.api.chat.message.send.ParseMode;
import pro.zackpollard.telegrambot.api.chat.message.send.SendablePhotoMessage;
import pro.zackpollard.telegrambot.api.chat.message.send.SendableTextMessage;
import pro.zackpollard.telegrambot.api.event.Listener;
import pro.zackpollard.telegrambot.api.event.chat.inline.InlineQueryReceivedEvent;
import pro.zackpollard.telegrambot.api.event.chat.message.CommandMessageReceivedEvent;
import xyz.nickr.telegram.conjugatorbot.extractor.DaExtractor;
import xyz.nickr.telegram.conjugatorbot.extractor.Extractor;
import xyz.nickr.telegram.conjugatorbot.extractor.SvExtractor;

/**
 * @author Nick Robson
 */
public class ConjugatorBot implements Listener {

    private static AtomicInteger queryId = new AtomicInteger(0);
    private static TelegramBot bot;
    private static final Gson GSON = new Gson();
    private static String IMGUR_TOKEN = System.getenv("IMGUR_TOKEN");

    private static final Map<String, String> groupLanguageBindings = new LinkedHashMap<>();

    private final Map<String, Extractor> extractors = new LinkedHashMap<String, Extractor>() {{
        put("sv", new SvExtractor());
        put("svenska", get("sv"));
        put("da", new DaExtractor());
        put("dansk", get("da"));
    }};

    private final Map<String, Consumer<CommandMessageReceivedEvent>> commands = new LinkedHashMap<String, Consumer<CommandMessageReceivedEvent>>() {{
        put("lookup", ConjugatorBot.this::lookupCommand);
        put("bind", ConjugatorBot.this::bindCommand);
    }};

    public static void main(String[] args) {
        bot = TelegramBot.login(System.getenv("BOT_TOKEN"));

        System.out.println("Logged in as " + bot.getBotUsername());

        try (FileReader reader = new FileReader("groups.json")) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            json.entrySet().forEach(e -> groupLanguageBindings.put(e.getKey(), e.getValue().getAsString()));
        } catch (FileNotFoundException ignored) {
            // noop
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        bot.getEventsManager().register(new ConjugatorBot());
        bot.startUpdates(true);
    }

    public void onCommandMessageReceived(CommandMessageReceivedEvent event) {
        if (!event.isBotMentioned())
            return;
        Consumer<CommandMessageReceivedEvent> command = commands.get(event.getCommand().toLowerCase());
        if (command != null) {
            try {
                command.accept(event);
            } catch (Exception ex) {
                ex.printStackTrace();
                reply(event, "An error occurred while handling your command!", ParseMode.NONE);
            }
        }
    }

    private void bindCommand(CommandMessageReceivedEvent event) {
        if (event.getChat().getType() != ChatType.PRIVATE && event.getMessage().getSender().getId() != 112972102L) {
            ChatMember member = event.getChat().getChatMember(event.getMessage().getSender());
            if (member.getStatus().ordinal() < ChatMemberStatus.ADMINISTRATOR.ordinal()) {
                reply(event, "You're not a chat admin.", ParseMode.NONE);
                return;
            }
        }
        String[] args = event.getArgs();
        if (args.length == 0) {
            String oldLang = groupLanguageBindings.remove(event.getChat().getId());
            if (oldLang != null) {
                try (FileWriter writer = new FileWriter("groups.json")) {
                    GSON.toJson(groupLanguageBindings, writer);
                } catch (IOException ignored) {}
                reply(event, "Unbound from " + oldLang + "!", ParseMode.NONE);
            } else {
                reply(event, "You need to specify a language to bind to: /bind (lang)", ParseMode.NONE);
            }
            return;
        }
        args[0] = args[0].toLowerCase();
        if (extractors.containsKey(args[0])) {
            groupLanguageBindings.put(event.getChat().getId(), args[0]);
            try (FileWriter writer = new FileWriter("groups.json")) {
                GSON.toJson(groupLanguageBindings, writer);
            } catch (IOException ignored) {}
            reply(event, "Language bound to: " + args[0], ParseMode.NONE);
        } else {
            reply(event, "That language isn't supported! Try one of: " + String.join(", ", extractors.keySet()), ParseMode.NONE);
        }
    }

    private Extractor.ExtractResult[] lookup(String chatId, String[] args) throws Exception {
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

    private void lookupCommand(CommandMessageReceivedEvent event) {
        try {
            Extractor.ExtractResult[] results = lookup(event.getChat().getId(), event.getArgs());
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
    }

    @Override
    public void onInlineQueryReceived(InlineQueryReceivedEvent event) {
        List<InlineQueryResult> resultList = new ArrayList<>();
        try {
            String[] args = event.getQuery().getQuery().split(" ");
            Extractor.ExtractResult[] results = lookup(String.valueOf(event.getQuery().getSender().getId()), args);
            for (Extractor.ExtractResult result : results) {
                if (result.img != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(result.img, "png", baos);
                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    URL url = Imgur.uploadSingle(IMGUR_TOKEN, "image.png", bais);
                    resultList.add(InlineQueryResultPhoto.builder()
                            .id(Integer.toString(queryId.getAndIncrement(), 36))
                            .thumbUrl(url)
                            .photoUrl(url)
                            .photoWidth(result.img.getWidth())
                            .photoHeight(result.img.getHeight())
                            .caption(result.caption)
                            .description(result.caption)
                            .build());
                } else {
                    resultList.add(InlineQueryResultArticle.builder()
                            .id(Integer.toString(queryId.getAndIncrement(), 36))
                            .description(result.caption)
                            .inputMessageContent(InputTextMessageContent.builder()
                                    .messageText(result.caption)
                                    .build())
                            .build());
                }
            }
            if (results.length == 0) {
                resultList.add(InlineQueryResultArticle.builder()
                        .id(Integer.toString(queryId.getAndIncrement(), 36))
                        .description("No results found!")
                        .inputMessageContent(InputTextMessageContent.builder()
                                .messageText("No results found!")
                                .build())
                        .build());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            resultList.clear();
            resultList.add(InlineQueryResultArticle.builder()
                    .id(Integer.toString(queryId.getAndIncrement(), 36))
                    .title(ex.getMessage())
                    .description(ex.getMessage())
                    .inputMessageContent(InputTextMessageContent.builder()
                            .messageText(ex.getMessage())
                            .build())
                    .build());
        }
        event.getQuery().answer(bot, InlineQueryResponse.builder()
                .results(resultList)
                .isPersonal(false)
                .cacheTime(0)
                .build()
        );
    }

    private Message reply(CommandMessageReceivedEvent event, String message, ParseMode parseMode) {
        return event.getChat().sendMessage(
                SendableTextMessage.builder()
                        .message(message)
                        .parseMode(parseMode)
                        .replyTo(event.getMessage().getMessageId())
                        .build()
        );
    }

    private Message reply(CommandMessageReceivedEvent event, BufferedImage img, String caption) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return event.getChat().sendMessage(
                    SendablePhotoMessage.builder()
                            .photo(new InputFile(bais, "image.png"))
                            .caption(caption)
                            .replyTo(event.getMessage().getMessageId())
                            .build()
            );
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

}
