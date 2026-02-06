package com.example.coen490solarpanel;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.SharedPreferences;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProvisioningFragment extends Fragment {

    // UUIDs matching your ESP32 Code
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHAR_SSID_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID CHAR_PASS_UUID = UUID.fromString("82544256-1d18-4066-976d-1d6836932486");
    private static final UUID CHAR_STATUS_UUID = UUID.fromString("e97c992c-559d-48d6-96b0-754784411135");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Runnable scanTimeoutRunnable;
    private boolean isProvisioningComplete = false;
    private boolean scanning;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // UI Elements
    private TextView tvStatus;
    private ProgressBar progressBar;
    private LinearLayout layoutCredentials;
    private EditText etSsid, etPass;
    private Button btnConnect;

    private OnProvisioningListener listener;

    public interface OnProvisioningListener {
        void onProvisioningSuccess();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnProvisioningListener) {
            listener = (OnProvisioningListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnProvisioningListener");
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Ensure you have created res/layout/fragment_provisioning.xml
        View view = inflater.inflate(R.layout.fragment_provisioning, container, false);

        tvStatus = view.findViewById(R.id.tv_prov_status);
        progressBar = view.findViewById(R.id.pb_prov_scanning);
        layoutCredentials = view.findViewById(R.id.layout_prov_credentials);
        etSsid = view.findViewById(R.id.et_prov_ssid);
        etPass = view.findViewById(R.id.et_prov_pass);
        btnConnect = view.findViewById(R.id.btn_prov_connect);

        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        btnConnect.setOnClickListener(v -> sendCredentials());

        startScan();

        return view;
    }

    // Helper to check permissions easier
    private boolean hasPermission(String permission) {
        Context context = getContext(); // Use getContext(), not requireContext()
        if (context == null) {
            return false; // Safely return false if the fragment is dead
        }
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(getContext(), "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (!scanning) {

            // 1. Define the timeout logic
            scanTimeoutRunnable = () -> {
                scanning = false;
                if (bluetoothLeScanner != null) {
                    // Safe Check: Only stop if we are actually scanning
                    if (getContext() != null && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                        bluetoothLeScanner.stopScan(leScanCallback);
                        // Optional: Update UI to show scan finished without finding device
                        if (isAdded()) {
                            tvStatus.setText("Scan timed out. Try again.");
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }
            };

            // 2. Start the timer
            handler.postDelayed(scanTimeoutRunnable, 10000);

            scanning = true;
            tvStatus.setText("Scanning for 'ESP32-Solar-Prov'...");

            // 3. Start Scanning
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    bluetoothLeScanner.startScan(leScanCallback);
                }
            } else {
                if (hasPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
                    bluetoothLeScanner.startScan(leScanCallback);
                }
            }
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (getContext() == null) return;

            BluetoothDevice device = result.getDevice();

            // ... (Permission Checks for name) ...
            String name = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                name = device.getName();
            } else if (hasPermission(Manifest.permission.BLUETOOTH)) {
                name = device.getName();
            }

            if (name != null && name.equals("ESP32-Solar-Prov")) {
                if (scanning) {
                    // --- THE FIX IS HERE ---
                    // Cancel the 10-second timer immediately!
                    if (scanTimeoutRunnable != null) {
                        handler.removeCallbacks(scanTimeoutRunnable);
                    }

                    if (bluetoothLeScanner != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) bluetoothLeScanner.stopScan(this);
                        } else {
                            if (hasPermission(Manifest.permission.BLUETOOTH_ADMIN)) bluetoothLeScanner.stopScan(this);
                        }
                    }
                    scanning = false;
                }

                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("Found ESP32! Connecting...");
                    progressBar.setVisibility(View.GONE);
                });

                connectToDevice(device);
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(BluetoothDevice device) {
        // LINT FIX: Explicit check before connectGatt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bluetoothGatt = device.connectGatt(getContext(), false, gattCallback);
            }
        } else {
            if (hasPermission(Manifest.permission.BLUETOOTH)) {
                bluetoothGatt = device.connectGatt(getContext(), false, gattCallback);
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // FIX: If we are already done, or the fragment is dead, STOP.
            if (isProvisioningComplete || getContext() == null) return;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // ... (your existing code) ...
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    gatt.discoverServices();
                } else if (hasPermission(Manifest.permission.BLUETOOTH)) {
                    gatt.discoverServices();
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvStatus.setText("Connected! Discovering Services..."));
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // FIX: Only show error if we weren't expecting a disconnect
                if (!isProvisioningComplete && getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvStatus.setText("Disconnected. Try again."));
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("Connected! Enter WiFi Details.");
                    layoutCredentials.setVisibility(View.VISIBLE);
                });

                // Setup Notifications
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic statusChar = service.getCharacteristic(CHAR_STATUS_UUID);
                    if (statusChar != null) {
                        // LINT FIX: Explicit check before setCharacteristicNotification
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                                gatt.setCharacteristicNotification(statusChar, true);
                            }
                        } else {
                            if (hasPermission(Manifest.permission.BLUETOOTH)) {
                                gatt.setCharacteristicNotification(statusChar, true);
                            }
                        }
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHAR_STATUS_UUID.equals(characteristic.getUuid())) {
                String status = new String(characteristic.getValue());
                requireActivity().runOnUiThread(() -> handleStatusUpdate(status));
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void sendCredentials() {
        String ssid = etSsid.getText().toString();
        String pass = etPass.getText().toString();

        if (ssid.isEmpty() || pass.isEmpty()) {
            Toast.makeText(getContext(), "Please enter SSID and Password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothGatt == null) return;

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic ssidChar = service.getCharacteristic(CHAR_SSID_UUID);
            BluetoothGattCharacteristic passChar = service.getCharacteristic(CHAR_PASS_UUID);

            if (ssidChar != null && passChar != null) {
                // 1. Write SSID
                // LINT FIX: Check permissions before writing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                        ssidChar.setValue(ssid);
                        bluetoothGatt.writeCharacteristic(ssidChar);
                    }
                } else {
                    if (hasPermission(Manifest.permission.BLUETOOTH)) {
                        ssidChar.setValue(ssid);
                        bluetoothGatt.writeCharacteristic(ssidChar);
                    }
                }

                // 2. Write Password (with delay)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                            passChar.setValue(pass);
                            bluetoothGatt.writeCharacteristic(passChar);
                        }
                    } else {
                        if (hasPermission(Manifest.permission.BLUETOOTH)) {
                            passChar.setValue(pass);
                            bluetoothGatt.writeCharacteristic(passChar);
                        }
                    }
                    Toast.makeText(getContext(), "Sending Credentials...", Toast.LENGTH_SHORT).show();
                }, 500);
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void handleStatusUpdate(String status) {

        // Check if the message starts with "IP:" (e.g., "IP:192.168.2.61")
        if (status.startsWith("IP:")) {
            String newIp = status.substring(3); // Remove "IP:" prefix

            // 1. Save IP to SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("esp_ip", newIp).apply();

            // 2. Mark complete
            isProvisioningComplete = true;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Connected to: " + newIp, Toast.LENGTH_LONG).show()
                );
            }

            // 3. Disconnect BLE
            if (bluetoothGatt != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) bluetoothGatt.disconnect();
                } else {
                    if (hasPermission(Manifest.permission.BLUETOOTH)) bluetoothGatt.disconnect();
                }
            }

            // 4. Switch Screens
            if (listener != null) listener.onProvisioningSuccess();

        } else if (status.contains("FAILED")) {
            // ... (keep existing failure code) ...
        } else {
            tvStatus.setText("Status: " + status);
        }
    }
}