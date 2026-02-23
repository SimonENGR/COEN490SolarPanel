package com.example.coen490solarpanel;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ProvisioningFragment.OnProvisioningListener {

    private static final String TAG = "MainActivity";

    // BLE UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHAR_STATUS_UUID = UUID.fromString("e97c992c-559d-48d6-96b0-754784411135");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Toolbar toolbar;
    private BottomNavigationView bottomNav;
    private ProgressBar progressBar;
    private TextView tvSplashStatus;
    private View splashScreen;

    // BLE
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanning = false;
    private boolean foundDevice = false;

    // HTTP Network Scanning
    private ExecutorService executorService;

    // Request code for permissions
    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        bottomNav = findViewById(R.id.bottom_navigation);
        progressBar = findViewById(R.id.pb_splash);
        tvSplashStatus = findViewById(R.id.tv_splash_status);
        splashScreen = findViewById(R.id.splash_screen);

        executorService = Executors.newSingleThreadExecutor();

        // Setup navigation (but keep hidden initially)
        setupBottomNavigation();

        // Check Permissions
        if (hasPermissions()) {
            startSmartDiscovery();
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
                startSmartDiscovery();
            }
        }
    }

    /**
     * DUAL-MODE DISCOVERY
     *
     * Strategy 1: Try BLE first (5 seconds)
     * Strategy 2: If BLE fails, scan local network via HTTP (10 seconds)
     * Strategy 3: If both fail, show provisioning
     */
    private void startSmartDiscovery() {
        runOnUiThread(() -> {
            tvSplashStatus.setText("Looking for ESP32...\n(BLE Mode)");
            progressBar.setVisibility(View.VISIBLE);
            splashScreen.setVisibility(View.VISIBLE);
        });

        // Step 1: Try BLE for 5 seconds
        tryBLEDiscovery();
    }

    private void tryBLEDiscovery() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            runOnUiThread(() -> tvSplashStatus.setText("⚠ Bluetooth disabled\nTrying network scan..."));
            handler.postDelayed(this::tryNetworkDiscovery, 1000);
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (!scanning && hasPermissions()) {
            // Scan for 5 seconds only
            handler.postDelayed(() -> {
                if (scanning && bluetoothLeScanner != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    bluetoothLeScanner.stopScan(scanCallback);
                    scanning = false;

                    if (!foundDevice) {
                        Log.d(TAG, "BLE scan failed, trying network discovery");
                        runOnUiThread(() -> tvSplashStatus.setText("BLE not found\nScanning WiFi network..."));
                        tryNetworkDiscovery();
                    }
                }
            }, 5000);

            scanning = true;
            foundDevice = false;
            bluetoothLeScanner.startScan(scanCallback);
            Log.d(TAG, "BLE Scan started");
        }
    }

    /**
     * HTTP Network Discovery
     * Scans the local network for ESP32 by trying common IP addresses
     */
    private void tryNetworkDiscovery() {
        executorService.execute(() -> {
            try {
                // Get our IP address
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                String myIp = Formatter.formatIpAddress(ipAddress);

                Log.d(TAG, "Phone IP: " + myIp);

                // Extract subnet (e.g., "192.168.1" from "192.168.1.100")
                String subnet = myIp.substring(0, myIp.lastIndexOf("."));

                runOnUiThread(() -> tvSplashStatus.setText("Scanning network...\n" + subnet + ".x"));

                // Scan common IP addresses (1-254)
                for (int i = 1; i <= 254 && !foundDevice; i++) {
                    String testIp = subnet + "." + i;

                    // Update UI every 10 IPs
                    if (i % 10 == 0) {
                        int finalI = i;
                        runOnUiThread(() -> tvSplashStatus.setText("Scanning network...\n" + subnet + "." + finalI + "/254"));
                    }

                    // Try to connect to http://IP/discover
                    if (checkESP32AtIP(testIp)) {
                        foundDevice = true;
                        String finalIp = testIp;

                        runOnUiThread(() -> {
                            tvSplashStatus.setText("✓ ESP32 Found!\nIP: " + finalIp);

                            // Save IP
                            SharedPreferences prefs = getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
                            prefs.edit().putString("esp_ip", finalIp).apply();

                            handler.postDelayed(() -> loadMainApp(), 1000);
                        });

                        return;
                    }
                }

                // If we get here, nothing was found
                if (!foundDevice) {
                    runOnUiThread(() -> {
                        tvSplashStatus.setText("ESP32 not found\nStarting setup...");
                        handler.postDelayed(() -> showProvisioningFlow(), 1500);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Network scan error", e);
                runOnUiThread(() -> showProvisioningFlow());
            }
        });
    }

    /**
     * Check if ESP32 is at the given IP address
     */
    private boolean checkESP32AtIP(String ip) {
        try {
            // First check if host is reachable (faster than HTTP timeout)
            InetAddress address = InetAddress.getByName(ip);
            if (!address.isReachable(100)) {  // 100ms timeout
                return false;
            }

            // Try HTTP request to /discover endpoint
            URL url = new URL("http://" + ip + "/discover");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(500);  // 500ms timeout
            conn.setReadTimeout(500);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                // Read response
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parse JSON
                JSONObject json = new JSONObject(response.toString());
                String status = json.optString("status", "");
                String device = json.optString("device", "");

                Log.d(TAG, "Found device at " + ip + ": " + device);

                // Check if it's our ESP32
                if (status.equals("ready") && device.equals("ESP32-Solar-Online")) {
                    return true;
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            // Silently fail - this IP doesn't have ESP32
        }

        return false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (foundDevice) return;

            BluetoothDevice device = result.getDevice();
            String name = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermissions()) {
                name = device.getName();
            } else if (hasPermissions()) {
                name = device.getName();
            }

            if (name == null) return;

            Log.d(TAG, "Found BLE device: " + name);

            if (name.equals("ESP32-Solar-Online")) {
                Log.d(TAG, "Found ESP32-Solar-Online via BLE");
                foundDevice = true;

                if (scanning && bluetoothLeScanner != null && hasPermissions()) {
                    bluetoothLeScanner.stopScan(scanCallback);
                    scanning = false;
                }

                connectToOnlineESP32(device);
            }
            else if (name.equals("ESP32-Solar-Prov")) {
                Log.d(TAG, "Found ESP32-Solar-Prov - needs provisioning");
                foundDevice = true;

                if (scanning && bluetoothLeScanner != null && hasPermissions()) {
                    bluetoothLeScanner.stopScan(scanCallback);
                    scanning = false;
                }

                showProvisioningFlow();
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToOnlineESP32(BluetoothDevice device) {
        runOnUiThread(() -> {
            tvSplashStatus.setText("✓ ESP32 Found via BLE!\nReading IP...");
        });

        if (!hasPermissions()) return;

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to ESP32 via BLE");
                    if (hasPermissions()) {
                        handler.postDelayed(() -> {
                            if (hasPermissions()) {
                                gatt.discoverServices();
                            }
                        }, 500);
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic statusChar = service.getCharacteristic(CHAR_STATUS_UUID);
                        if (statusChar != null && hasPermissions()) {
                            gatt.setCharacteristicNotification(statusChar, true);

                            BluetoothGattDescriptor descriptor = statusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }

                            handler.postDelayed(() -> {
                                if (hasPermissions()) {
                                    gatt.readCharacteristic(statusChar);
                                }
                            }, 500);
                        }
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (CHAR_STATUS_UUID.equals(characteristic.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                    String statusMessage = new String(characteristic.getValue());
                    handleStatusMessage(gatt, statusMessage);
                }
            }
        });
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void handleStatusMessage(BluetoothGatt gatt, String statusMessage) {
        if (statusMessage.startsWith("READY:")) {
            String ipAddress = statusMessage.substring(6);
            Log.d(TAG, "ESP32 IP from BLE: " + ipAddress);

            SharedPreferences prefs = getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("esp_ip", ipAddress).apply();

            if (hasPermissions() && gatt != null) {
                gatt.disconnect();
                handler.postDelayed(() -> {
                    if (gatt != null) gatt.close();
                }, 500);
            }

            runOnUiThread(() -> {
                tvSplashStatus.setText("✓ Connected!\nIP: " + ipAddress);
                handler.postDelayed(this::loadMainApp, 1000);
            });
        }
    }

    private void showProvisioningFlow() {
        runOnUiThread(() -> {
            splashScreen.setVisibility(View.GONE);
            bottomNav.setVisibility(View.GONE);
            if (getSupportActionBar() != null) getSupportActionBar().hide();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ProvisioningFragment())
                    .commit();
        });
    }

    private void loadMainApp() {
        splashScreen.setVisibility(View.GONE);
        bottomNav.setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
            getSupportActionBar().setTitle("Home");
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
        bottomNav.setSelectedItemId(R.id.navigation_home);
    }

    @Override
    public void onProvisioningSuccess() {
        loadMainApp();
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (executorService != null) {
            executorService.shutdown();
        }
        if (bluetoothGatt != null) {
            if (hasPermissions()) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }
}
