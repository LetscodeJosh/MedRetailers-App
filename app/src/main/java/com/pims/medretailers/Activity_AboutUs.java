package com.pims.medretailers;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Activity_AboutUs extends BaseActivity {

    private TextView tvAboutDescription;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aboutus);

        tvAboutDescription = findViewById(R.id.tvAboutDescription); // Need to add this ID to layout

        TextView tvRedirectContact = findViewById(R.id.tvRedirectContact);
        if (tvRedirectContact != null) {
            setupHoverEffect(tvRedirectContact);
            tvRedirectContact.setOnClickListener(v -> {
                Intent intent = new Intent(Activity_AboutUs.this, Activity_Contactus.class);
                startActivity(intent);
            });
        }

        fetchCompanyInfo();
    }

    private void fetchCompanyInfo() {
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        String cookie = prefs.getString("Session_Cookie", "");

        // First get company list
        String url = Config.BASE_URL + "/api/resource/Company?limit_page_length=1";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (okhttp3.ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful() && responseBody != null) {
                        JSONObject json = new JSONObject(responseBody.string());
                        JSONArray data = json.optJSONArray("data");
                        if (data != null && data.length() > 0) {
                            String companyName = data.getJSONObject(0).getString("name");
                            fetchSpecificCompanyDetails(companyName, cookie);
                        }
                    }
                } catch (Exception e) {
                    Log.e("ABOUT_US", "Error fetching company list", e);
                }
            }
        });
    }

    private void fetchSpecificCompanyDetails(String name, String cookie) {
        String url = Config.BASE_URL + "/api/resource/Company/" + name;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("ABOUT_US", "Failed to fetch specific company", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (okhttp3.ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful() && responseBody != null) {
                        JSONObject json = new JSONObject(responseBody.string());
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            final String description = data.optString("company_description", "");
                            if (!description.isEmpty()) {
                                runOnUiThread(() -> {
                                    if (tvAboutDescription != null) {
                                        tvAboutDescription.setText(android.text.Html.fromHtml(description, android.text.Html.FROM_HTML_MODE_LEGACY));
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ABOUT_US", "Error parsing company details", e);
                }
            }
        });
    }

    @Override
    protected void setupHoverEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.7f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }
}
