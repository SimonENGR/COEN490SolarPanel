package com.example.coen490solarpanel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class EfficiencyFragment extends Fragment {

    private TextView tvEnergyExpense;
    private TextView tvCleaningCycle;
    private TextView tvPanelTilting;
    private TextView tvSystemUpkeep;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_efficiency, container, false);

        // Initialize views
        tvEnergyExpense = view.findViewById(R.id.tv_energy_expense);
        tvCleaningCycle = view.findViewById(R.id.tv_cleaning_cycle);
        tvPanelTilting = view.findViewById(R.id.tv_panel_tilting);
        tvSystemUpkeep = view.findViewById(R.id.tv_system_upkeep);

        // Set efficiency data
        updateEfficiencyData();

        return view;
    }

    private void updateEfficiencyData() {
        // Placeholder values - these would come from your actual data
        double cleaningCycle = 12.5; // kWh
        double panelTilting = 8.2;   // kWh
        double systemUpkeep = 15.3;  // kWh
        double totalEnergyExpense = cleaningCycle + panelTilting + systemUpkeep;

        // Update UI
        tvEnergyExpense.setText(String.format("Energy Expense: %.1f kWh", totalEnergyExpense));
        tvCleaningCycle.setText(String.format("- Cleaning Cycle: %.1f kWh", cleaningCycle));
        tvPanelTilting.setText(String.format("- Panel Tilting: %.1f kWh", panelTilting));
        tvSystemUpkeep.setText(String.format("- System Upkeep: %.1f kWh", systemUpkeep));
    }
}