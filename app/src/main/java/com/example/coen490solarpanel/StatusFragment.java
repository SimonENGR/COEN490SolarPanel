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
import android.widget.EditText;
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

public class StatusFragment extends Fragment {

    // ----------------------------------------------------------------
    // UI — Tilt section
    // ----------------------------------------------------------------
    private TextView tvMotorStatus;
    private TextView tvTiltAngle;

    private Button btnCleanUp, btnCleanDown;   // hold-to-operate (raw IBT-2)
    private Button btnTiltUp,  btnTiltDown;
    private Button btnStopAll;

    private Button   btnMinus1, btnMinus01, btnPlus01, btnPlus1, btnGoAngle;
    private EditText etTargetAngle;

    // ----------------------------------------------------------------
    // UI — Wiper section
    // ----------------------------------------------------------------
    private TextView tvWiperPosition;
    private Button   btnFullClean;

    // ----------------------------------------------------------------
    // Networking
    // ----------------------------------------------------------------
    private SolarApiService apiService;
    private Vibrator        vibrator;

    // ----------------------------------------------------------------
    // Motor state tracking
    // ----------------------------------------------------------------
    private boolean isMotorActive = false;
    private String  activeMotor   = "";

    // ----------------------------------------------------------------
    // Polling handler
    // ----------------------------------------------------------------
    private final android.os.Handler pollHandler = new android.os.Handler();
    private Runnable statusPoller;

    // ================================================================
    // Fragment lifecycle
    // ================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_status, container, false);

        // --- Tilt UI ---
        tvMotorStatus = view.findViewById(R.id.tv_motor_status);
        tvTiltAngle   = view.findViewById(R.id.tv_tilt_angle);

        btnCleanUp    = view.findViewById(R.id.btn_clean_up);
        btnCleanDown  = view.findViewById(R.id.btn_clean_down);
        btnTiltUp     = view.findViewById(R.id.btn_tilt_up);
        btnTiltDown   = view.findViewById(R.id.btn_tilt_down);
        btnStopAll    = view.findViewById(R.id.btn_stop_all);

        btnMinus1     = view.findViewById(R.id.btn_minus_1);
        btnMinus01    = view.findViewById(R.id.btn_minus_01);
        btnPlus01     = view.findViewById(R.id.btn_plus_01);
        btnPlus1      = view.findViewById(R.id.btn_plus_1);
        btnGoAngle    = view.findViewById(R.id.btn_go_angle);
        etTargetAngle = view.findViewById(R.id.et_target_angle);

        // --- Wiper UI ---
        tvWiperPosition = view.findViewById(R.id.tv_wiper_position);
        btnFullClean    = view.findViewById(R.id.btn_full_clean);

        // --- Vibrator ---
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        // --- Network ---
        setupApiService();

        // ----------------------------------------------------------------
        // Hold-to-operate buttons
        // ----------------------------------------------------------------
        setupHoldToOperateButton(btnCleanUp,   "clean",  1, "Cleaning ↑");
        setupHoldToOperateButton(btnCleanDown, "clean", -1, "Cleaning ↓");
        setupHoldToOperateButton(btnTiltUp,    "tilt",   1, "Tilting ↑");
        setupHoldToOperateButton(btnTiltDown,  "tilt",  -1, "Tilting ↓");

        // Emergency stop
        btnStopAll.setOnClickListener(v -> {
            vibrateDevice(100);
            sendMotorCommand("all", 0);
        });
        btnStopAll.setOnLongClickListener(v -> {
            showEmergencyStopDialog();
            return true;
        });

        // ----------------------------------------------------------------
        // Precision tilt controls
        // ----------------------------------------------------------------
        btnMinus1.setOnClickListener(v ->  { vibrateDevice(30); sendAngleNudge(-1.0f); });
        btnMinus01.setOnClickListener(v -> { vibrateDevice(30); sendAngleNudge(-0.1f); });
        btnPlus01.setOnClickListener(v ->  { vibrateDevice(30); sendAngleNudge(0.1f);  });
        btnPlus1.setOnClickListener(v ->   { vibrateDevice(30); sendAngleNudge(1.0f);  });

        btnGoAngle.setOnClickListener(v -> {
            String text = etTargetAngle.getText().toString().trim();
            if (!text.isEmpty()) {
                vibrateDevice(50);
                sendAngleTarget(Float.parseFloat(text));
            } else {
                Toast.makeText(getContext(), "Enter a target angle", Toast.LENGTH_SHORT).show();
            }
        });

        // ----------------------------------------------------------------
        // Full clean button
        // ----------------------------------------------------------------
        btnFullClean.setOnClickListener(v -> {
            vibrateDevice(80);
            showFullCleanConfirmDialog();
        });

        // ----------------------------------------------------------------
        // Initial display state
        // ----------------------------------------------------------------
        updateMotorStatus("Ready");
        tvWiperPosition.setText("Wiper Actuation");

        // ----------------------------------------------------------------
        // Status poller — fetches tilt angle and wiper position
        // ----------------------------------------------------------------
        statusPoller = new Runnable() {
            @Override
            public void run() {
                fetchStatus();
                int interval = isMotorActive ? 500 : 2000;
                pollHandler.postDelayed(this, interval);
            }
        };
        pollHandler.post(statusPoller);

        return view;
    }

    // ================================================================
    // Networking setup
    // ================================================================

    private void setupApiService() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        String espIp = prefs.getString("esp_ip", "192.168.4.1");
        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://" + espIp + "/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(SolarApiService.class);
        } catch (Exception e) {
            Log.e("StatusFragment", "Retrofit init error", e);
        }
    }

    // ================================================================
    // Status polling
    // ================================================================

    private void fetchStatus() {
        if (apiService == null) return;

        apiService.getStatus().enqueue(new Callback<SolarStatus>() {
            @Override
            public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                if (getContext() == null || response.body() == null) return;
                SolarStatus status = response.body();

                if (tvTiltAngle != null) {
                    tvTiltAngle.setText(
                            String.format("Tilt Angle: %.2f°", status.tiltAngle));
                }

                if (tvWiperPosition != null) {
                    tvWiperPosition.setText("Wiper Actuation");
                }

                // Surface stall to the user
                if (status.wiperStalled) {
                    updateMotorStatus("⚠ Wiper STALLED");
                    Snackbar.make(requireView(),
                                    "⚠ Wiper stalled — check for obstructions, then press Stop All to reset.",
                                    Snackbar.LENGTH_INDEFINITE)
                            .setAction("STOP ALL", v -> {
                                vibrateDevice(200);
                                sendMotorCommand("all", 0);
                            })
                            .show();
                }
            }

            @Override
            public void onFailure(Call<SolarStatus> call, Throwable t) {
                Log.e("StatusFragment", "Status fetch failed", t);
            }
        });
    }

    // ================================================================
    // Tilt motor methods
    // ================================================================

    private void setupHoldToOperateButton(Button button, String motorType,
                                          int direction, String label) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    vibrateDevice(50);
                    sendMotorCommand(motorType, direction);
                    updateMotorStatus(label + " ACTIVE");
                    activeMotor   = motorType;
                    isMotorActive = true;
                    button.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    vibrateDevice(30);
                    sendMotorCommand(motorType, 0);
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
                vibrator.vibrate(VibrationEffect.createOneShot(
                        duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    private void sendMotorCommand(String type, int dir) {
        if (apiService == null) {
            Snackbar.make(requireView(), "Not connected to ESP32",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        apiService.controlMotor(type, dir).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call,
                                   Response<SyncResponse> response) {
                if (getContext() == null) return;
                if (!response.isSuccessful() || response.body() == null) {
                    Snackbar.make(requireView(),
                            "Command failed: " + response.code(),
                            Snackbar.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                Snackbar.make(requireView(), "Connection error. Check WiFi.",
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
                    Snackbar.make(requireView(), "✓ All motors stopped",
                            Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateMotorStatus(String status) {
        if (tvMotorStatus == null) return;
        tvMotorStatus.setText("Motor Status: " + status);
        if (status.contains("ACTIVE")) {
            tvMotorStatus.setTextColor(
                    getResources().getColor(android.R.color.holo_orange_dark));
        } else if (status.equals("Ready")) {
            tvMotorStatus.setTextColor(
                    getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvMotorStatus.setTextColor(
                    getResources().getColor(android.R.color.primary_text_light));
        }
    }

    private void sendAngleNudge(float delta) {
        if (apiService == null) return;
        updateMotorStatus(String.format("Nudging %+.1f°...", delta));
        apiService.nudgeAngle(delta).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call,
                                   Response<SyncResponse> response) {
                if (getContext() == null) return;
                updateMotorStatus("Positioning...");
                pollHandler.postDelayed(() -> {
                    fetchStatus();
                    updateMotorStatus("Ready");
                }, 2000);
            }
            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
                Log.e("StatusFragment", "Nudge failed", t);
            }
        });
    }

    private void sendAngleTarget(float target) {
        if (apiService == null) return;
        updateMotorStatus(String.format("Going to %.1f°...", target));
        apiService.setAngle(target).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call,
                                   Response<SyncResponse> response) {
                if (getContext() == null) return;
                updateMotorStatus("Positioning...");
                pollHandler.postDelayed(() -> {
                    fetchStatus();
                    updateMotorStatus("Ready");
                }, 3000);
            }
            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
                Log.e("StatusFragment", "SetAngle failed", t);
            }
        });
    }

    // ================================================================
    // Full clean cycle
    // ================================================================

    private void showFullCleanConfirmDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("🧹 Start Cleaning Cycle")
                .setMessage("The wiper will travel up, back down, then return to the top (rest position). Continue?")
                .setPositiveButton("START", (dialog, which) -> {
                    vibrateDevice(80);
                    triggerFullClean();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void triggerFullClean() {
        if (apiService == null) return;
        updateMotorStatus("Full clean cycle running...");
        apiService.triggerFullClean(1).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call,
                                   Response<SyncResponse> response) {
                if (getContext() == null) return;
                Snackbar.make(requireView(),
                        "✓ Clean cycle started — wiper returning to top",
                        Snackbar.LENGTH_LONG).show();
                pollHandler.postDelayed(() -> {
                    fetchStatus();
                    updateMotorStatus("Ready");
                }, 15000);
            }
            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Clean cycle failed");
                Log.e("StatusFragment", "Full clean trigger failed", t);
            }
        });
    }

    // ================================================================
    // Fragment lifecycle
    // ================================================================

    @Override
    public void onResume() {
        super.onResume();
        pollHandler.removeCallbacks(statusPoller);
        pollHandler.post(statusPoller);
    }

    @Override
    public void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(statusPoller);
        if (isMotorActive) {
            sendMotorCommand(activeMotor, 0);
            Toast.makeText(getContext(), "Motors stopped (fragment paused)",
                    Toast.LENGTH_SHORT).show();
        }
    }
}