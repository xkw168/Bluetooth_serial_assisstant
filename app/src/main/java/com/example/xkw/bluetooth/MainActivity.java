package com.example.xkw.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author xkw
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /**
     * fragment
     */
    private TextFragment textFragment;
    private DrawFragment drawFragment;
    private AboutFragment aboutFragment;

    /**
     * variable
     */
    private boolean mConnected = false;
    private boolean ifService = false;
    private int fragmentIndex = 0;
    private String mData;
    private BLEAdapter mBLEAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    /**
     * constant
     */
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int ADD_DEVICE = 2;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 3;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    public final static String REC_DATA = "com.example.xkw.soil_sensor.REC_DATA";

    /**
     * service & characteristic
     */
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattService rd_wr_GattService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic rd_wr_Characteristic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        mBLEAdapter = new BLEAdapter();

        //set fragment
        setDefaultFragment();

        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(mToolbar);
        Switch switchData = (Switch) findViewById(R.id.switch_data);
        switchData.setChecked(true);
        switchData.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean choose) {
                if(fragmentIndex == 0 && textFragment != null){
                    textFragment.setRecState(choose);
                }else if (fragmentIndex == 1 && drawFragment != null){
                    drawFragment.setRecState(choose);
                }
            }
        });

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        //start the service
        Intent gattServiceIntent = new Intent(this.getApplicationContext(), BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver,makeGattUpdateIntentFilter());
        updateConnectionState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_menu,menu);
        if (mConnected){
            menu.findItem(R.id.connect_device).setVisible(false);
            menu.findItem(R.id.disconnect_device).setVisible(true);
        }else {
            menu.findItem(R.id.connect_device).setVisible(true);
            menu.findItem(R.id.disconnect_device).setVisible(false);
        }
        menu.findItem(R.id.clear_rec_field).setVisible(true);
        menu.findItem(R.id.clear_send_field).setVisible(true);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.connect_device:
                Log.w(TAG, "connect to device");
                Toast.makeText(this,"连接设备",Toast.LENGTH_SHORT).show();
                startConnectBLE();
                //使菜单无效，进而重新加载
                invalidateOptionsMenu();
                break;
            case R.id.disconnect_device:
                Log.w(TAG, "disconnect the device");
                Toast.makeText(this,"断开设备",Toast.LENGTH_SHORT).show();
                mConnected = false;
                mBluetoothLeService.disconnect();
                invalidateOptionsMenu();
                break;
            case R.id.clear_rec_field:
                Log.w(TAG, "clear receive data");
                Toast.makeText(this,"清空接收区",Toast.LENGTH_SHORT).show();
                if (fragmentIndex == 0 && textFragment != null){
                    textFragment.clearRecField();
                }else if (fragmentIndex == 1 && drawFragment != null){
                    drawFragment.clearRecField();
                }
                break;
            case R.id.clear_send_field:
                Log.w(TAG, "clear send data");
                Toast.makeText(this,"清空发送区",Toast.LENGTH_SHORT).show();
                if (fragmentIndex == 0 && textFragment != null){
                    textFragment.clearSendField();
                }else if (fragmentIndex == 1 && drawFragment != null){
                    drawFragment.clearSendField();
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_text:
                    if (textFragment == null){
                        textFragment = new TextFragment();
                    }
                    fragmentIndex = 0;
                    replaceFragment(textFragment);
                    return true;
                case R.id.navigation_draw:
                    if (drawFragment == null){
                        drawFragment = new DrawFragment();
                    }
                    fragmentIndex = 1;
                    replaceFragment(drawFragment);
                    return true;
                case R.id.navigation_about:
                    if (aboutFragment == null){
                        aboutFragment = new AboutFragment();
                    }
                    fragmentIndex = 2;
                    replaceFragment(aboutFragment);
                    return true;
                default:
                    break;
            }
            return false;
        }
    };

    void replaceFragment(Fragment fragment){
        if (fragment == null){
            Log.w(TAG, "replaceFragment: fragment is not initialized");
        }else {
            Log.w(TAG, "replaceFragment: replace fragment successful");
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.frame_main,fragment);
            fragmentTransaction.commit();
        }
    }

    void setDefaultFragment(){
        if (textFragment == null){
            textFragment = new TextFragment();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frame_main, textFragment);
        fragmentTransaction.commit();
    }

    public void startConnectBLE(){
        //清空适配器
        mBLEAdapter.clearAll();
        //开始搜索蓝牙
        startSearchBLE();
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        mBuilder.setTitle("附近的蓝牙");
        mBuilder.setAdapter(mBLEAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                connect(mBLEAdapter.getItem(i).getDeviceAddress());
            }
        });
        mBuilder.create().show();
    }

    public void startSearchBLE(){
        //判断本机是否支持蓝牙
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager != null){
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (mBluetoothAdapter == null)
        {
            Toast.makeText(this, R.string.ble_not_supported,Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        //Android6.0以上需要动态获取权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        //检查蓝牙是否开启,若未开启则提醒用户
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableBTintent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTintent, REQUEST_ENABLE_BT);
        }
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {//与startActivityForResult相呼应
        //用户未开启蓝牙则退出程序
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class BLEAdapter extends BaseAdapter {
        private ArrayList<BLEDevice> mDevice;
        private LayoutInflater mInflator;

        BLEAdapter(){
            super();
            mDevice = new ArrayList<BLEDevice>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public BLEAdapter(ArrayList<BLEDevice> devices){
            mDevice = devices;
            mInflator = MainActivity.this.getLayoutInflater();
        }

        void addDevice(BLEDevice device){
            if (!mDevice.contains(device)){
                mDevice.add(device);
            }
        }

        public void removeDevice(int index){
            mDevice.remove(index);
        }

        void clearAll(){
            mDevice.clear();
        }

        @Override
        public int getCount() {
            return mDevice.size();
        }

        @Override
        public BLEDevice getItem(int i) {
            return mDevice.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            BLEHolder bleHolder;
            if (view == null){
                //注意inflate（）的第三个参数必须设置为false，不然会和onCreate函数里面的setContentView冲突
                view = mInflator.inflate(R.layout.listitem_device,null,false);
                bleHolder = new BLEHolder();
                bleHolder.deviceName = (TextView)view.findViewById(R.id.device_name);
                bleHolder.deviceAddress = (TextView)view.findViewById(R.id.device_address);
                //将ViewHolder储存至view内
                view.setTag(bleHolder);
            }
            else {
                //从view内读取ViewHolder
                bleHolder = (BLEHolder)view.getTag();
            }
            BLEDevice device = mDevice.get(i);
            bleHolder.deviceName.setText(device.getDeviceName());
            bleHolder.deviceAddress.setText(device.getDeviceAddress());
            return view;
        }
        class BLEHolder {
            //定义了一个内部类ViewHolder用于对每一个设备的信息进行缓存
            TextView deviceName;
            TextView deviceAddress;
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            ifService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            ifService = false;
        }
    };

    /**
     * Handles various events fired by the Service.
     *     ACTION_GATT_CONNECTED: connected to a GATT server.
     *     ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     *     ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     *     ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
     *                            or notification operations.
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState();
                //使菜单无效之后会重新加载，进而重新设置item的可见
                invalidateOptionsMenu();
                Log.d(TAG, "Connect State: " + mConnected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState();
                invalidateOptionsMenu();
                Log.d(TAG, "Connect State: " + mConnected);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //获得数据读取服务
                rd_wr_GattService = mBluetoothLeService.getSupportedGattServices(
                        UUID.fromString(BluetoothData.DEVICE_SERVICE));
                //没有此项服务
                if (rd_wr_GattService == null){
                    Log.d(TAG, "rd_wr_GattService: null");
                }else {
                    //从该项服务中获取相应的characteristic
                    rd_wr_Characteristic = rd_wr_GattService.getCharacteristic(
                            UUID.fromString(BluetoothData.DEVICE_CHARACTERISTIC_SERVICE));
                    //没有此项characteristic
                    if (rd_wr_Characteristic == null){
                        Log.d(TAG, "rd_wr_Characteristic: null");
                    }else {
                        //获取characteristic当前的属性
                        final int charaProp = rd_wr_Characteristic.getProperties();
                        //BluetoothGattCharacteristic.PROPERTY_READ —— 数据处于可读取状态
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0){
                            if (mNotifyCharacteristic != null){
                                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic,false);
                                mNotifyCharacteristic = null;
                            }
                            //读取数据
                            mBluetoothLeService.readCharacteristic(rd_wr_Characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                            mNotifyCharacteristic = rd_wr_Characteristic;
                            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic,true);
                        }
                    }
                }
            //读取到数据或者数据发生改变
            }else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "Data received:" );
                mData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                final Intent rec_data = new Intent(REC_DATA);
                //告诉fragment接收到了数据
                sendBroadcast(rec_data);
            }
        }
    };

    //扫描到设备时候的回调函数
    private BluetoothAdapter.LeScanCallback mLeScanCallBack = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String deviceName = bluetoothDevice.getName();
                    String deviceAddress = bluetoothDevice.getAddress();
                    if (deviceName != null && deviceName.length() > 0 && !deviceName.contains("abeacon")){
                        BLEDevice mBLEDevice = new BLEDevice(deviceName,deviceAddress);
                        mBLEAdapter.addDevice(mBLEDevice);
                        mBLEAdapter.notifyDataSetChanged();
                    }
                    else {
                        //nothing
                        Log.e(TAG, "Device name:" + (deviceName != null ? deviceName : " "));
                    }
                }
            });
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void updateConnectionState(){
        if (mConnected){
            getSupportActionBar().setSubtitle("已连接");
        }else {
            getSupportActionBar().setSubtitle("未连接");
        }
    }

    public void connect(String deviceAddress){
        if (ifService && mBluetoothLeService != null){
            mBluetoothLeService.connect(deviceAddress);
        }
    }

    private void scanLeDevice(final boolean enable){
        if (enable){
            //在预定的扫描时间之后停止扫描
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallBack);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(mLeScanCallBack);
        }
        else {
            mBluetoothAdapter.stopLeScan(mLeScanCallBack);
        }
        invalidateOptionsMenu();
    }

    public boolean getConnectionState(){
        return mConnected;
    }

    public String getData(){
        return mData;
    }

    //向单片机发送读取数据的指令
    public void sendData(String str){
        if (mConnected){
            if (rd_wr_Characteristic == null){
                Toast.makeText(MainActivity.this,"无读写服务",Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onClick: BLE not connect");
            }else {
                rd_wr_Characteristic.setValue(str);
                mBluetoothLeService.writeCharacteristic(rd_wr_Characteristic);
            }
        }
    }
}
