package com.example.coen490solarpanel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TrackingFragment extends Fragment {

    // --- UI Elements ---
    private TextView tvMotorStatus;
    private TextView tvTiltAngle;

    // --- Motor Buttons ---
    private Button btnTiltUp, btnTiltDown;
    private Button btnStopAll;

    // --- Precision Angle Buttons ---
    private Button btnMinus1, btnMinus01, btnPlus01, btnPlus1, btnGoAngle;
    private android.widget.EditText etTargetAngle;

    // --- Networking ---
    private SolarApiService apiService;
    private Vibrator vibrator;

    // --- Motor State Tracking ---
    private boolean isMotorActive = false;
    private String activeMotor = "";

    // --- Angle Polling ---
    private android.os.Handler angleHandler = new android.os.Handler();
    private Runnable anglePoller;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tracking, container, false);

        tvMotorStatus = view.findViewById(R.id.tv_motor_status);
        tvTiltAngle = view.findViewById(R.id.tv_tilt_angle);

        // Initialize Motor Buttons
        btnTiltUp = view.findViewById(R.id.btn_tilt_up);
        btnTiltDown = view.findViewById(R.id.btn_tilt_down);
        btnStopAll = view.findViewById(R.id.btn_stop_all);

        // Get Vibrator Service
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        // Setup Network Connection
        setupApiService();

        // Set up HOLD-TO-OPERATE buttons with haptic feedback
        setupHoldToOperateButton(btnTiltUp, "tilt", 1, "Tilting ↑");
        setupHoldToOperateButton(btnTiltDown, "tilt", -1, "Tilting ↓");

        // Emergency Stop with confirmation
        btnStopAll.setOnClickListener(v -> {
            vibrateDevice(100);
            sendMotorCommand("all", 0);
        });

        btnStopAll.setOnLongClickListener(v -> {
            showEmergencyStopDialog();
            return true;
        });

        // Precision Angle Controls
        btnMinus1 = view.findViewById(R.id.btn_minus_1);
        btnMinus01 = view.findViewById(R.id.btn_minus_01);
        btnPlus01 = view.findViewById(R.id.btn_plus_01);
        btnPlus1 = view.findViewById(R.id.btn_plus_1);
        btnGoAngle = view.findViewById(R.id.btn_go_angle);
        etTargetAngle = view.findViewById(R.id.et_target_angle);

        btnMinus1.setOnClickListener(v -> { vibrateDevice(30); sendAngleNudge(-1.0f); });
        btnMinus01.setOnClickListener(v -> { vibrateDevice(30); sendAngleNudge(-0.1f); });
        btnPlus01.setOnClickListener(v -> { vibrateDevice(30); sendAngleNudge(0.1f); });
        btnPlus1.setOnClickListener(v -> { vibrateDevice(30); sendAngleNudge(1.0f); });

        btnGoAngle.setOnClickListener(v -> {
            String text = etTargetAngle.getText().toString().trim();
            if (!text.isEmpty()) {
                vibrateDevice(50);
                sendAngleTarget(Float.parseFloat(text));
            } else {
                Toast.makeText(getContext(), "Enter a target angle", Toast.LENGTH_SHORT).show();
            }
        });

        updateMotorStatus("Ready");

        // Setup angle poller (always polls, faster during motor use)
        anglePoller = new Runnable() {
            @Override
            public void run() {
                fetchTiltAngle();
                // Poll faster during motor operation, slower when idle
                int interval = isMotorActive ? 500 : 2000;
                angleHandler.postDelayed(this, interval);
            }
        };

        // Start continuous polling
        angleHandler.post(anglePoller);

        return view;
    }

    private void setupApiService() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        String espIp = prefs.getString("esp_ip", "192.168.4.1");

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://" + espIp + "/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(SolarApiService.class);
        } catch (Exception e) {
            Log.e("TrackingFragment", "Retrofit Init Error", e);
        }
    }

    /**
     * Sets up a button to only operate while held down (safety feature)
     */
    private void setupHoldToOperateButton(Button button, String motorType, int direction, String label) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Button pressed - START motor
                    vibrateDevice(50);
                    sendMotorCommand(motorType, direction);
                    updateMotorStatus(label + " ACTIVE");
                    activeMotor = motorType;
                    isMotorActive = true;
                    button.setPressed(true);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Button released - STOP motor
                    vibrateDevice(30);
                    sendMotorCommand(motorType, 0); // Stop
                    updateMotorStatus("Ready");
                    isMotorActive = false;
                    button.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    private void vibrateDevice(long duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    private void sendMotorCommand(String type, int dir) {
        if (apiService == null) {
            Snackbar.make(requireView(), "Not connected to ESP32", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Visual feedback
        String directionText = (dir == 1) ? "↑" : (dir == -1) ? "↓" : "STOP";

        if (dir != 0) {
            Log.d("MotorControl", "Starting: " + type + " " + directionText);
        }

        // Send API Request
        apiService.controlMotor(type, dir).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    Log.d("MotorControl", "Success: " + response.body().message);
                } else {
                    Snackbar.make(requireView(),
                            "Command failed: " + response.code(),
                            Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;

                Snackbar.make(requireView(),
                                "Connection error. Check WiFi.",
                                Snackbar.LENGTH_LONG)
                        .setAction("RETRY", v -> sendMotorCommand(type, dir))
                        .show();
            }
        });
    }

    private void showEmergencyStopDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("⚠ Emergency Stop")
                .setMessage("This will immediately stop all motors and disable auto-tracking. Continue?")
                .setPositiveButton("STOP ALL", (dialog, which) -> {
                    vibrateDevice(200);
                    sendMotorCommand("all", 0);
                    Snackbar.make(requireView(), "✓ All motors stopped", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateMotorStatus(String status) {
        if (tvMotorStatus != null) {
            tvMotorStatus.setText("Motor Status: " + status);

            // Color coding
            if (status.contains("ACTIVE")) {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (status.equals("Ready")) {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            }
        }
    }

    private void fetchTiltAngle() {
        if (apiService == null) return;

        apiService.getStatus().enqueue(new Callback<SolarStatus>() {
            @Override
            public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                if (getContext() == null || response.body() == null) return;

                SolarStatus status = response.body();
                if (tvTiltAngle != null) {
                    tvTiltAngle.setText(String.format("Tilt Angle: %.2f°", status.tiltAngle));
                }
            }

            @Override
            public void onFailure(Call<SolarStatus> call, Throwable t) {
                Log.e("TrackingFragment", "Angle fetch failed", t);
            }
        });
    }

    private void sendAngleNudge(float delta) {
        if (apiService == null) return;
        updateMotorStatus(String.format("Nudging %+.1f°...", delta));

        apiService.nudgeAngle(delta).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                updateMotorStatus("Positioning...");
                // Fetch updated angle after a short delay
                angleHandler.postDelayed(() -> fetchTiltAngle(), 500);
                angleHandler.postDelayed(() -> {
                    fetchTiltAngle();
                    updateMotorStatus("Ready");
                }, 2000);
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
                Log.e("TrackingFragment", "Nudge failed", t);
            }
        });
    }

    private void sendAngleTarget(float target) {
        if (apiService == null) return;
        updateMotorStatus(String.format("Going to %.1f°...", target));

        apiService.setAngle(target).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                updateMotorStatus("Positioning...");
                angleHandler.postDelayed(() -> {
                    fetchTiltAngle();
                    updateMotorStatus("Ready");
                }, 3000);
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
                Log.e("TrackingFragment", "SetAngle failed", t);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Restart angle polling
        angleHandler.removeCallbacks(anglePoller); // Prevent duplicates
        angleHandler.post(anglePoller);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop angle polling
        angleHandler.removeCallbacks(anglePoller);
        // Safety: Stop all motors when leaving the fragment
        if (isMotorActive) {
            sendMotorCommand(activeMotor, 0);
            Toast.makeText(getContext(), "Motors stopped (fragment paused)", Toast.LENGTH_SHORT).show();
        }
    }
}
