package net.kdt.pojavlaunch.services;

import android.os.Handler;
import android.os.Looper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class McStatusService {

    public interface McStatusCallback {
        void onSuccess(String json);
        void onError(Exception e);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public static void fetchServerStatus(String serverIp, McStatusCallback callback) {
        String url = "https://api.mcstatus.io/v2/status/java/" + serverIp;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError(new IOException("Unexpected code " + response)));
                } else {
                    String responseBody = response.body().string();
                    mainHandler.post(() -> callback.onSuccess(responseBody));
                }
            }
        });
    }
}
