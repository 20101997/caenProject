package com.grupposts.trasporti.rfid;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.caen.BLEPort.BLEPort;
import com.caen.BLEPort.BLEPortEvent;
import com.caen.BLEPort.CAENRFIDBleScannerCallBacks;
import com.caen.RFIDLibrary.CAENRFIDBLEConnectionEventListener;
import com.caen.RFIDLibrary.CAENRFIDException;
import com.caen.RFIDLibrary.CAENRFIDPort;
import com.caen.RFIDLibrary.CAENRFIDReader;
import com.caen.RFIDLibrary.CAENRFIDReaderInfo;
import com.grupposts.trasporti.R;
import com.grupposts.trasporti.features.rfid.models.DemoReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class BleConnectionActivity extends Activity implements CAENRFIDBLEConnectionEventListener {

    public static Vector<DemoReader> Readers = new Vector<>();;



    public static int Selected_Reader;


    private ProgressDialog tcpBtWaitProgressDialog;




    private final String TAG = BleConnectionActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 2;
    private static final int REQUEST_ENABLE_BT_CANCELLED = 3;
    private static final long REFRESH_INTERVAL = 500;
    public static final int BLE_SELECTED = 1;
    public static final int BLE_CANCELED = 0;
    private BluetoothAdapter mBluetoothAdapter;
    private LocationManager mLocationManager;
    private TextView mProgressBarTitle;
    private ProgressBar mProgressBar;
    private final AtomicBoolean mStopScanRequest = new AtomicBoolean(false);
    private final AtomicBoolean mIsBleScanning = new AtomicBoolean(false);
    private final Semaphore mStopScanSemaphore = new Semaphore(0);
    private ArrayAdapter<BluetoothDevice> mAdapter;
    private final List<BluetoothDevice> mEntries = new ArrayList<BluetoothDevice>(){
        @Override
        public boolean add(BluetoothDevice bleDevice) {
            for (BluetoothDevice device : this) {
                if (device.getAddress().equals(bleDevice.getAddress()))
                    return false;
            }
            return super.add(bleDevice);
        }
    };

    private CAENRFIDBleScannerCallBacks mCAENRFIDScannerCallBacks;
    //
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // contacts-related task you need to do.
                if (mBluetoothAdapter == null) {
                    Toast.makeText(getApplicationContext(), "No bluetooth adapter...",
                            Toast.LENGTH_SHORT).show();
                } else {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(
                                BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    } else {
                        // ----------------------------------------- //
                        //                                           //
                        //               FINALLY START....           //
                        //                                           //
                        // ----------------------------------------- //
                        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Toast.makeText(this, "Turn on your GPS please", Toast.LENGTH_SHORT).show();
                            mStopScanSemaphore.release();
                            this.setResult(REQUEST_ENABLE_BT_CANCELLED);
                            finish();
                        } else {
                            refreshDeviceList();
                        }
                    }
                }
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                mStopScanSemaphore.release();
                setResult(REQUEST_ENABLE_BT_CANCELLED);
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // ----------------------------------------- //
                //                                           //
                //               FINALLY START....           //
                //                                           //
                // ----------------------------------------- //
                if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    Toast.makeText(this, "Turn on your GPS please", Toast.LENGTH_SHORT).show();
                    mStopScanSemaphore.release();
                    this.setResult(REQUEST_ENABLE_BT_CANCELLED);
                    finish();
                }else{
                    refreshDeviceList();
                }
            } else {
                this.setResult(REQUEST_ENABLE_BT_CANCELLED);
                mStopScanSemaphore.release();
                finish();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_connection);
        //
        ListView mListView = findViewById(R.id.deviceList);
        mProgressBar =  findViewById(R.id.progressBar);
        mProgressBarTitle =  findViewById(R.id.progressBarTitle);

        mAdapter = new ArrayAdapter<BluetoothDevice>(this,
                android.R.layout.simple_expandable_list_item_2, mEntries) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null){
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    assert inflater != null;
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final BluetoothDevice device = mEntries.get(position);
                final String title = String.format("Name %s",
                        device.getName());
                row.getText1().setText(title);

                final String subtitle = String.format("MAC  %s", device.getAddress());
                row.getText2().setText(subtitle);
                if(device.getName().substring(0)=="s"){
                    mStopScanRequest.set(true);

                }

                // returnToBackActivity(device);

                return row;
            }

        };
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }
                mStopScanRequest.set(true);
                final BluetoothDevice device = mEntries.get(position);
                returnToBackActivity(device);
            }
        });
        //CAEN RFID DEVICE SCANNING CALLBACKS
        mCAENRFIDScannerCallBacks = new CAENRFIDBleScannerCallBacks() {
            @Override
            public void onStartScan() {

            }

            @Override
            public void onStopScan() {

            }

            @Override
            public void onDeviceFound(final BluetoothDevice bluetoothDevice) {
                mAdapter.add(bluetoothDevice);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.add(bluetoothDevice);
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        };
        //PERMISSION AND BT ENABLING...
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(
                ( ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) || (
                        ContextCompat.checkSelfPermission(
                                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                )
        )
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ACCESS_FINE_LOCATION);

        } else {
            if (mBluetoothAdapter == null) {
                Toast.makeText(getApplicationContext(), "No bluetooth adapter...",
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            } else {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

                } else {

                    if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                        Toast.makeText(this, "Turn on your GPS please", Toast.LENGTH_SHORT).show();
                        mStopScanSemaphore.release();
                        this.setResult(REQUEST_ENABLE_BT_CANCELLED);
                        finish();
                    }else{
                        refreshDeviceList();
                    }
                }
            }
        }
    }


    protected void onPostRefresh(Boolean result) {
        mProgressBarTitle.setText(
                String.format("%s device(s) found", mEntries.size()));
        hideProgressBar();
        Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
        mIsBleScanning.set(false);
        if(result) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            refreshDeviceList();
                        }
                    }, REFRESH_INTERVAL);
        }
    }

    private void refreshDeviceList() {
        mIsBleScanning.set(true);
        showProgressBar();

        Thread refresher = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Refreshing device list ...");
                if(mStopScanRequest.get()) {
                    mStopScanSemaphore.release();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onPostRefresh(false);
                        }
                    });
                    return;
                }
                BLEPort.findCAENRFIDBLEDevices(5000, mCAENRFIDScannerCallBacks);
                if(mStopScanRequest.get()) {
                    mStopScanSemaphore.release();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onPostRefresh(false);
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onPostRefresh(true);
                    }
                });
            }
        });
        refresher.setName("DeviceRefresher");
        refresher.start();
    }

    private void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void returnToBackActivity(BluetoothDevice bluetoothDevice) {

        int selected_port = bluetoothDevice == null ? BLE_CANCELED : BLE_SELECTED;
        if(selected_port == BleConnectionActivity.BLE_SELECTED){
            final BluetoothDevice device = (BluetoothDevice) bluetoothDevice;
            assert device != null;
            tcpBtWaitProgressDialog = ProgressDialog.show(this,
                    "Connection ","Connecting to "+ device.getName());
            new BleConnectionActivity.BLEConnector(device).start();
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        mStopScanRequest.set(true);
        if(mIsBleScanning.get()) {
            try {
                BLEPort.stopFindingBLEDevices();
                mStopScanSemaphore.acquire();
            } catch (InterruptedException ignored) {

            }
        }
        super.onDestroy();
    }

    @Override
    public void onBLEConnectionEvent(CAENRFIDReader caenrfidReader, BLEPortEvent blePortEvent) {

    }
    private class BLEConnector extends Thread{

        BluetoothDevice bleDevice = null;
        boolean error1 = false;
        boolean error2 = false;

        BLEConnector ( BluetoothDevice device) {
            bleDevice = device;
        }

        @Override
        public void run() {
            super.run();
            //connect to the reader...
            CAENRFIDReader r = new CAENRFIDReader();
            CAENRFIDReaderInfo info = null;
            String fwrel = null;
            try {
                r.addCAENRFIDBLEConnectionEventListener(BleConnectionActivity.this);
                r.Connect(getApplicationContext(),bleDevice);
            } catch (CAENRFIDException e) {
                error1 = true;
            }
            if (!error1) {
                try {
                    error2 = false;
                    info = r.GetReaderInfo();
                    fwrel = r.GetFirmwareRelease();
                } catch (CAENRFIDException e) {
                    error2 = true;
                }
                if (!error2) {
                    DemoReader dr = new DemoReader(r,
                            info.GetModel(), info.GetSerialNumber(), fwrel,
                            CAENRFIDPort.CAENRFID_BLE);
                    Readers.add(dr);


                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onPostExecute(true);
                }
            });
        }

        protected void onPostExecute(Boolean result) {
            if (error1) {
                Toast.makeText(getApplicationContext(),
                        "Error during connection...", Toast.LENGTH_SHORT)
                        .show();
            } else if (error2) {
                Toast.makeText(getApplicationContext(),
                        "Error retrieving info from reader...",
                        Toast.LENGTH_SHORT).show();
            }
            tcpBtWaitProgressDialog.dismiss();
        }
    }

}