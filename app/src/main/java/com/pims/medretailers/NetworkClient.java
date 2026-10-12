package com.pims.medretailers;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * Singleton class to provide a single OkHttpClient instance across the app.
 * Reusing the same client allows for connection pooling, which significantly
 * speeds up network requests by reducing handshake overhead.
 */
public class NetworkClient {
    private static OkHttpClient instance;

    public static synchronized OkHttpClient getInstance() {
        if (instance == null) {
            instance = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return instance;
    }
}
