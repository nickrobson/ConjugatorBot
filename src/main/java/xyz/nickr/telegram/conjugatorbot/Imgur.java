package xyz.nickr.telegram.conjugatorbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Imgur {

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public static JsonObject upload(String clientId, String title, byte[] bytes) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.imgur.com/3/image")
                .header("Authorization", "Client-ID " + clientId)
                .method("POST", new MultipartBody.Builder()
                        .addFormDataPart("title", null, RequestBody.create(MediaType.parse("text/plain"), title))
                        .addFormDataPart("image", title, RequestBody.create(MediaType.parse("application/octet-stream"), bytes))
                        .build())
                .build();
        JsonObject obj;
        try (Response response = HTTP.newCall(request).execute()) {
            try (Reader isr = response.body().charStream()) {
                obj = GSON.fromJson(isr, JsonObject.class);
            }
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
