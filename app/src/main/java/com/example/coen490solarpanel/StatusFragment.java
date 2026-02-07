package com.example.coen490solarpanel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class StatusFragment extends Fragment {

    // --- UI Elements ---
    private TextView tvBatteryStatus;
    private TextView tvBatterySensor;
    private TextView tvPanelSensor;
    private TextView tvCurrentEnergy;
    private TextView tvMeanEnergy;

    // --- Motor Buttons ---
    private Button btnCleanUp, btnCleanDown;
    private Button btnTiltUp, btnTiltDown;
    private Button btnStopAll;

    // --- Networking ---
    private SolarApiService apiService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        // 1. Initialize Status Views
        tvBatteryStatus = view.findViewById(R.id.tv_battery_status);
        tvBatterySensor = view.findViewById(R.id.tv_battery_sensor);
        tvPanelSensor = view.findViewById(R.id.tv_panel_sensor);
        tvCurrentEnergy = view.findViewById(R.id.tv_current_energy);
        tvMeanEnergy = view.findViewById(R.id.tv_mean_energy);

        // 2. Initialize Motor Buttons
        btnCleanUp = view.findViewById(R.id.btn_clean_up);
        btnCleanDown = view.findViewById(R.id.btn_clean_down);
        btnTiltUp = view.findViewById(R.id.btn_tilt_up);
        btnTiltDown = view.findViewById(R.id.btn_tilt_down);
        btnStopAll = view.findViewById(R.id.btn_stop_all);

        // 3. Setup Network Connection
        setupApiService();

        // 4. Set Button Listeners (1 = Up/Fwd, -1 = Down/Rev, 0 = Stop)
        btnCleanUp.setOnClickListener(v -> sendMotorCommand("clean", 1));
        btnCleanDown.setOnClickListener(v -> sendMotorCommand("clean", -1));

        btnTiltUp.setOnClickListener(v -> sendMotorCommand("tilt", 1));
        btnTiltDown.setOnClickListener(v -> sendMotorCommand("tilt", -1));

        btnStopAll.setOnClickListener(v -> sendMotorCommand("all", 0));

        // 5. Populate Data
        updateStatusData();

        return view;
    }

    private void setupApiService() {
        if (getContext() == null) return;

        // Retrieve the IP address saved during provisioning
        SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
        String espIp = prefs.getString("esp_ip", "192.168.4.1"); // Default fallback

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://" + espIp + "/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(SolarApiService.class);
        } catch (Exception e) {
            Log.e("StatusFragment", "Retrofit Init Error", e);
        }
    }

    private void sendMotorCommand(String type, int dir) {
        if (apiService == null) {
            Toast.makeText(getContext(), "Error: Not connected to ESP32", Toast.LENGTH_SHORT).show();
            return;
        }

        // Visual feedback
        String directionText = (dir == 1) ? "FWD/UP" : (dir == -1) ? "REV/DOWN" : "STOP";
        Toast.makeText(getContext(), "Sending: " + type + " " + directionText, Toast.LENGTH_SHORT).show();

        // Send API Request
        apiService.controlMotor(type, dir).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (getContext() == null) return;

                if (response.isSuccessful()) {
                    Log.d("MotorControl", "Success: " + response.body());
                } else {
                    Toast.makeText(getContext(), "Command Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Connection Error. Check WiFi.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatusData() {
        // Placeholder values matching your previous file
        double currentCharge = 7.8; // kWh
        double totalCapacity = 12.0; // kWh
        double batteryPercentage = (currentCharge / totalCapacity) * 100;

        tvBatteryStatus.setText(String.format(
                "Battery Status: %.1f kWh / %.1f kWh (%.0f%%)",
                currentCharge, totalCapacity, batteryPercentage
        ));

        tvBatterySensor.setText("Battery Sensor: ✓ Operational");
        tvPanelSensor.setText("Panel Sensor: ✓ Operational");
        tvCurrentEnergy.setText("Current Energy Harvest: 2.4 kWh/hr");
        tvMeanEnergy.setText("Mean Energy Harvest (Past 7 days): 1.8 kWh/hr");
    }
}