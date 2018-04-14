package com.example.fsanti.proofofvisit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jota.IotaAPI;
import jota.IotaAPICommands;
import jota.dto.response.GetNodeInfoResponse;
import jota.dto.response.SendTransferResponse;
import jota.model.Transaction;
import jota.model.Transfer;
import jota.utils.TrytesConverter;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class MainActivity extends AppCompatActivity {

    private final String LOG_TAG_BLUETOOTH = "BluetoothEvent";
    private final String LOG_TAG_IOTA = "IOTAEvent";
    private BluetoothLeScannerCompat scannerCompat;
    private BluetoothDevice bluetoothDevice;

    // UUID of beacon services to which the phone will connect
    // https://learn.adafruit.com/introducing-adafruit-ble-bluetooth-low-energy-friend/uart-service
    UUID SERVICE_UUID_NORDIC_UART = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    UUID CHARACTERISTIC_UUID_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    UUID CHARACTERISTIC_UUID_TX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    String deviceAddress = "EA:17:E9:64:3A:7A"; //"DB:12:B4:4A:73:79";

    // callback to discover the beacon implementing the POV GATT Service
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // We scan with report delay > 0. This will never be called.
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (!results.isEmpty()) {
                // Device detected, we can automatically connect to it and stop the scan
                // TODO: fixed address
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                // ScanResult result = results.get(0);
                // bluetoothDevice = result.getDevice();
                // String deviceAddress = bluetoothDevice.getAddress();
                bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
                // Log.e(LOG_TAG_BLUETOOTH, "onBatchScanResults: " + deviceAddress);
                // connect to the GATT server and stop scanning
                connectGATT();
                scannerCompat.stopScan(scanCallback);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // Scan error
            Log.e(LOG_TAG_BLUETOOTH, "onScanError");
        }
    };

    // GATT callbacks
    private final BluetoothGattCallback gattServerCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {

            Log.i(LOG_TAG_BLUETOOTH, "onConnectionStateChange, status: " + Integer.toString(status));
            Log.i(LOG_TAG_BLUETOOTH, "onConnectionStateChange, newState: " + Integer.toString(newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(LOG_TAG_BLUETOOTH, "Connected to GATT client. Attempting to start service discovery");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(LOG_TAG_BLUETOOTH, "Not connected from GATT client");
            }

            bluetoothGatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG_BLUETOOTH, "onServicesDiscovered not connected");
                return;
            }

            // read the value of the characteristic
            Log.e(LOG_TAG_BLUETOOTH, "onServicesDiscovered");
            BluetoothGattCharacteristic characteristic = gatt
                    .getService(SERVICE_UUID_NORDIC_UART)
                    .getCharacteristic(CHARACTERISTIC_UUID_RX);


            writeStringCharacteristic(gatt, characteristic, "HELLO RUUVI!!!");

            /*
            // ask for notifications
            gatt.setCharacteristicNotification(characteristic, true);

            // Write on the config descriptor to be notified when the value changes
            BluetoothGattDescriptor descriptor =
                    characteristic.getDescriptor(DESCRIPTOR_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            */
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(LOG_TAG_BLUETOOTH, "onCharacteristicChanged");
            // readCounterCharacteristic(characteristic);
        }
    };

    /**
     * BLUETOOTH METHODS
     */

    private void connectGATT () {
        Log.e(LOG_TAG_BLUETOOTH, "connecting GATT...");
        bluetoothDevice.connectGatt(this, false, gattServerCallback);
    }

    /*
    private void readCounterCharacteristic(BluetoothGattCharacteristic
                                                   characteristic) {

        Log.e(LOG_TAG_BLUETOOTH, "readCounterCharacteristic");

        if (CHARACTERISTIC_UUID_TX.equals(characteristic.getUuid())) {
            byte[] data = characteristic.getValue();
            String str = new String(data, StandardCharsets.UTF_8);
            Log.e(LOG_TAG_BLUETOOTH, "value: " + str);
            // TODO: UPDATE UI
        }
    } */


    private void writeStringCharacteristic(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, String str) {
        Log.e(LOG_TAG_BLUETOOTH,"writeStringCharacteristic");
        characteristic.setValue(str);
        // characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(characteristic);

    }

    /**
     * IOTA METHODS
     */

    private void sendToIOTA () {

        IotaAPI api = new IotaAPI.Builder()
                .protocol("http")
                .host("nodes.iota.fm")
                .port("80")
                .build();


        if (api != null && api.getNodeInfo() != null) {

            Log.e(LOG_TAG_IOTA, "APP Version: " + api.getNodeInfo().getAppVersion());

            String seed = "XWKTJAVSSPKSFLBQHHD9AZIZKGYIEAXVERYVCBSERKSHERWITYCICPYOJKQVJJSBURFDBPUFHIOJS9TGAARJVGG9HD";
            String address = "XWKTJAVSSPKSFLBQHHD9AZIZKGYIEAXVERYVCBSERKSHERWITYCICPYOJKQVJJSBURFDBPUFHIOJS9TGAARJVGG9HD";
            String address_remainder = "XWKTJAVSSPKSFLBQHHD9AZIZKGYIEAXVERYVCBSERKSHERWITYCICPYOJKQVJJSBURFDBPUFHIOJS9TGAARJVGG9H0";
            String tag = "ILOVEIOTA999999999999999999";
            String msg = "HELLO WE ARE ALL TOGETHER";

            List<Transfer> transfers = new ArrayList<>();
            Transfer transfer = new Transfer(address, 0, TrytesConverter.toTrytes(msg), tag);
            transfers.add(transfer);

            /*
            SendTransferResponse response = api.sendTransfer(seed, 2, 9,
                    15, transfers, null,"",false);
            */

        }



    }

    /**
     * LIFECYCLE METHODS
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kill issue
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                // SEND TO IOTA
                Log.i(LOG_TAG_IOTA, "Try to send to IOTA");
                sendToIOTA();
            }
        });

        // Instantiate Bluetooth Scanner
        scannerCompat = BluetoothLeScannerCompat.getScanner();

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(1000)
                .build();

        // only filter beacons which implement our service
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID_NORDIC_UART))
                .build();

        scannerCompat.startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
