package com.example.fsanti.proofofvisit;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Build;
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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
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
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rx_characteristic;
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private int stage = 0;
    private byte[] challengeHash = new byte[32];

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    static CharsetEncoder asciiEncoder =
            Charset.forName("US-ASCII").newEncoder(); // or "ISO-8859-1" for ISO Latin 1

    public static boolean isPureAscii(String v) {
        return asciiEncoder.canEncode(v);
    }

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
                if (133 == status) {
                    // Sometimes status is 133, seems to be random. Retry?
                    // https://stackoverflow.com/questions/25330938/android-bluetoothgatt-status-133-register-callback
                    Log.i(LOG_TAG_BLUETOOTH, "Status 133, Retry connection");
                    startScan();
                }
            }

            bluetoothGatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt _gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG_BLUETOOTH, "onServicesDiscovered not connected");
                return;
            }

            // read the value of the characteristic
            Log.e(LOG_TAG_BLUETOOTH, "onServicesDiscovered");


            BluetoothGattCharacteristic _tx_characteristic = _gatt
                    .getService(SERVICE_UUID_NORDIC_UART)
                    .getCharacteristic(CHARACTERISTIC_UUID_TX);

            // ask for notifications
            _gatt.setCharacteristicNotification(_tx_characteristic, true);

            // Write on the config descriptor to be notified when the value changes
            BluetoothGattDescriptor descriptor =
                    _tx_characteristic.getDescriptor(DESCRIPTOR_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            _gatt.writeDescriptor(descriptor);

            BluetoothGattCharacteristic _rx_characteristic = _gatt
                    .getService(SERVICE_UUID_NORDIC_UART)
                    .getCharacteristic(CHARACTERISTIC_UUID_RX);

            gatt = _gatt;
            rx_characteristic = _rx_characteristic;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.i(LOG_TAG_BLUETOOTH, "onCharacteristicChanged");
            // readCounterCharacteristic(characteristic);
            byte[] value=characteristic.getValue();
            String str = new String(value, StandardCharsets.UTF_8);
            if(isPureAscii(str)) {
              Log.i(LOG_TAG_BLUETOOTH, str);
            } else {
                if(1 == stage ) {
                    Log.i(LOG_TAG_BLUETOOTH, "Part 1 of challenge received, copying " + value[3] + " bytes");
                    System.arraycopy(value,
                            4,
                            challengeHash,
                            0,
                            value[3]);
                    stage ++;
                } else if (2 == stage) {
                    Log.i(LOG_TAG_BLUETOOTH, "Part 1 of challenge received, copying " + value[3] + " bytes");
                    System.arraycopy(value,
                            4,
                            challengeHash,
                            16,
                            value[3]);
                    stage ++;
                    Log.i(LOG_TAG_BLUETOOTH, "Challenge received " + bytesToHex(challengeHash));
                    Log.i(LOG_TAG_IOTA, "Try to send to IOTA");
                    sendToIOTA();

                } else {
                    Log.e(LOG_TAG_BLUETOOTH, "Replying to challenge not implemented");
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("LOG_TAG_BLUETOOTH", characteristic.toString());
            byte[] value=characteristic.getValue();
            String v = new String(value);
            Log.i("LOG_TAG_BLUETOOTH", "Value: " + v);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            Log.i("LOG_TAG_BLUETOOTH", "Descriptor written");
            BluetoothGattCharacteristic rx_characteristic = gatt
                    .getService(SERVICE_UUID_NORDIC_UART)
                    .getCharacteristic(CHARACTERISTIC_UUID_RX);

            writeStringCharacteristic(gatt, rx_characteristic, "HELLO RUUVI!!!");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            Log.i("LOG_TAG_BLUETOOTH", "Characteristic written");
        }
    };

    /**
     * BLUETOOTH METHODS
     */
    @TargetApi(23)
    private void connectGATT () {
        Log.i(LOG_TAG_BLUETOOTH, "connecting GATT...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothDevice.connectGatt(this, false, gattServerCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothDevice.connectGatt(this, false, gattServerCallback);
        }
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
        Log.i(LOG_TAG_BLUETOOTH,"writeStringCharacteristic");
        characteristic.setValue(str);
        // characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(characteristic);
    }

    private void writeByteCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] bytes) {
        Log.i(LOG_TAG_BLUETOOTH,"writeByteCharacteristic");
        characteristic.setValue(bytes);
        // characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        // If write was started successfully
        if(gatt.writeCharacteristic(characteristic)) {
            //Update state
            stage = 1;
        } else {
            Log.e(LOG_TAG_BLUETOOTH, "Could not request challenge");
            stage = 0;
        }

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

        GetNodeInfoResponse nodeInfo = api.getNodeInfo();
        // Log.e(LOG_TAG_IOTA, "APP Version: " + api.getNodeInfo().getAppVersion());
        if (api != null && nodeInfo != null) {

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
                //Initiate challenge-response
                Log.i(LOG_TAG_BLUETOOTH, "Request challenge");
                byte[] initiate = new byte[]{(byte)0x00, (byte)0xFA, (byte)0x00, (byte)0x00};
                writeByteCharacteristic(gatt, rx_characteristic, initiate);


                // SEND TO IOTA
                //Log.i(LOG_TAG_IOTA, "Try to send to IOTA");
                sendToIOTA();
            }
        });

        startScan();
    }

    protected void startScan(){
        // Instantiate Bluetooth Scanner
        scannerCompat = BluetoothLeScannerCompat.getScanner();

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(2500)
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
