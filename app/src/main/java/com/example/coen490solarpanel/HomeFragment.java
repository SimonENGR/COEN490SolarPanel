package com.example.coen490solarpanel;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {

    // --- UI Elements ---
    private TextView tvMonitoringStatus;
    private TextView tvUptime;
    private TextView tvTimeSinceCleaning;
    private TextView tvHorizontalTilt;
    private TextView tvAzimuth;
    private TextView tvConnectionStatus;
    private ImageView ivConnectionIndicator;
    private SwitchMaterial toggleMode;
    private Button btnAutoMode;
    private Button btnSync;
    private SwipeRefreshLayout swipeRefreshLayout;

    // --- Networking & Logic ---
    private SolarApiService apiService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES_BEFORE_WARNING = 3;

    // --- GPS Location ---
    private FusedLocationProviderClient fusedLocationClient;
    private long lastSyncTime = 0;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showSyncConfirmationDialog();
                } else {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Location permission needed for GPS sync", Toast.LENGTH_LONG).show();
                }
            });

    private final Runnable statusPoller = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                fetchSolarStatus();
                handler.postDelayed(this, 5000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize views
        tvMonitoringStatus = view.findViewById(R.id.tv_monitoring_status);
        tvUptime = view.findViewById(R.id.tv_uptime);
        tvTimeSinceCleaning = view.findViewById(R.id.tv_time_since_cleaning);
        tvHorizontalTilt = view.findViewById(R.id.tv_horizontal_tilt);
        tvAzimuth = view.findViewById(R.id.tv_azimuth);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        ivConnectionIndicator = view.findViewById(R.id.iv_connection_indicator);
        toggleMode = view.findViewById(R.id.toggle_mode);
        btnAutoMode = view.findViewById(R.id.btn_auto_mode);
        btnSync = view.findViewById(R.id.btn_sync);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // --- DYNAMIC IP SETUP (From Provisioning) ---
        setupRetrofit();

        // --- Swipe to Refresh ---
        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchSolarStatus();
            swipeRefreshLayout.setRefreshing(false);
        });

        // --- Sync Button with cooldown ---
        btnSync.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSyncTime < 5000) { // 5 second cooldown
                Toast.makeText(getContext(), "Please wait before syncing again", Toast.LENGTH_SHORT).show();
                return;
            }
            handleSyncButtonClick();
        });

        // --- Toggle Mode Switch ---
        toggleMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                toggleManualMode(isChecked);
            }
        });

        // --- Auto Mode Button ---
        btnAutoMode.setOnClickListener(v -> {
            toggleManualMode(false);
            toggleMode.setChecked(false);
        });

        // --- Load last sync time ---
        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        lastSyncTime = prefs.getLong("last_sync_time", 0);

        updateHomeData();
        updateConnectionStatus(false); // Start as disconnected

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        consecutiveFailures = 0;
        statusPoller.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        handler.removeCallbacks(statusPoller);
    }

    private void setupRetrofit() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        String espIp = prefs.getString("esp_ip", "192.168.4.1");
        String baseUrl = "http://" + espIp + "/";

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(SolarApiService.class);

            // Update UI with IP
            if (tvConnectionStatus != null) {
                tvConnectionStatus.setText("ESP32 IP: " + espIp);
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Retrofit Init Error", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error connecting to ESP32", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (getContext() == null || ivConnectionIndicator == null) return;

        if (connected) {
            ivConnectionIndicator.setImageResource(android.R.drawable.presence_online);
            consecutiveFailures = 0;
        } else {
            ivConnectionIndicator.setImageResource(android.R.drawable.presence_busy);
        }
    }

    private void handleSyncButtonClick() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            showSyncConfirmationDialog();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void showSyncConfirmationDialog() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        long lastSync = prefs.getLong("last_sync_time", 0);
        String lastSyncText = lastSync > 0 ?
                "Last sync: " + android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", lastSync) :
                "Never synced";

        new AlertDialog.Builder(getContext())
                .setTitle("Sync Location?")
                .setMessage("This will send GPS data to the ESP32.\n\n" + lastSyncText)
                .setPositiveButton("Sync Now", (dialog, which) -> getUserLocationAndSend())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void getUserLocationAndSend() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        Snackbar.make(requireView(), "Acquiring GPS location...", Snackbar.LENGTH_SHORT).show();

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (getContext() == null) return;

                    if (location != null) {
                        sendToESP32(location);
                    } else {
                        Snackbar.make(requireView(),
                                "GPS signal not found. Please try outdoors.",
                                Snackbar.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null)
                        Snackbar.make(requireView(),
                                "GPS Error: " + e.getMessage(),
                                Snackbar.LENGTH_LONG).show();
                });
    }

    private void sendToESP32(Location location) {
        long timestamp = System.currentTimeMillis() / 1000;
        LocationPayload payload = new LocationPayload(
                location.getLatitude(),
                location.getLongitude(),
                timestamp
        );

        if (apiService == null) {
            Snackbar.make(requireView(), "Not connected to ESP32", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Show loading indicator
        btnSync.setEnabled(false);
        btnSync.setText("Syncing...");

        apiService.updateLocation(payload).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;

                btnSync.setEnabled(true);
                btnSync.setText("Sync Location");

                if (response.isSuccessful() && response.body() != null) {
                    lastSyncTime = System.currentTimeMillis();

                    // Save sync time
                    SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
                    prefs.edit().putLong("last_sync_time", lastSyncTime).apply();

                    Snackbar.make(requireView(),
                            "✓ " + response.body().message,
                            Snackbar.LENGTH_LONG).show();
                    updateConnectionStatus(true);
                } else {
                    Snackbar.make(requireView(),
                            "Sync failed. Check connection.",
                            Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;

                btnSync.setEnabled(true);
                btnSync.setText("Sync Location");

                Snackbar.make(requireView(),
                                "Connection failed. Check WiFi.",
                                Snackbar.LENGTH_LONG)
                        .setAction("RETRY", v -> sendToESP32(location))
                        .show();
                updateConnectionStatus(false);
            }
        });
    }

    private void fetchSolarStatus() {
        if (apiService == null || getContext() == null) return;

        apiService.getStatus().enqueue(new Callback<SolarStatus>() {
            @Override
            public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                if (getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    SolarStatus s = response.body();
                    updateConnectionStatus(true);

                    tvHorizontalTilt.setText(String.format("Elevation: %.1f°", s.elevation));
                    tvAzimuth.setText(String.format("Azimuth: %.1f°", s.azimuth));

                    if (s.override) {
                        tvMonitoringStatus.setText("⚠ MANUAL MODE");
                        tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        toggleMode.setChecked(true);
                    } else {
                        tvMonitoringStatus.setText("✓ AUTO TRACKING");
                        tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        toggleMode.setChecked(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<SolarStatus> call, Throwable t) {
                if (getContext() == null) return;

                consecutiveFailures++;
                updateConnectionStatus(false);

                tvMonitoringStatus.setText("✗ OFFLINE / DISCONNECTED");
                tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                // Show warning after multiple failures
                if (consecutiveFailures == MAX_FAILURES_BEFORE_WARNING) {
                    Snackbar.make(requireView(),
                                    "Lost connection to ESP32. Check WiFi.",
                                    Snackbar.LENGTH_LONG)
                            .setAction("SETTINGS", v -> {
                                // Could open WiFi settings or retry provisioning
                            })
                            .show();
                }
            }
        });
    }

    private void toggleManualMode(boolean isManual) {
        if (apiService == null) return;

        apiService.toggleMode(isManual ? 1 : 0).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (getContext() == null) return;

                String mode = isManual ? "Manual Mode ON" : "Auto Mode ON";
                Snackbar.make(requireView(), mode, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (getContext() == null) return;

                Snackbar.make(requireView(),
                        "Failed to switch mode",
                        Snackbar.LENGTH_SHORT).show();
                toggleMode.setChecked(!isManual); // Revert switch
            }
        });
    }

    private void updateHomeData() {
        tvUptime.setText("System Uptime: Connecting...");
        tvTimeSinceCleaning.setText("Last Cleaning: Syncing...");
    }
}
