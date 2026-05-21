package com.pims.medretailers;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import java.lang.ref.WeakReference;

public class AppNotification {

    private static WeakReference<View> currentNotificationRef = new WeakReference<>(null);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Runnable currentHideRunnable = null;

    // Types of notifications
    public enum Type {
        SUCCESS, ERROR, INFO
    }

    public static void show(Context context, String message) {
        show(context, message, Type.INFO); // Default to INFO
    }

    public static void show(Context context, String message, Type type) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        // QOL: For Errors, use a persistent AlertDialog to ensure the user can read the full ERPNext message.
        if (type == Type.ERROR) {
            mainHandler.post(() -> {
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("Not permitted")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            });
            return;
        }

        mainHandler.post(() -> {
            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) return;

            // 1. Instant Responsiveness: Remove old notification and clear timers
            View oldNotification = currentNotificationRef.get();
            if (oldNotification != null) {
                if (currentHideRunnable != null) mainHandler.removeCallbacks(currentHideRunnable);
                if (oldNotification.getParent() != null) {
                    rootView.removeView(oldNotification);
                }
            }

            LayoutInflater inflater = LayoutInflater.from(activity);
            View notificationView = inflater.inflate(R.layout.custom_top_notification, rootView, false);
            currentNotificationRef = new WeakReference<>(notificationView);

            // 2. Interactive: Click to dismiss
            notificationView.setOnClickListener(v -> hide(notificationView, rootView));

            TextView tvMessage = notificationView.findViewById(R.id.tvNotificationMessage);
            if (tvMessage != null) {
                // Add icons based on type for better UX
                String prefix;
                switch (type) {
                    case SUCCESS: prefix = "✅ "; break;
                    case INFO:    prefix = "ℹ️ "; break;
                    default:      prefix = "ℹ️ "; break;
                }
                tvMessage.setText(prefix + message);
            }

            rootView.addView(notificationView);

            // 3. Fast Pop-up: Modern Overshoot animation
            notificationView.setVisibility(View.INVISIBLE);
            notificationView.post(() -> {
                notificationView.setVisibility(View.VISIBLE);
                float height = notificationView.getHeight();
                if (height <= 0) height = 250f;

                notificationView.setTranslationY(-height - 50f);
                notificationView.setAlpha(0f);

                notificationView.animate()
                        .translationY(35f) // Slide into view with a slight margin
                        .alpha(1f)
                        .setDuration(250) // Ultra-fast, snappier entry
                        .setInterpolator(new OvershootInterpolator(0.7f))
                        .start();
            });

            // 4. Readable: Dynamic duration based on text length (Min 4.5s, Max 10s)
            int displayDuration = Math.max(4500, Math.min(10000, message.length() * 85));

            currentHideRunnable = () -> hide(notificationView, rootView);
            mainHandler.postDelayed(currentHideRunnable, displayDuration);
        });
    }

    private static void hide(View view, ViewGroup parent) {
        if (view == null || parent == null || view.getParent() == null) return;

        view.animate()
                .translationY(-view.getHeight() - 100f)
                .alpha(0f)
                .setDuration(350)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    parent.removeView(view);
                    if (currentNotificationRef.get() == view) {
                        currentNotificationRef.clear();
                    }
                })
                .start();
    }
}
