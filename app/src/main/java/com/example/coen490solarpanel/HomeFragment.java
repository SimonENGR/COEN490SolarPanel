package com.example.coen490solarpanel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    private TextView tvMonitoringStatus;
    private TextView tvUptime;
    private TextView tvTimeSinceCleaning;
    private TextView tvHorizontalTilt;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize views - these should now be inside the card_monitoring_status
        tvMonitoringStatus = view.findViewById(R.id.tv_monitoring_status);
        tvUptime = view.findViewById(R.id.tv_uptime);
        tvTimeSinceCleaning = view.findViewById(R.id.tv_time_since_cleaning);
        tvHorizontalTilt = view.findViewById(R.id.tv_horizontal_tilt);

        // Set initial values
        updateHomeData();

        return view;
    }

    private void updateHomeData() {
        // Monitoring status
        tvMonitoringStatus.setText("Monitoring is currently ON");

        // Uptime - placeholder value (in hours)
        tvUptime.setText("Monitoring uptime: 156 hours");

        // Time since last cleaning - placeholder value
        tvTimeSinceCleaning.setText("Time since last cleaning: 72 hours");

        // Horizontal tilt - this would ideally come from shared data with WeatherFragment
        // For now, using placeholder
        tvHorizontalTilt.setText("Current Horizontal Tilt: 45°");
    }

    // Method to update tilt from weather data (could be called from MainActivity)
    public void updateTiltFromWeather(double sunAngle) {
        if (tvHorizontalTilt != null) {
            tvHorizontalTilt.setText(String.format("Current Horizontal Tilt: %.1f°", sunAngle));
        }
    }
}