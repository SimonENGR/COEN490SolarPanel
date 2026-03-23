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

public class CleaningFragment extends Fragment {

    // --- UI Elements ---
    private TextView tvMotorStatus;

    // --- Motor Buttons ---
    private Button btnCleanUp, btnCleanDown;
    private Button btnRunCleanCycle;
    private Button btnStopAll;

    // --- Networking ---
    private SolarApiService apiService;
    private Vibrator vibrator;

    // --- Motor State Tracking ---
    private boolean isMotorActive = false;
    private String activeMotor = "";

    // --- Poller Setup ---
    private boolean isAutoCleaning = false;
    private android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable statusPoller;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cleaning, container, false);

        tvMotorStatus = view.findViewById(R.id.tv_motor_status);

        // Initialize Motor Buttons
        btnCleanUp = view.findViewById(R.id.btn_clean_up);
        btnCleanDown = view.findViewById(R.id.btn_clean_down);
        btnRunCleanCycle = view.findViewById(R.id.btn_run_clean_cycle);
        btnStopAll = view.findViewById(R.id.btn_stop_all);

        // Get Vibrator Service
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        // Setup Network Connection
        setupApiService();

        // Set up HOLD-TO-OPERATE buttons with haptic feedback
        setupHoldToOperateButton(btnCleanUp, "clean", 1, "Cleaning ↑");
        setupHoldToOperateButton(btnCleanDown, "clean", -1, "Cleaning ↓");

        // Set up one-shot Auto Clean Button
        btnRunCleanCycle.setOnClickListener(v -> {
            vibrateDevice(50);
            sendRunCleanCycleCommand();
        });

        // Emergency Stop with confirmation
        btnStopAll.setOnClickListener(v -> {
            vibrateDevice(100);
            sendMotorCommand("all", 0);
        });

        btnStopAll.setOnLongClickListener(v -> {
            showEmergencyStopDialog();
            return true;
        });

        updateMotorStatus("Ready");

        // --- NEW POLLER SETUP (SECTION B) ---
        statusPoller = new Runnable() {
            @Override
            public void run() {
                // Only poll the ESP32 if we are actively waiting for an auto-clean to finish
                if (apiService != null && isAutoCleaning) {
                    apiService.getStatus().enqueue(new Callback<SolarStatus>() {
                        @Override
                        public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                            if (getContext() == null || response.body() == null) return;

                            // If ESP32 reports the wiper has hit the bottom limit and stopped
                            if (!response.body().wiperMoving) {
                                isAutoCleaning = false; // Stop polling
                                updateMotorStatus("Ready"); // Reset the UI!
                            }
                        }

                        @Override
                        public void onFailure(Call<SolarStatus> call, Throwable t) {
                            // Ignore occasional network drops during polling
                        }
                    });
                }
                statusHandler.postDelayed(this, 2000); // Check every 2 seconds
            }
        };
        statusHandler.post(statusPoller);

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
            Log.e("CleaningFragment", "Retrofit Init Error", e);
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

    private void sendRunCleanCycleCommand() {
        if (apiService == null) {
            Snackbar.make(requireView(), "Not connected to ESP32", Snackbar.LENGTH_SHORT).show();
            return;
        }

        updateMotorStatus("Auto Clean Cycle ACTIVE");

        apiService.runCleaningCycle(1).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    // --- SECTION C UPDATE: Tell the poller to start watching! ---
                    isAutoCleaning = true;
                    Snackbar.make(requireView(), "✓ Auto Clean Cycle Started", Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(requireView(),
                            "Command failed: " + response.code(),
                            Snackbar.LENGTH_SHORT).show();
                    updateMotorStatus("Ready");
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;

                updateMotorStatus("Command failed");
                Snackbar.make(requireView(),
                                "Connection error. Check WiFi.",
                                Snackbar.LENGTH_LONG)
                        .setAction("RETRY", v -> sendRunCleanCycleCommand())
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

    // --- SECTION D UPDATES: Lifecycle Management ---
    @Override
    public void onResume() {
        super.onResume();
        if (statusHandler != null && statusPoller != null) {
            statusHandler.post(statusPoller);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (statusHandler != null && statusPoller != null) {
            statusHandler.removeCallbacks(statusPoller); // Stop polling when fragment isn't visible
        }

        // Safety: Stop all motors when leaving the fragment
        if (isMotorActive) {
            sendMotorCommand(activeMotor, 0);
            Toast.makeText(getContext(), "Motors stopped (fragment paused)", Toast.LENGTH_SHORT).show();
        }
    }
}