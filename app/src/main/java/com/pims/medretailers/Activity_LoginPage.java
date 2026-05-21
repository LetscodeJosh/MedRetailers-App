package com.pims.medretailers;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.pims.medretailers.databinding.ActivityLoginpageBinding;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Activity_LoginPage extends BaseActivity {

    private ActivityLoginpageBinding binding;
    private final OkHttpClient client = NetworkClient.getInstance();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_LOGIN_URL = Config.BASE_URL + "/api/method/login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginpageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle logout message from session expiration
        String logoutReason = getIntent().getStringExtra("logout_reason");
        if (logoutReason != null) {
            AppNotification.show(this, logoutReason, AppNotification.Type.INFO);
        }

        android.graphics.Typeface monoTypeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL);
        binding.etMail.setTypeface(monoTypeface);
        binding.etPassword.setTypeface(monoTypeface);
        binding.btnloginaccount.setTypeface(monoTypeface);
        binding.btnloginaccount.setTextSize(16f); // Ensure readable size
        binding.tvVersionInfo.setText(getString(R.string.version_info, Config.APP_VERSION));

        binding.btnloginaccount.setText("Sign In");

        binding.btnloginaccount.setOnClickListener(v -> {
            String usr = binding.etMail.getText().toString().trim();
            String pwd = binding.etPassword.getText().toString().trim();
            if (usr.isEmpty() || pwd.isEmpty()) {
                AppNotification.show(this, "Please enter email and password", AppNotification.Type.ERROR);
                return;
            }
            loginUser(usr, pwd);
        });
    }

    private void loginUser(String usr, String pwd) {
        binding.btnloginaccount.setEnabled(false);
        binding.btnloginaccount.setText("Authenticating...");

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("usr", usr);
            jsonBody.put("pwd", pwd);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_LOGIN_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    binding.btnloginaccount.setEnabled(true);
                    binding.btnloginaccount.setText("Login to MedRetailer");
                    AppNotification.show(Activity_LoginPage.this,
                            "Network Error: " + e.getMessage(), AppNotification.Type.ERROR);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (response.isSuccessful()) {
                    String extractedCookie = "";
                    for (String header : response.headers("Set-Cookie")) {
                        if (header.contains("sid=")) {
                            extractedCookie = header.split(";")[0];
                        }
                    }

                    final String finalCookie = extractedCookie;
                    getSharedPreferences("MedRetailerSession", MODE_PRIVATE)
                            .edit()
                            .putString("Session_Cookie", finalCookie)
                            .putString("User_Email", usr)
                            .apply();

                    runOnUiThread(() -> binding.btnloginaccount.setText(
                            "Syncing Profile..."));
                    startParallelDataFetch(usr, finalCookie);

                } else {
                    final String respBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        binding.btnloginaccount.setEnabled(true);
                        binding.btnloginaccount.setText("Sign In");
                        String erpError = ErrorUtils.parseErpNextError(respBody);
                        if (erpError.contains("Unknown ERP Error")) {
                            erpError = "Login Failed. Check credentials.";
                        }
                        AppNotification.show(Activity_LoginPage.this, erpError, AppNotification.Type.ERROR);
                    });
                }
            }
        });
    }

    private void startParallelDataFetch(String userEmail, String finalCookie) {
        // Optimized: Only wait for Role and Permissions to speed up entry.
        // User Details (Name/Address) can load in background or after transition.
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> resolvedRole = new AtomicReference<>("MedRep");
        AtomicReference<JSONObject> resolvedPermissions = new AtomicReference<>(new JSONObject());
        AtomicReference<String> debugStatus = new AtomicReference<>("Success");

        // 1. Fetch Details (Non-blocking)
        fetchUserDetailsParallel(userEmail, finalCookie, null);

        // 2. Fetch Permissions (Blocking)
        fetchUserPermissionsParallel(userEmail, finalCookie, resolvedPermissions, latch);

        // 3. Fetch Roles (Blocking)
        fetchUserRoleParallel(userEmail, finalCookie, resolvedRole, debugStatus, latch);

        // Wait for essential data in a background thread to not block UI
        new Thread(() -> {
            try {
                // Essential sync max wait - should be very fast
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            proceedToApp(finalCookie, resolvedPermissions.get(), resolvedRole.get(), debugStatus.get());
        }).start();
    }

    private void fetchUserDetailsParallel(String userEmail, String finalCookie, CountDownLatch latch) {
        String url = Config.BASE_URL + "/api/resource/User/" + userEmail;
        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { if(latch != null) latch.countDown(); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject data = new JSONObject(response.body().string()).getJSONObject("data");
                        String fullName = data.optString("full_name", "");
                        String location = data.optString("location", "No Address Available");

                        getSharedPreferences("MedRetailerSession", MODE_PRIVATE).edit()
                                .putString("Full_Name", fullName)
                                .putString("User_Address", location)
                                .putString("User_Email", userEmail)
                                .apply();
                    } catch (Exception e) { e.printStackTrace(); }
                }
                if(latch != null) latch.countDown();
            }
        });
    }

    private void fetchUserPermissionsParallel(String userEmail, String finalCookie, AtomicReference<JSONObject> resolvedPermissions, CountDownLatch latch) {
        String url = Config.BASE_URL + "/api/resource/User%20Permission"
                + "?fields=[%22allow%22,%22for_value%22]"
                + "&filters=[[%22user%22,%22=%22,%22" + userEmail + "%22]]"
                + "&limit_page_length=500";

        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { latch.countDown(); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject grouped = new JSONObject();
                        JSONArray dataArray = new JSONObject(response.body().string()).getJSONArray("data");
                        for (int i = 0; i < dataArray.length(); i++) {
                            JSONObject perm = dataArray.getJSONObject(i);
                            String type = perm.optString("allow");
                            String val = perm.optString("for_value");
                            if (!grouped.has(type)) grouped.put(type, new JSONArray());
                            grouped.getJSONArray(type).put(val);
                        }
                        resolvedPermissions.set(grouped);
                    } catch (Exception e) { e.printStackTrace(); }
                }
                latch.countDown();
            }
        });
    }

    private void fetchUserRoleParallel(String userEmail, String cookie, AtomicReference<String> resolvedRole, AtomicReference<String> debugStatus, CountDownLatch latch) {
        try {
            String encodedEmail = java.net.URLEncoder.encode(userEmail, "UTF-8");
            String url = Config.BASE_URL + "/api/method/hr_automation.api.user.get_roles?user_email=" + encodedEmail;
            Request request = new Request.Builder().url(url).addHeader("Cookie", cookie).get().build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    debugStatus.set("Network Fail");
                    latch.countDown();
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            JSONArray rolesArray = jsonObject.getJSONObject("message").getJSONArray("roles");
                            String finalRole = "MedRep";
                            int priority = 0;

                            for (int i = 0; i < rolesArray.length(); i++) {
                                String role = rolesArray.getString(i).trim();
                                if (role.equalsIgnoreCase("Administrator") || role.equalsIgnoreCase("System Manager")) {
                                    if (priority < 10) { finalRole = "Admin"; priority = 10; }
                                } else if (role.equalsIgnoreCase("Sales Master Manager") || role.equalsIgnoreCase("GM")) {
                                    if (priority < 8) { finalRole = "GM"; priority = 8; }
                                } else if (role.equalsIgnoreCase("NSM-1") || role.equalsIgnoreCase("NSM1") || role.equalsIgnoreCase("Sales Manager I")) {
                                    if (priority < 7) { finalRole = "NSM-1"; priority = 7; }
                                } else if (role.equalsIgnoreCase("NSM-2") || role.equalsIgnoreCase("NSM2") || role.equalsIgnoreCase("Sales Manager II")) {
                                    if (priority < 6) { finalRole = "NSM-2"; priority = 6; }
                                } else if (role.equalsIgnoreCase("Sales Manager") || role.equalsIgnoreCase("DSM") || role.equalsIgnoreCase("Sales Manager I-A")) {
                                    if (priority < 4) { finalRole = "DSM"; priority = 4; }
                                } else if (role.equalsIgnoreCase("Sales User") || role.equalsIgnoreCase("Sales Representative")) {
                                    if (priority < 2) { finalRole = "MedRep"; priority = 2; }
                                }
                            }
                            resolvedRole.set(finalRole);
                        } catch (Exception e) {
                            debugStatus.set("JSON Parse Error");
                            e.printStackTrace();
                        }
                    } else {
                        debugStatus.set("API Blocked - Code " + response.code());
                    }
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            debugStatus.set("URL Encoding Failed");
            latch.countDown();
        }
    }

    private void proceedToApp(String finalcookie, JSONObject groupedPermissions, String userRole, String debugStatus) {
        // ✅ Save with exact key "User_Role" — SO_Landscape reads this same key
        getSharedPreferences("MedRetailerSession", MODE_PRIVATE)
                .edit()
                .putString("Session_Cookie", finalcookie)
                .putString("User_Permissions_Map",  groupedPermissions.toString())
                .putString("User_Role", userRole)
                .apply();

        runOnUiThread(() -> {
            if (userRole.equals("MedRep") && !debugStatus.equals("Success")) {
                AppNotification.show(this,
                        "Warning! Defaulted to MedRep. Reason: " + debugStatus, AppNotification.Type.INFO);
            } else {
                AppNotification.show(this,
                        "Welcome! Logged in as: " + userRole, AppNotification.Type.SUCCESS);
            }

            Intent intent = new Intent(this, Activity_SO_Landscape.class);
            intent.putExtra("Session_Cookie", finalcookie);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}