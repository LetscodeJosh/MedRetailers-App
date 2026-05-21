package com.pims.medretailers;

/**
 * Global configuration settings for the application.
 */
public class Config {
    // Application version
    public static final String APP_VERSION = "1.3.7-alpha";

    // API Configuration
    // Set this to "medretailers.com" for production
    public static final String API_ENDPOINT_DOMAIN = "mirror.medretailers.com";

    public static final String BASE_URL = "https://" + API_ENDPOINT_DOMAIN;

    private Config() {
        // Private constructor to prevent instantiation
    }
}
