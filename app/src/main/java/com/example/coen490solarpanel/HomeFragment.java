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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
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
    private SwitchMaterial toggleMode;
    private Button btnSync;

    // --- Networking & Logic ---
    private SolarApiService apiService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    // --- GPS Location ---
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showSyncConfirmationDialog();
                } else {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Location permission needed", Toast.LENGTH_LONG).show();
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

        tvMonitoringStatus = view.findViewById(R.id.tv_monitoring_status);
        tvUptime = view.findViewById(R.id.tv_uptime);
        tvTimeSinceCleaning = view.findViewById(R.id.tv_time_since_cleaning);
        tvHorizontalTilt = view.findViewById(R.id.tv_horizontal_tilt);
        tvAzimuth = view.findViewById(R.id.tv_azimuth);
        toggleMode = view.findViewById(R.id.toggle_mode);
        btnSync = view.findViewById(R.id.btn_sync);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // --- DYNAMIC IP SETUP (From Provisioning) ---
        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        String espIp = prefs.getString("esp_ip", "192.168.4.1");

        // Prevent trailing slash error if user entered it weirdly, though BLE sends it clean usually
        String baseUrl = "http://" + espIp + "/";

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(SolarApiService.class);
        } catch (Exception e) {
            Log.e("HomeFragment", "Retrofit Init Error", e);
        }

        btnSync.setOnClickListener(v -> handleSyncButtonClick());

        toggleMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                toggleManualMode(isChecked);
            }
        });

        updateHomeData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        statusPoller.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        handler.removeCallbacks(statusPoller);
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
        new AlertDialog.Builder(getContext())
                .setTitle("Sync Location?")
                .setMessage("This will send GPS data to the ESP32.")
                .setPositiveButton("Yes, Sync", (dialog, which) -> getUserLocationAndSend())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void getUserLocationAndSend() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        Toast.makeText(getContext(), "Acquiring GPS...", Toast.LENGTH_SHORT).show();

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    // CRASH FIX: Check context inside callback
                    if (getContext() == null) return;

                    if (location != null) {
                        sendToESP32(location);
                    } else {
                        Toast.makeText(getContext(), "GPS signal not found.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "GPS Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendToESP32(Location location) {
        long timestamp = System.currentTimeMillis() / 1000;
        LocationPayload payload = new LocationPayload(
                location.getLatitude(),
                location.getLongitude(),
                timestamp
        );

        if (apiService == null) return;

        apiService.updateLocation(payload).enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                // CRASH FIX: Check context
                if (getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(getContext(), "ESP32: " + response.body().message, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), "Sync Failed.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                // CRASH FIX: Check context
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Connection Failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchSolarStatus() {
        if (apiService == null) return;

        apiService.getStatus().enqueue(new Callback<SolarStatus>() {
            @Override
            public void onResponse(Call<SolarStatus> call, Response<SolarStatus> response) {
                // CRASH FIX: STOP if fragment is detached
                if (getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    SolarStatus s = response.body();

                    tvHorizontalTilt.setText(String.format("Elevation: %.1f°", s.elevation));
                    tvAzimuth.setText(String.format("Azimuth: %.1f°", s.azimuth));

                    if (s.override) {
                        tvMonitoringStatus.setText("MANUAL MODE");
                        tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        toggleMode.setChecked(true);
                    } else {
                        tvMonitoringStatus.setText("AUTO TRACKING");
                        tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        toggleMode.setChecked(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<SolarStatus> call, Throwable t) {
                // CRASH FIX: STOP if fragment is detached
                if (getContext() == null) return;

                // Now it is safe to use getResources()
                tvMonitoringStatus.setText("OFFLINE / DISCONNECTED");
                tvMonitoringStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });
    }

    private void toggleManualMode(boolean isManual) {
        if (apiService == null) return;

        apiService.toggleMode(isManual ? 1 : 0).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (getContext() == null) return; // Safety check
                String mode = isManual ? "Manual Mode ON" : "Auto Mode ON";
                Toast.makeText(getContext(), mode, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                if (getContext() == null) return; // Safety check
                Toast.makeText(getContext(), "Failed to switch mode", Toast.LENGTH_SHORT).show();
                toggleMode.setChecked(!isManual);
            }
        });
    }

    private void updateHomeData() {
        tvUptime.setText("System Uptime: Syncing...");
        tvTimeSinceCleaning.setText("Last Cleaning: ...");
    }
}