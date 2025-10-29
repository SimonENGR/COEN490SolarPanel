package com.example.coen490solarpanel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class StatusFragment extends Fragment {

    private TextView tvBatteryStatus;
    private TextView tvBatterySensor;
    private TextView tvPanelSensor;
    private TextView tvCurrentEnergy;
    private TextView tvMeanEnergy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        // Initialize views
        tvBatteryStatus = view.findViewById(R.id.tv_battery_status);
        tvBatterySensor = view.findViewById(R.id.tv_battery_sensor);
        tvPanelSensor = view.findViewById(R.id.tv_panel_sensor);
        tvCurrentEnergy = view.findViewById(R.id.tv_current_energy);
        tvMeanEnergy = view.findViewById(R.id.tv_mean_energy);

        // Set status data
        updateStatusData();

        return view;
    }

    private void updateStatusData() {
        // Battery Status - placeholder values
        double currentCharge = 7.8; // kWh
        double totalCapacity = 12.0; // kWh
        double batteryPercentage = (currentCharge / totalCapacity) * 100;

        tvBatteryStatus.setText(String.format(
                "Battery Status: %.1f kWh / %.1f kWh (%.0f%%)",
                currentCharge, totalCapacity, batteryPercentage
        ));

        // Sensor Status - placeholder values
        tvBatterySensor.setText("Battery Sensor: ✓ Operational");
        tvPanelSensor.setText("Panel Sensor: ✓ Operational");

        // Panel Status - placeholder values
        tvCurrentEnergy.setText("Current Energy Harvest: 2.4 kWh/hr");
        tvMeanEnergy.setText("Mean Energy Harvest (Past 7 days): 1.8 kWh/hr");
    }
}