package com.pims.medretailers;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

// Ensure this matches your actual layout file name (e.g., activity_signinpage.xml -> ActivitySigninpageBinding)
// If your layout is still named activity_loginpage.xml, keep it as is, but usually, it should be distinct.
import com.pims.medretailers.databinding.ActivitySigninpageBinding;

public class Activity_SigninPage extends BaseActivity {

    // ViewBinding object
    private ActivitySigninpageBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate view using ViewBinding
        binding = ActivitySigninpageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- EXISTING LOGIC: REGISTER BUTTON ---
        // Assuming this button completes the registration
        binding.btnSigninpage.setText("Register");

        binding.btnSigninpage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(Activity_SigninPage.this, "Registration completed!", Toast.LENGTH_SHORT).show();
                Log.d("SigninPage", "User Registered successfully.");

                // Proceed to main app (SO Portrait)
                Intent intent = new Intent(Activity_SigninPage.this, Activity_SO_Landscape.class);
                startActivity(intent);
            }
        });

        binding.tvBacktoLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d("SigninPage", "Navigating back to Login Page");

                Intent intent = new Intent(Activity_SigninPage.this, Activity_LoginPage.class);
                startActivity(intent);

                finish();
            }
        });
    }
}