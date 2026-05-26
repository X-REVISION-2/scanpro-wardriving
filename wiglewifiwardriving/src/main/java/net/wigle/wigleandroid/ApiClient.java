package net.wigle.wigleandroid;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;

public class ApiClient {

    private static final String API_URL = "https://wdgwars.pl/api/me";
    private final OkHttpClient client = new OkHttpClient();

    public interface StatsCallback {
        void onSuccess(int total, int wifi, int ble);
        void onError(Exception e);
    }

    public void fetchStats(String apiKey, StatsCallback callback) {
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("X-API-Key", apiKey)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError(
                                new IOException("HTTP error: " + response.code()));
                        return;
                    }

                    String body = response.body().string();

                    JSONObject json = new JSONObject(body);

                    int total = json.getInt("total");
                    int wifi = json.getInt("wifi");
                    int ble = json.getInt("ble");

                    callback.onSuccess(total, wifi, ble);

                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    response.close();
                }
            }
        });
    }
}