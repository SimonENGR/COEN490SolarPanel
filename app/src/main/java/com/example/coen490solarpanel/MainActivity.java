package com.example.coen490solarpanel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements ProvisioningFragment.OnProvisioningListener {

    private Toolbar toolbar;
    private BottomNavigationView bottomNav;

    // Request code for permissions
    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        bottomNav = findViewById(R.id.bottom_navigation);

        // Initialize Navigation Logic (but keep hidden for now)
        setupBottomNavigation();

        // Check Permissions first. If granted, show Provisioning. If not, request them.
        if (hasPermissions()) {
            showProvisioningFlow();
        } else {
            requestBluetoothPermissions();
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showProvisioningFlow();
            }
        }
    }

    private void showProvisioningFlow() {
        // Hide Main Navigation
        bottomNav.setVisibility(View.GONE);
        if(getSupportActionBar() != null) getSupportActionBar().hide();

        // Load Provisioning Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ProvisioningFragment())
                .commit();
    }

    // --- Callback from ProvisioningFragment ---
    @Override
    public void onProvisioningSuccess() {
        runOnUiThread(() -> {
            // Restore UI
            bottomNav.setVisibility(View.VISIBLE);
            if(getSupportActionBar() != null) {
                getSupportActionBar().show();
                getSupportActionBar().setTitle("Home");
            }

            // Load Home Fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.navigation_home);
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String title = "";
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
                title = "Home";
            } else if (itemId == R.id.navigation_efficiency) {
                selectedFragment = new EfficiencyFragment();
                title = "Efficiency";
            } else if (itemId == R.id.navigation_status) {
                selectedFragment = new StatusFragment();
                title = "Status";
            } else if (itemId == R.id.navigation_weather) {
                selectedFragment = new WeatherFragment();
                title = "Weather";
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                }
            }
            return true;
        });
    }
}