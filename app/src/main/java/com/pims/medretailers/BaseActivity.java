package com.pims.medretailers;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base Activity to monitor network connectivity and enforce session security.
 * Automatically logs out the user after 6 seconds of internet disconnection.
 */
public class BaseActivity extends AppCompatActivity {

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler logoutHandler = new Handler(Looper.getMainLooper());
    private Runnable logoutRunnable;
    private boolean isOffline = false;

    // Session Configuration: Inactivity timeout before automatic logout
    // Current setting: 10 minutes (10 * 60 * 1000 ms)
    protected static final long INACTIVITY_TIMEOUT = 10 * 60 * 1000;
    private static long lastInteractionTime = System.currentTimeMillis();
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private Runnable inactivityRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        setupNetworkCallback();
        setupInactivityTimer();
    }

    private void setupInactivityTimer() {
        inactivityRunnable = () -> {
            boolean isAuthenticatedPage = !(BaseActivity.this instanceof Activity_LoginPage || BaseActivity.this instanceof Activity_Splash);
            if (isAuthenticatedPage) {
                Log.d("BaseActivity", "Session expired due to inactivity.");
                performLogout("Session expired due to inactivity. You have been standby for too long.");
            }
        };
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastInteractionTime = System.currentTimeMillis();
        resetInactivityTimer();
    }

    private void resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable);
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Intelligent Session Check: If coming back from background after timeout
        boolean isAuthenticatedPage = !(this instanceof Activity_LoginPage || this instanceof Activity_Splash);
        if (isAuthenticatedPage) {
            long timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime;
            if (timeSinceLastInteraction >= INACTIVITY_TIMEOUT) {
                performLogout("Session expired. You were away for too long.");
                return;
            }
        }
        
        resetInactivityTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Crucial Fix: Stop timer when activity is not in foreground. 
        // Prevents background activities from triggering logout while user is active in another screen.
        inactivityHandler.removeCallbacks(inactivityRunnable);
    }

    private void setupNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@androidx.annotation.NonNull Network network) {
                super.onAvailable(network);
                if (isOffline) {
                    isOffline = false;
                    cancelLogoutTimer();
                    runOnUiThread(() -> AppNotification.show(BaseActivity.this, "Internet Restored. Process can continue.", AppNotification.Type.SUCCESS));
                }
            }

            @Override
            public void onLost(@androidx.annotation.NonNull Network network) {
                super.onLost(network);
                isOffline = true;
                runOnUiThread(() -> {
                    boolean isAuthenticatedPage = !(BaseActivity.this instanceof Activity_LoginPage || BaseActivity.this instanceof Activity_Splash);
                    
                    if (isAuthenticatedPage) {
                        AppNotification.show(BaseActivity.this, "Network Lost. Session will expire in 30 seconds if not restored.", AppNotification.Type.ERROR);
                        startLogoutTimer();
                    } else {
                        AppNotification.show(BaseActivity.this, "Internet Disconnected. App may not work during this time.", AppNotification.Type.ERROR);
                    }
                });
            }
        };

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        } catch (Exception e) {
            Log.e("BaseActivity", "Failed to register network callback", e);
        }
    }

    private void startLogoutTimer() {
        cancelLogoutTimer();
        logoutRunnable = () -> {
            if (isOffline) {
                Log.d("BaseActivity", "Network lost for >30s. Performing automatic logout.");
                performLogout("Logged out due to persistent network loss.");
            }
        };
        logoutHandler.postDelayed(logoutRunnable, 30000); // 30-second grace period (increased from 6s)
    }

    private void cancelLogoutTimer() {
        if (logoutRunnable != null) {
            logoutHandler.removeCallbacks(logoutRunnable);
            logoutRunnable = null;
        }
    }

    protected void performLogout(String reason) {
        // Clear session
        getSharedPreferences("MedRetailerSession", MODE_PRIVATE).edit().clear().apply();
        
        // Redirect to login
        Intent intent = new Intent(this, Activity_LoginPage.class);
        if (reason != null) {
            intent.putExtra("logout_reason", reason);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    protected void performLogout() {
        performLogout(null);
    }

    protected void animateHamburgerMenu(android.widget.ImageView view, Runnable showMenuAction) {
        if (view == null) {
            showMenuAction.run();
            return;
        }

        view.animate()
                .rotation(135f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    view.setImageResource(R.drawable.ic_close_x);
                    view.animate()
                            .rotation(0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .withEndAction(showMenuAction)
                            .start();
                })
                .start();
    }

    protected void revertHamburgerMenu(android.widget.ImageView view) {
        if (view == null) return;

        view.animate()
                .rotation(-135f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    view.setImageResource(R.drawable.ic_hamburger);
                    view.animate()
                            .rotation(0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }

    protected void showUniversalMenu(android.view.View v) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        int rootPad = (int)(16 * density);
        root.setPadding(rootPad, rootPad, rootPad, rootPad);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int layoutPad = (int)(32 * density);
        layout.setPadding(layoutPad, layoutPad, layoutPad, layoutPad);
        layout.setGravity(android.view.Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#F8F9FA"));
        gd.setCornerRadius(30 * density);
        layout.setBackground(gd);
        root.addView(layout);

        // Profile Icon
        android.widget.ImageView profileIcon = new android.widget.ImageView(this);
        profileIcon.setImageResource(R.drawable.ic_profile);
        int iconSize = (int)(64 * density);
        android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMargins(0, 0, 0, (int)(16 * density));
        profileIcon.setLayoutParams(iconParams);
        layout.addView(profileIcon);

        android.content.SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        String displayName = prefs.getString("Full_Name", "User");
        String userEmail = prefs.getString("User_Email", "");

        android.widget.TextView tvUserName = new android.widget.TextView(this);
        tvUserName.setText(displayName);
        tvUserName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        tvUserName.setGravity(android.view.Gravity.CENTER);
        tvUserName.setTextColor(android.graphics.Color.parseColor("#333333"));
        tvUserName.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        layout.addView(tvUserName);

        android.widget.TextView tvUserEmail = new android.widget.TextView(this);
        tvUserEmail.setText(userEmail);
        tvUserEmail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        tvUserEmail.setGravity(android.view.Gravity.CENTER);
        tvUserEmail.setTextColor(android.graphics.Color.GRAY);
        tvUserEmail.setPadding(0, 0, 0, (int)(24 * density));
        layout.addView(tvUserEmail);

        // Options
        android.widget.TextView btnSOList = createMenuButton("Go Back to SO List", true);
        android.widget.TextView btnLogout = createMenuButton("Logout Account", true);
        android.widget.TextView btnStay   = createMenuButton("Stay", false);

        if (!(this instanceof Activity_SO_Landscape)) {
            layout.addView(btnSOList);
        }
        
        layout.addView(btnLogout);
        
        if (!(this instanceof Activity_SO_Landscape)) {
            layout.addView(btnStay);
        }

        builder.setView(root);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnSOList.setOnClickListener(v2 -> {
            dialog.dismiss();
            navigateToSOList();
        });
        btnLogout.setOnClickListener(v2 -> {
            dialog.dismiss();
            performLogout();
        });
        btnStay.setOnClickListener(v2 -> dialog.dismiss());
        
        dialog.setOnDismissListener(d -> revertHamburgerMenu((android.widget.ImageView) v));
        dialog.show();
    }

    private android.widget.TextView createMenuButton(String text, boolean isPrimary) {
        float density = getResources().getDisplayMetrics().density;
        android.widget.TextView btn = new android.widget.TextView(this);
        btn.setText(text);
        btn.setGravity(android.view.Gravity.CENTER);
        int hPad = (int)(24 * density);
        int vPad = (int)(12 * density);
        btn.setPadding(hPad, vPad, hPad, vPad);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        if (isPrimary) {
            bg.setColor(android.graphics.Color.parseColor("#835C9F"));
            btn.setTextColor(android.graphics.Color.WHITE);
        } else {
            bg.setColor(android.graphics.Color.TRANSPARENT);
            btn.setTextColor(android.graphics.Color.GRAY);
        }
        bg.setCornerRadius(20 * density);
        btn.setBackground(bg);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, 0);
        btn.setLayoutParams(lp);

        setupHoverEffect(btn);
        return btn;
    }

    protected void navigateToSOList() {
        OrderDataManager.getInstance().clearData();
        android.content.Intent intent = new android.content.Intent(this, Activity_SO_Landscape.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    protected void setupHoverEffect(android.view.View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(50).start();
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        cancelLogoutTimer();
        inactivityHandler.removeCallbacks(inactivityRunnable);
    }
}
