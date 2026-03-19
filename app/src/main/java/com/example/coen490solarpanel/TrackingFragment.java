package com.example.coen490solarpanel;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TrackingFragment extends Fragment {

    private static final String TAG = "TrackingFragment";
    private static final String CHANNEL_ID = "weather_alerts";

    // --- UI Elements ---
    private TextView tvMotorStatus;
    private TextView tvTiltAngle;
    private TextView tvWeatherStatus;

    // --- Mode Selector ---
    private RadioGroup rgModeSelector;
    private RadioButton rbAutomatic, rbSemiAuto, rbManual;

    // --- Manual Controls Container ---
    private LinearLayout layoutManualControls;
    private LinearLayout layoutWeatherPresets;

    // --- Semi-Auto Confirmation ---
    private LinearLayout layoutSemiAutoConfirm;
    private TextView tvConfirmMessage;
    private Button btnConfirmApprove, btnConfirmReject;

    // --- Motor Buttons ---
    private Button btnTiltUp, btnTiltDown;
    private Button btnStopAll;

    // --- Arc Slider ---
    private ArcSliderView arcSlider;

    // --- Weather Preset Buttons ---
    private Button btnWeatherOvercast, btnWeatherRain, btnWeatherSnow, btnWeatherClear;

    // --- Networking ---
    private SolarApiService apiService;
    private Vibrator vibrator;

    // --- Motor State Tracking ---
    private boolean isMotorActive = false;
    private String activeMotor = "";

    // --- Angle Polling ---
    private android.os.Handler angleHandler = new android.os.Handler();
    private Runnable anglePoller;
    private int currentMode = 0;
    private boolean notificationSent = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tracking, container, false);

        // Initialize Status Views
        tvMotorStatus = view.findViewById(R.id.tv_motor_status);
        tvTiltAngle = view.findViewById(R.id.tv_tilt_angle);
        tvWeatherStatus = view.findViewById(R.id.tv_weather_status);

        // Initialize Mode Selector
        rgModeSelector = view.findViewById(R.id.rg_mode_selector);
        rbAutomatic = view.findViewById(R.id.rb_automatic);
        rbSemiAuto = view.findViewById(R.id.rb_semi_auto);
        rbManual = view.findViewById(R.id.rb_manual);

        // Initialize Collapsible Sections
        layoutManualControls = view.findViewById(R.id.layout_manual_controls);
        layoutWeatherPresets = view.findViewById(R.id.layout_weather_presets);
        layoutSemiAutoConfirm = view.findViewById(R.id.layout_semi_auto_confirm);
        tvConfirmMessage = view.findViewById(R.id.tv_confirm_message);
        btnConfirmApprove = view.findViewById(R.id.btn_confirm_approve);
        btnConfirmReject = view.findViewById(R.id.btn_confirm_reject);

        // Initialize Motor Buttons
        btnTiltUp = view.findViewById(R.id.btn_tilt_up);
        btnTiltDown = view.findViewById(R.id.btn_tilt_down);
        btnStopAll = view.findViewById(R.id.btn_stop_all);

        // Initialize Arc Slider
        arcSlider = view.findViewById(R.id.arc_slider);

        // Weather Preset Buttons
        btnWeatherOvercast = view.findViewById(R.id.btn_weather_overcast);
        btnWeatherRain = view.findViewById(R.id.btn_weather_rain);
        btnWeatherSnow = view.findViewById(R.id.btn_weather_snow);
        btnWeatherClear = view.findViewById(R.id.btn_weather_clear);

        // Get Vibrator Service
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        // Create notification channel
        createNotificationChannel();

        // Setup Network Connection
        setupApiService();

        // --- MODE SELECTOR ---
        rgModeSelector.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rb_automatic) {
                mode = 0;
            } else if (checkedId == R.id.rb_semi_auto) {
                mode = 1;
            } else {
                mode = 2;
            }
            vibrateDevice(30);
            sendModeChange(mode);
            updateUiForMode(mode);
        });

        // --- SEMI-AUTO CONFIRMATION ---
        btnConfirmApprove.setOnClickListener(v -> {
            vibrateDevice(50);
            sendWeatherConfirmation(1);
        });

        btnConfirmReject.setOnClickListener(v -> {
            vibrateDevice(50);
            sendWeatherConfirmation(0);
        });

        // --- WEATHER PRESET BUTTONS ---
        btnWeatherOvercast.setOnClickListener(v -> {
            vibrateDevice(30);
            sendWeatherOverride("overcast");
        });
        btnWeatherRain.setOnClickListener(v -> {
            vibrateDevice(30);
            sendWeatherOverride("rain");
        });
        btnWeatherSnow.setOnClickListener(v -> {
            vibrateDevice(30);
            sendWeatherOverride("snow");
        });
        btnWeatherClear.setOnClickListener(v -> {
            vibrateDevice(30);
            sendWeatherOverride("clear");
        });

        // --- ARC SLIDER ---
        arcSlider.setOnAngleChangedListener(new ArcSliderView.OnAngleChangedListener() {
            @Override
            public void onAngleChanged(float angle) {
                // Live preview: update angle display while dragging
                if (tvTiltAngle != null) {
                    tvTiltAngle.setText(String.format("Target: %.1f°", angle));
                }
            }

            @Override
            public void onAngleFinalized(float angle) {
                // User lifted finger — auto-send the target angle immediately
                vibrateDevice(50);
                sendAngleTarget(angle);
                updateMotorStatus(String.format("Going to %.1f°...", angle));
            }
        });

        // --- HOLD-TO-OPERATE TILT BUTTONS ---
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

        updateMotorStatus("Ready");

        // Setup status poller
        anglePoller = new Runnable() {
            @Override
            public void run() {
                fetchStatus();
                int interval = isMotorActive ? 500 : 2000;
                angleHandler.postDelayed(this, interval);
            }
        };

        angleHandler.post(anglePoller);

        return view;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Weather Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Weather-based panel repositioning alerts");
            NotificationManager manager = requireContext().getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
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
            Log.e(TAG, "Retrofit Init Error", e);
        }
    }

    /**
     * Updates the UI visibility based on the selected mode.
     */
    private void updateUiForMode(int mode) {
        currentMode = mode;
        notificationSent = false;

        if (mode == 2) {
            // MANUAL: Show arc slider + weather presets
            layoutManualControls.setVisibility(View.VISIBLE);
            layoutWeatherPresets.setVisibility(View.VISIBLE);
            layoutSemiAutoConfirm.setVisibility(View.GONE);
        } else if (mode == 1) {
            // SEMI-AUTO: Show weather presets, hide manual controls
            layoutManualControls.setVisibility(View.GONE);
            layoutWeatherPresets.setVisibility(View.VISIBLE);
            // Confirmation banner shown/hidden by status polling
        } else {
            // AUTOMATIC: Hide everything
            layoutManualControls.setVisibility(View.GONE);
            layoutWeatherPresets.setVisibility(View.GONE);
            layoutSemiAutoConfirm.setVisibility(View.GONE);
        }
    }

    private void sendModeChange(int mode) {
        if (apiService == null) {
            Snackbar.make(requireView(), "Not connected to ESP32", Snackbar.LENGTH_SHORT).show();
            return;
        }

        apiService.setMode(mode).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    Snackbar.make(requireView(), response.body().message, Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                Snackbar.make(requireView(), "Failed to change mode", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void sendWeatherOverride(String condition) {
        if (apiService == null) return;
        updateMotorStatus("Setting weather: " + condition + "...");

        apiService.setWeatherCondition(condition).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    Snackbar.make(requireView(), response.body().message, Snackbar.LENGTH_SHORT).show();
                }
                updateMotorStatus("Ready");
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
            }
        });
    }

    private void sendWeatherConfirmation(int confirm) {
        if (apiService == null) return;

        apiService.confirmWeather(confirm).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                layoutSemiAutoConfirm.setVisibility(View.GONE);
                notificationSent = false;
                String msg = confirm == 1 ? "✓ Weather override approved" : "✗ Weather override rejected";
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                Snackbar.make(requireView(), "Confirmation failed", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Fetches full status from ESP32 (angle + weather + mode)
     */
    private void fetchStatus() {
        if (apiService == null) return;

        apiService.getStatus().enqueue(new Callback<SolarStatus>() {
            @Override
            public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                if (getContext() == null || response.body() == null) return;

                SolarStatus status = response.body();

                // Update tilt angle (only when not dragging the slider)
                if (tvTiltAngle != null && !arcSlider.isDragging()) {
                    tvTiltAngle.setText(String.format("Tilt Angle: %.2f°", status.tiltAngle));
                    // Sync arc slider with current angle
                    arcSlider.setAngle((float) status.tiltAngle);
                }

                // Update weather status
                if (tvWeatherStatus != null) {
                    String weatherText = "Weather: " + (status.weatherCondition != null ? status.weatherCondition : "Unknown");
                    if (status.weatherOverride >= 0) {
                        weatherText += " → Override: " + status.weatherOverride + "°";
                    }
                    tvWeatherStatus.setText(weatherText);
                }

                // Sync mode selector with ESP32 state
                if (status.mode != currentMode) {
                    currentMode = status.mode;
                    switch (status.mode) {
                        case 0: rbAutomatic.setChecked(true); break;
                        case 1: rbSemiAuto.setChecked(true); break;
                        case 2: rbManual.setChecked(true); break;
                    }
                    updateUiForMode(status.mode);
                }

                // Semi-auto: show confirmation banner if weather is pending
                if (status.mode == 1 && status.weatherPending) {
                    String condition = status.weatherCondition != null ? status.weatherCondition : "Unknown";
                    int angle = status.weatherOverride;
                    tvConfirmMessage.setText("⚠ " + condition + " detected. Move panel to " + angle + "°?");
                    layoutSemiAutoConfirm.setVisibility(View.VISIBLE);

                    if (!notificationSent) {
                        showWeatherNotification(condition, angle);
                        notificationSent = true;
                    }
                } else {
                    layoutSemiAutoConfirm.setVisibility(View.GONE);
                    if (!status.weatherPending) {
                        notificationSent = false;
                    }
                }
            }

            @Override
            public void onFailure(Call<SolarStatus> call, Throwable t) {
                Log.e(TAG, "Status fetch failed", t);
            }
        });
    }

    private void showWeatherNotification(String condition, int angle) {
        if (getContext() == null) return;

        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Weather Change: " + condition)
                    .setContentText("Move panel to " + angle + "°? Open app to approve.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            Intent intent = new Intent(requireContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    requireContext(), 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);

            NotificationManagerCompat notifManager = NotificationManagerCompat.from(requireContext());
            notifManager.notify(1001, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission not granted", e);
        }
    }

    // =============================================
    // MOTOR CONTROL METHODS
    // =============================================

    private void setupHoldToOperateButton(Button button, String motorType, int direction, String label) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    vibrateDevice(50);
                    sendMotorCommand(motorType, direction);
                    updateMotorStatus(label + " ACTIVE");
                    activeMotor = motorType;
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

        String directionText = (dir == 1) ? "↑" : (dir == -1) ? "↓" : "STOP";
        if (dir != 0) {
            Log.d("MotorControl", "Starting: " + type + " " + directionText);
        }

        apiService.controlMotor(type, dir).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("MotorControl", "Success: " + response.body().message);
                } else {
                    Snackbar.make(requireView(), "Command failed: " + response.code(), Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                Snackbar.make(requireView(), "Connection error. Check WiFi.", Snackbar.LENGTH_LONG)
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

            if (status.contains("ACTIVE")) {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (status.equals("Ready")) {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvMotorStatus.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            }
        }
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
                    fetchStatus();
                    updateMotorStatus("Ready");
                }, 3000);
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;
                updateMotorStatus("Command failed");
                Log.e(TAG, "SetAngle failed", t);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        angleHandler.removeCallbacks(anglePoller);
        angleHandler.post(anglePoller);
    }

    @Override
    public void onPause() {
        super.onPause();
        angleHandler.removeCallbacks(anglePoller);
        if (isMotorActive) {
            sendMotorCommand(activeMotor, 0);
            Toast.makeText(getContext(), "Motors stopped (fragment paused)", Toast.LENGTH_SHORT).show();
        }
    }
}
