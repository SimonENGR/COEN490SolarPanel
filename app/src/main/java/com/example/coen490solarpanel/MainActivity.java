package com.example.coen490solarpanel;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
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
                // Update toolbar title
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                }
            }
            return true;
        });

        // Set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.navigation_home);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Home");
            }
        }
    }
}