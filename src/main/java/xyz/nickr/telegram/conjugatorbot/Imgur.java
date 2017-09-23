package xyz.nickr.telegram.conjugatorbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class Imgur {

    private static final CloseableHttpClient HTTP = HttpClients.createDefault();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public static JsonObject upload(String clientId, String title, InputStream is) throws IOException {
        HttpPost post = new HttpPost("https://api.imgur.com/3/image");
        post.setHeader("Authorization", "Client-ID " + clientId);
        HttpEntity entity = MultipartEntityBuilder.create().addPart("title", new StringBody(title, ContentType.TEXT_PLAIN)).addPart("image", new InputStreamBody(is, title)).build();
        post.setEntity(entity);
        CloseableHttpResponse res = HTTP.execute(post);
        JsonObject obj;
        try (InputStreamReader isr = new InputStreamReader(res.getEntity().getContent())) {
            obj = GSON.fromJson(isr, JsonObject.class);
            res.close();
        }
        return obj;
    }

    public static URL uploadSingle(String clientId, String title, InputStream is) throws IOException {
        JsonObject obj = upload(clientId, title, is);
        try {
            return obj != null && obj.get("success").getAsBoolean() ? new URL(obj.getAsJsonObject("data").get("link").getAsString()) : null;
        } catch (MalformedURLException e) {
            return null;
        }
    }

}
