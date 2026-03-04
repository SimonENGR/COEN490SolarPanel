package com.example.coen490solarpanel;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    private VideoView videoBackground;
    private ImageButton btnConnect;
    private ImageButton btnPrototype;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        videoBackground = findViewById(R.id.video_background);
        btnConnect = findViewById(R.id.btn_connect);
        btnPrototype = findViewById(R.id.btn_prototype);

        setupVideoBackground();
        setupButtons();
    }

    private void setupVideoBackground() {
        // Construct the URI for the raw video file
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.idle_home);
        videoBackground.setVideoURI(videoUri);

        videoBackground.setOnPreparedListener(mp -> {
            // Loop the video continuously
            mp.setLooping(true);
            // Mute the video just in case there's any background noise
            mp.setVolume(0f, 0f);

            // --- MANUAL VIDEO SCALE OVERRIDE ---
            // The video is currently scaling too large vertically.
            // Adjust these two numbers to explicitly stretch/squish the video 
            // until it fits your screen exactly the way you want.
            // 1.0f = Native video width/height.
            float horizontalScale = 2.2f;
            float verticalScale = 1.0f;
            
            // Apply scales directly
            videoBackground.setScaleX(horizontalScale);
            videoBackground.setScaleY(verticalScale);
        });

        // Start playing
        videoBackground.start();
    }

    private void setupButtons() {
        // Add green press effect
        setButtonPressEffect(btnConnect);
        setButtonPressEffect(btnPrototype);

        // Connect Button -> Starts normal logic (ESP32 discovery)
        btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            // Default behavior is normal connection, no extra needed
            startActivity(intent);
            finish(); // Close welcome screen so user can't press back to it
        });

        // Prototype Button -> Passes a flag to bypass discovery
        btnPrototype.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            intent.putExtra("isPrototype", true);
            startActivity(intent);
            finish(); // Close welcome screen
        });
    }

    private void setButtonPressEffect(ImageButton button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Apply a semi-transparent green tint overlay
                    button.setColorFilter(Color.argb(80, 0, 255, 0), PorterDuff.Mode.SRC_ATOP);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Remove tint when finger is lifted
                    button.clearColorFilter();
                    break;
            }
            // Return false so that the onClickListener still fires
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoBackground != null && !videoBackground.isPlaying()) {
            videoBackground.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoBackground != null && videoBackground.isPlaying()) {
            videoBackground.pause();
            // Freeing up some resources
        }
    }
}
