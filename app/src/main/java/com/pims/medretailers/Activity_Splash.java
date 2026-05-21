package com.pims.medretailers;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import com.pims.medretailers.databinding.ActivitySplashBinding;

public class Activity_Splash extends BaseActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setText("");
        setupHoverEffect(binding.btnLogin);

        binding.btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show notification on the splash screen
                AppNotification.show(Activity_Splash.this, "Proceeding to Login...", AppNotification.Type.SUCCESS);
                
                Log.d("LoginPage", "User clicked login, redirecting...");

                // Add a small delay so the notification animation is seen
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(Activity_Splash.this, Activity_LoginPage.class);
                    startActivity(intent);
                    finish(); // Close splash activity
                }, 300);
            }
        });
    }

    @Override
    protected void setupHoverEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.7f).setDuration(100).start();
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