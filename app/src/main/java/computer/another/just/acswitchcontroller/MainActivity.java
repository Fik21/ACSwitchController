package computer.another.just.acswitchcontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import computer.another.just.acswitchcontroller.ble.BleDevicesScanner;
import computer.another.just.acswitchcontroller.ble.BleManager;
import computer.another.just.acswitchcontroller.ble.BleUtils;

public class MainActivity extends Communicator {

    private final static String TAG = MainActivity.class.getSimpleName();

    private BleManager blem;
    private boolean mIsScanPaused = true;
    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private BluetoothDeviceData mSelectedDeviceData;
    private BleDevicesScanner mScanner;
    private BluetoothDeviceData theDevice;
    private final static long kMinDelayToUpdateUI = 200;
    private long mLastUpdateMillis;
    private UartDataChunk mDataBuffer;
    private volatile int mReceivedBytes;
    private String currentData;
    private ToggleButton c1, c2;
    private Button timer1, timer2, con;
    private EditText time1, time2;
    private TextView tr1, tr2, constat;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        c1 = (ToggleButton) findViewById(R.id.toggleButton);
        c2 = (ToggleButton) findViewById(R.id.toggleButton2);
        time1 = (EditText) findViewById(R.id.num1);
        time2 = (EditText) findViewById(R.id.num2);
        tr1 = (TextView) findViewById(R.id.tr1);
        tr2 = (TextView) findViewById(R.id.tr2);
        constat = (TextView) findViewById(R.id.conn);
        timer1 = findViewById(R.id.button);
        timer2 = findViewById(R.id.button2);
        con = findViewById(R.id.conb);
        currentData = "";

        constat.setText("Connection Status:");

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_SHORT).show();
            }else{
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }else{
            Toast.makeText(this, "Location permissions already granted", Toast.LENGTH_SHORT).show();
        }


        blem = new BleManager(this);
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        mScanner = new BleDevicesScanner(bluetoothAdapter, null, new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                //final String deviceName = device.getName();
                //Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));

                BluetoothDeviceData previouslyScannedDeviceData = null;
                if (mScannedDevices == null)
                    mScannedDevices = new ArrayList<>();       // Safeguard

                // Check that the device was not previously found
                for (BluetoothDeviceData deviceData : mScannedDevices) {
                    if (deviceData.device.getAddress().equals(device.getAddress())) {
                        previouslyScannedDeviceData = deviceData;
                        break;
                    }
                }

                BluetoothDeviceData deviceData;
                if (previouslyScannedDeviceData == null) {
                    // Add it to the mScannedDevice list
                    deviceData = new BluetoothDeviceData();
                    mScannedDevices.add(deviceData);
                } else {
                    deviceData = previouslyScannedDeviceData;
                }

                deviceData.device = device;
                deviceData.rssi = rssi;
                deviceData.scanRecord = scanRecord;
                decodeScanRecords(deviceData);

                // Update device data
                long currentMillis = SystemClock.uptimeMillis();
                if (previouslyScannedDeviceData == null || currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {          // Avoid updating when not a new device has been found and the time from the last update is really short to avoid updating UI so fast that it will become unresponsive
                    mLastUpdateMillis = currentMillis;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {  }
                    });
                }
            }
        });

        blem.setBleListener(this);
        this.mBleManager = blem;

        boolean anyluck = false;
        mScanner.start();


        c1.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean toggled) {
                if (toggled) {
                    sendData("A".getBytes());
                } else {
                    sendData("AF".getBytes());
                }
            }
        });

        c2.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean toggled) {
                if (toggled) {
                    sendData("B".getBytes());
                } else {
                    sendData("BF".getBytes());
                }
            }
        });

        timer1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                c1.setChecked(true);
                setTimer(0);
            }
        });

        timer2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                c2.setChecked(true);
                setTimer(1);
            }
        });

        con.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connect();
                Toast.makeText(getApplicationContext(), "Attempting to Connect...", Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void clearUI(int c) {

        c1 = (ToggleButton) findViewById(R.id.toggleButton);
        c2 = (ToggleButton) findViewById(R.id.toggleButton2);
        time1 = (EditText) findViewById(R.id.num1);
        time2 = (EditText) findViewById(R.id.num2);
        tr1 = (TextView) findViewById(R.id.tr1);
        tr2 = (TextView) findViewById(R.id.tr2);
        constat = (TextView) findViewById(R.id.conn);
        timer1 = findViewById(R.id.button);
        timer2 = findViewById(R.id.button2);
        con = findViewById(R.id.conb);

       if (c == 0){
            c1.setChecked(false);
            c2.setChecked(false);
            time1.setText("");
            time2.setText("");
            tr1.setText("Time Remaining: --");
            tr2.setText("Time Remaining: --");
            constat.setText("Connection Status: Disconnected");
        } else {
            constat.setText("Connection Status: Connected!");
            con.setEnabled(false);
        }
    }

    @Override
    public void onDisconnected() {
        clearUI(0);
    }

    @Override
    public void onConnected() {
        clearUI(1);
    }

    @Override
    public void onServicesDiscovered() {
        if (blem == null) {
            Log.w(TAG, "Ble Manager is Null");
        }
        Log.w(TAG, blem.getGattService(UUID_SERVICE).toString());
        mUartService = blem.getGattService(UUID_SERVICE);
        enableRxNotifications();
    }

    private void setTimer(int channel) {
        EditText num;
        String s = "";

        if (channel == 0) {
            num = (EditText) findViewById(R.id.num1);
            s = num.getText().toString();
            s = "AS"+s;
        } else {
            num = (EditText) findViewById(R.id.num2);
            s = num.getText().toString();
            s = "BS"+s;
        }
        sendData(s.getBytes());
    }

    private void connect() {

        BluetoothDeviceData dd;
        boolean anyluck = false;

        for (int i = 0; i < mScannedDevices.size(); i++) {
                dd = mScannedDevices.get(i);
                if (dd.getNiceName().trim().equals("Adafruit Bluefruit LE")) {
                    theDevice = dd;
                    anyluck = true;
                    break;
                }
            }

        if(anyluck) {
            BluetoothDevice btd = theDevice.device;
            String addr = btd.getAddress();

           if(blem.connect(this, addr)) {
               constat.setText("Connection Status: Connected!");
           } else {
               constat.setText("Connection Status: Failed");
           }
        } else {
            constat.setText("Could Not Find Device.");
        }

    }

    private void updateUI() {

        //sendDataWithCRC("Hello".getBytes());

        String get = "";
        TextView fillme = findViewById(R.id.conn);
        if(mDataBuffer != null) {
            fillme.setText(mDataBuffer.getData().toString());
        } else {
            Log.w(TAG, "Empty Return Data!!!");
        }

    }
    
    private class BluetoothDeviceData {
        BluetoothDevice device;
        public int rssi;
        byte[] scanRecord;
        private String advertisedName;           // Advertised name
        private String cachedNiceName;
        private String cachedName;

        // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
        static final int kType_Unknown = 0;
        static final int kType_Uart = 1;
        static final int kType_Beacon = 2;
        static final int kType_UriBeacon = 3;

        public int type;
        int txPower;
        ArrayList<UUID> uuids;

        String getName() {
            if (cachedName == null) {
                cachedName = device.getName();
                if (cachedName == null) {
                    cachedName = advertisedName;      // Try to get a name (but it seems that if device.getName() is null, this is also null)
                }
            }

            return cachedName;
        }

        String getNiceName() {
            if (cachedNiceName == null) {
                cachedNiceName = getName();
                if (cachedNiceName == null) {
                    cachedNiceName = device.getAddress();
                }
            }

            return cachedNiceName;
        }
    }




    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.kType_Unknown;

        // Check if is an iBeacon ( 0x02, 0x0x1, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
        final boolean isBeacon = advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;

        // Check if is an URIBeacon
        final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
        final boolean isUriBeacon = Arrays.equals(Arrays.copyOf(scanRecord, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

        if (isBeacon) {
            deviceData.type = BluetoothDeviceData.kType_Beacon;

            // Read uuid
            offset = 9;
            UUID uuid = BleUtils.getUuidFromByteArrayBigEndian(Arrays.copyOfRange(scanRecord, offset, offset + 16));
            uuids.add(uuid);
            offset += 16;

            // Skip major minor
            offset += 2 * 2;   // major, minor

            // Read txpower
            final int txPower = advertisedData[offset++];
            deviceData.txPower = txPower;
        } else if (isUriBeacon) {
            deviceData.type = BluetoothDeviceData.kType_UriBeacon;

            // Read txpower
            final int txPower = advertisedData[9];
            deviceData.txPower = txPower;
        } else {
            // Read standard advertising packet
            while (offset < advertisedData.length - 2) {
                // Length
                int len = advertisedData[offset++];
                if (len == 0) break;

                // Type
                int type = advertisedData[offset++];
                if (type == 0) break;

                // Data
//            Log.d(TAG, "record -> lenght: " + length + " type:" + type + " data" + data);

                switch (type) {
                    case 0x02:          // Partial list of 16-bit UUIDs
                    case 0x03: {        // Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = advertisedData[offset++] & 0xFF;
                            uuid16 |= (advertisedData[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    }

                    case 0x06:          // Partial list of 128-bit UUIDs
                    case 0x07: {        // Complete list of 128-bit UUIDs
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                                uuids.add(uuid);

                            } catch (IndexOutOfBoundsException e) {
                                Log.e("FRUIT", "BlueToothDeviceFilter.parseUUID: " + e.toString());
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 16;
                                len -= 16;
                            }
                        }
                        break;
                    }

                    case 0x09: {
                        byte[] nameBytes = new byte[len - 1];
                        for (int i = 0; i < len - 1; i++) {
                            nameBytes[i] = advertisedData[offset++];
                        }

                        String name = null;
                        try {
                            name = new String(nameBytes, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        deviceData.advertisedName = name;
                        break;
                    }

                    case 0x0A: {        // TX Power
                        final int txPower = advertisedData[offset++];
                        deviceData.txPower = txPower;
                        break;
                    }

                    default: {
                        offset += (len - 1);
                        break;
                    }
                }
            }

            // Check if Uart is contained in the uuids
            boolean isUart = false;
            for (UUID uuid : uuids) {
                if (uuid.toString().equalsIgnoreCase("6e400001-b5a3-f393-e0a9-e50e24dcca9e")) {
                    isUart = true;
                    break;
                }
            }
            if (isUart) {
                deviceData.type = BluetoothDeviceData.kType_Uart;
            }
        }

        deviceData.uuids = uuids;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        mScanner.stop();

    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        Log.w(TAG, "Data recieved.");
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                mReceivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, bytes);
                String fill = new String(dataChunk.getData());
                currentData = fill;
                ((TextView) findViewById(R.id.powone)).setText("Channel One: " + fill);
                Log.w(TAG, "Data Processed");


            }
        }
    }

}


