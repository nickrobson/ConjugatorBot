package xyz.nickr.telegram.conjugatorbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import okhttp3.FormBody;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Imgur {

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public static JsonObject upload(String clientId, String title, byte[] bytes) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(bytes);
        Request request = new Request.Builder()
                .url("https://api.imgur.com/3/image")
                .header("Authorization", "Client-ID " + clientId)
                .post(new FormBody.Builder()
                        .add("image", base64)
                        .add("title", title)
                        .add("type", "base64")
                        .build())
                .build();
        JsonObject obj;
        try (Response response = HTTP.newCall(request).execute()) {
            obj = GSON.fromJson(response.body().string(), JsonObject.class);
        }
        return obj;
    }

    public static URL uploadSingle(String clientId, String title, byte[] bytes) throws IOException {
        JsonObject obj = upload(clientId, title, bytes);
        try {
            return obj != null && obj.get("success").getAsBoolean() ? new URL(obj.getAsJsonObject("data").get("link").getAsString()) : null;
        } catch (MalformedURLException e) {
            return null;
        }
    }

}
