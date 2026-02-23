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
import android.content.SharedPreferences;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

public class ProvisioningFragment extends Fragment {

    // UUIDs matching ESP32 Code
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHAR_SSID_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID CHAR_PASS_UUID = UUID.fromString("82544256-1d18-4066-976d-1d6836932486");
    private static final UUID CHAR_STATUS_UUID = UUID.fromString("e97c992c-559d-48d6-96b0-754784411135");

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Runnable scanTimeoutRunnable;
    private boolean isProvisioningComplete = false;
    private boolean scanning;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // UI Elements
    private TextView tvStatus;
    private TextView tvStepIndicator;
    private ProgressBar progressBar;
    private LinearLayout layoutCredentials;
    private TextInputLayout tilSsid, tilPassword;
    private EditText etSsid, etPass;
    private Button btnConnect;
    private Button btnRetry;

    private OnProvisioningListener listener;
    private int scanAttempts = 0;
    private static final int MAX_SCAN_ATTEMPTS = 3;

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
        View view = inflater.inflate(R.layout.fragment_provisioning, container, false);

        // Initialize views
        tvStatus = view.findViewById(R.id.tv_prov_status);
        tvStepIndicator = view.findViewById(R.id.tv_step_indicator);
        progressBar = view.findViewById(R.id.pb_prov_scanning);
        layoutCredentials = view.findViewById(R.id.layout_prov_credentials);
        tilSsid = view.findViewById(R.id.til_prov_ssid);
        tilPassword = view.findViewById(R.id.til_prov_password);
        etSsid = view.findViewById(R.id.et_prov_ssid);
        etPass = view.findViewById(R.id.et_prov_pass);
        btnConnect = view.findViewById(R.id.btn_prov_connect);
        btnRetry = view.findViewById(R.id.btn_prov_retry);

        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Real-time SSID validation
        etSsid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    tilSsid.setError("SSID required");
                } else {
                    tilSsid.setError(null);
                }
                updateConnectButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Real-time password validation
        etPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() < 8) {
                    tilPassword.setError("Password must be at least 8 characters");
                } else {
                    tilPassword.setError(null);
                }
                updateConnectButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnConnect.setOnClickListener(v -> sendCredentials());
        btnRetry.setOnClickListener(v -> retryScan());

        updateStepIndicator("Step 1/3: Searching for ESP32...");
        startScan();

        return view;
    }

    private void updateStepIndicator(String step) {
        if (tvStepIndicator != null) {
            tvStepIndicator.setText(step);
        }
    }

    private void updateConnectButton() {
        String ssid = etSsid.getText().toString();
        String pass = etPass.getText().toString();
        btnConnect.setEnabled(ssid.length() > 0 && pass.length() >= 8);
    }

    private boolean hasPermission(String permission) {
        Context context = getContext();
        if (context == null) return false;
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            tvStatus.setText("⚠ Please enable Bluetooth");
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }

        scanAttempts++;
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (!scanning) {
            btnRetry.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);

            scanTimeoutRunnable = () -> {
                scanning = false;
                if (bluetoothLeScanner != null && getContext() != null && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    bluetoothLeScanner.stopScan(leScanCallback);

                    if (isAdded()) {
                        progressBar.setVisibility(View.GONE);

                        if (scanAttempts < MAX_SCAN_ATTEMPTS) {
                            tvStatus.setText("ESP32 not found. Retry " + scanAttempts + "/" + MAX_SCAN_ATTEMPTS);
                            btnRetry.setVisibility(View.VISIBLE);
                        } else {
                            tvStatus.setText("✗ Could not find ESP32 after " + MAX_SCAN_ATTEMPTS + " attempts.\n\nTroubleshooting:\n• Ensure ESP32 is powered on\n• Check if ESP32 is within range\n• Verify Bluetooth is enabled");
                            btnRetry.setVisibility(View.VISIBLE);
                            btnRetry.setText("Try Again");
                        }
                    }
                }
            };

            handler.postDelayed(scanTimeoutRunnable, 10000);
            scanning = true;

            String attemptText = scanAttempts > 1 ? " (Attempt " + scanAttempts + ")" : "";
            tvStatus.setText("🔍 Scanning for 'ESP32-Solar-Prov'..." + attemptText);
            updateStepIndicator("Step 1/3: Searching for ESP32...");

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void retryScan() {
        if (scanAttempts >= MAX_SCAN_ATTEMPTS) {
            scanAttempts = 0; // Reset counter
        }
        startScan();
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (getContext() == null) return;

            BluetoothDevice device = result.getDevice();
            String name = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                name = device.getName();
            } else if (hasPermission(Manifest.permission.BLUETOOTH)) {
                name = device.getName();
            }

            if (name != null && name.equals("ESP32-Solar-Prov")) {
                if (scanning) {
                    if (scanTimeoutRunnable != null) {
                        handler.removeCallbacks(scanTimeoutRunnable);
                    }

                    if (bluetoothLeScanner != null && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                    scanning = false;
                }

                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("✓ ESP32 Found! Connecting...");
                    updateStepIndicator("Step 2/3: Connecting to ESP32...");
                    progressBar.setVisibility(View.GONE);
                });

                connectToDevice(device);
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(BluetoothDevice device) {
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("Provisioning", "Connected. Waiting for discovery...");

                // Use a delay to ensure the stack is ready
                handler.postDelayed(() -> {
                    if (getContext() != null) {
                        boolean started = gatt.discoverServices();
                        Log.d("Provisioning", "Service discovery started: " + started);
                    }
                }, 1000); // 1 second delay works best for many devices

                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("✓ Connected! Readying services...");
                });
            }
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("✓ Ready to configure WiFi");
                    updateStepIndicator("Step 3/3: Enter WiFi credentials");
                    layoutCredentials.setVisibility(View.VISIBLE);

                    // Pre-fill with saved WiFi if available
                    SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
                    String savedSsid = prefs.getString("last_ssid", "");
                    if (!savedSsid.isEmpty()) {
                        etSsid.setText(savedSsid);
                    }
                });

                // Setup Notifications - THIS IS THE CRITICAL PART!
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic statusChar = service.getCharacteristic(CHAR_STATUS_UUID);
                    if (statusChar != null) {
                        // STEP 1: Enable local notifications
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                                gatt.setCharacteristicNotification(statusChar, true);
                            }
                        } else {
                            if (hasPermission(Manifest.permission.BLUETOOTH)) {
                                gatt.setCharacteristicNotification(statusChar, true);
                            }
                        }

                        // STEP 2: Write to descriptor to enable notifications on ESP32 side
                        // THIS IS THE MISSING PART!
                        BluetoothGattDescriptor descriptor = statusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                    Log.d("ProvisioningFragment", "Notification descriptor written");
                                }
                            } else {
                                if (hasPermission(Manifest.permission.BLUETOOTH)) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                    Log.d("ProvisioningFragment", "Notification descriptor written");
                                }
                            }
                        } else {
                            Log.e("ProvisioningFragment", "Descriptor is null!");
                        }
                    } else {
                        Log.e("ProvisioningFragment", "Status characteristic is null!");
                    }
                } else {
                    Log.e("ProvisioningFragment", "Service is null!");
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

        if (ssid.isEmpty() || pass.length() < 8) {
            Toast.makeText(getContext(), "Please check SSID and Password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothGatt == null) {
            Toast.makeText(getContext(), "Not connected to ESP32", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button during send
        btnConnect.setEnabled(false);
        btnConnect.setText("Sending...");
        tvStatus.setText("📡 Sending WiFi credentials to ESP32...");

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic ssidChar = service.getCharacteristic(CHAR_SSID_UUID);
            BluetoothGattCharacteristic passChar = service.getCharacteristic(CHAR_PASS_UUID);

            if (ssidChar != null && passChar != null) {
                // Save SSID for next time
                SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
                prefs.edit().putString("last_ssid", ssid).apply();

                // 1. Write SSID
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
                    tvStatus.setText("⏳ ESP32 connecting to WiFi...\n(This may take 10-20 seconds)");
                }, 500);
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void handleStatusUpdate(String status) {
        isProvisioningComplete = true;
        if (status.startsWith("IP:")) {
            String newIp = status.substring(3);

            // Save IP
            SharedPreferences prefs = requireContext().getSharedPreferences("SolarPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("esp_ip", newIp).apply();

            isProvisioningComplete = true;

            tvStatus.setText("✓ Success! ESP32 connected\nIP Address: " + newIp);
            updateStepIndicator("Complete! ✓");

            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "✓ Connected: " + newIp, Toast.LENGTH_LONG).show()
                );
            }

            // Disconnect BLE
            if (bluetoothGatt != null) {
                handler.postDelayed(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) bluetoothGatt.disconnect();
                    } else {
                        if (hasPermission(Manifest.permission.BLUETOOTH)) bluetoothGatt.disconnect();
                    }
                }, 1000);
            }

            // Switch to main app
            if (listener != null) {
                handler.postDelayed(() -> listener.onProvisioningSuccess(), 1500);
            }

        } else if (status.contains("FAILED")) {
            btnConnect.setEnabled(true);
            btnConnect.setText("Retry Connection");
            tvStatus.setText("✗ WiFi connection failed\n\nPlease check:\n• SSID is correct\n• Password is correct\n• Router is powered on");
        } else {
            tvStatus.setText("Status: " + status);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
}
