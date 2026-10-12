package com.pims.medretailers;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Activity_Contactus extends BaseActivity {

    private EditText etEmail, etMessage;
    private Spinner spCategory;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contactus);

        etEmail = findViewById(R.id.etEmail);
        etMessage = findViewById(R.id.etMessage);
        spCategory = findViewById(R.id.spCategory);
        View btnSend = findViewById(R.id.btnSend);

        // Pre-fill email if logged in
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        String savedEmail = prefs.getString("User_Email", "");
        if (etEmail != null && !savedEmail.isEmpty()) {
            etEmail.setText(savedEmail);
        }

        if (btnSend != null) {
            setupHoverEffect(btnSend);
            btnSend.setOnClickListener(v -> validateAndSend());
        }
    }

    private void validateAndSend() {
        String email = etEmail.getText().toString().trim();
        String message = etMessage.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();

        if (email.isEmpty()) {
            AppNotification.show(this, "Please enter your email", AppNotification.Type.ERROR);
            return;
        }
        if (message.isEmpty()) {
            AppNotification.show(this, "Please enter a message", AppNotification.Type.ERROR);
            return;
        }

        sendToERPNext(email, message, category);
    }

    private void sendToERPNext(String email, String message, String category) {
        AppNotification.show(this, "Sending your message...", AppNotification.Type.INFO);

        try {
            JSONObject data = new JSONObject();
            data.put("doctype", "Lead");
            data.put("email_id", email);
            data.put("lead_name", "Contact Form: " + email);
            data.put("source", "Mobile App");
            data.put("notes", "Category: " + category + "\n\n" + message);

            RequestBody body = RequestBody.create(
                    data.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            String cookie = prefs.getString("Session_Cookie", "");

            Request request = new Request.Builder()
                    .url(Config.BASE_URL + "/api/resource/Lead")
                    .post(body)
                    .addHeader("Cookie", cookie)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> AppNotification.show(Activity_Contactus.this, "Connection failed", AppNotification.Type.ERROR));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (okhttp3.ResponseBody responseBody = response.body()) {
                        final String respStr = responseBody != null ? responseBody.string() : "";
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                AppNotification.show(Activity_Contactus.this, "Thank you! We'll get back to you soon.", AppNotification.Type.SUCCESS);
                                if (etMessage != null) etMessage.setText("");
                            } else {
                                Log.e("ERP_ERROR", respStr);
                                AppNotification.show(Activity_Contactus.this, "Failed to send message.", AppNotification.Type.ERROR);
                            }
                        });
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            AppNotification.show(this, "An error occurred", AppNotification.Type.ERROR);
        }
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
