package com.saveurlife.goodnews.ble.service;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;


import static com.saveurlife.goodnews.ble.Common.CHARACTERISTIC_UUID;
import static com.saveurlife.goodnews.ble.Common.DEVICEINFO_UUID;
import static com.saveurlife.goodnews.ble.Common.SERVICE_UUID;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;


import com.saveurlife.goodnews.GoodNewsApplication;
import com.saveurlife.goodnews.R;
import com.saveurlife.goodnews.ble.BleMeshConnectedUser;
//import com.saveurlife.goodnews.ble.message.ChatDatabaseManager;
import com.saveurlife.goodnews.ble.ChatRepository;
import com.saveurlife.goodnews.ble.CurrentActivityEvent;
//import com.saveurlife.goodnews.ble.GroupRepository;
import com.saveurlife.goodnews.ble.message.ChatDatabaseManager;
//import com.saveurlife.goodnews.ble.message.GroupDatabaseManager;
import com.saveurlife.goodnews.ble.message.SendMessageManager;
import com.saveurlife.goodnews.main.PreferencesUtil;
import com.saveurlife.goodnews.models.ChatMessage;
import com.saveurlife.goodnews.service.LocationService;
import com.saveurlife.goodnews.service.UserDeviceInfoService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BleService extends Service {

    private String nowChatRoomID = "";
    private PreferencesUtil preferencesUtil;
    private int alter = 1;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BleService getService() {
            // Return this instance of BleService so clients can call public methods
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    private boolean isAdvertising = false;

    public static SendMessageManager sendMessageManager;
    private LocationService locationService;
    private UserDeviceInfoService userDeviceInfoService;
    private static String myId;
    private static String myName;

    private static String myFamilyId = "";
    private static List<String> myGroupIds = new ArrayList<>();

    private BleServiceScanCallback mBleScanCallback;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private ArrayList<String> deviceArrayList;
    private ArrayList<String> deviceArrayListName;
    private MutableLiveData<List<String>> deviceArrayListNameLiveData = new MutableLiveData<>();

    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>(); // 스캔한 디바이스 객체

    private ArrayList<String> bleConnectedDevicesArrayList;
    private MutableLiveData<List<String>> bleConnectedDevicesArrayListLiveData = new MutableLiveData<>();

    private static Map<String, BluetoothGatt> deviceGattMap = new HashMap<>(); // 나와 ble로 직접 연결된 디바이스 <주소, BluetoothGatt>

    private static Map<String, Map<String, BleMeshConnectedUser>> bleMeshConnectedDevicesMap; // mesh network로 나와 연결된 디바이스들 <나와 직접 연결된 디바이스 address, 이 디바이스를 통해 연결되어 있는 유저 <userId, user>
    private MutableLiveData<Map<String, Map<String, BleMeshConnectedUser>>> bleMeshConnectedDevicesMapLiveData = new MutableLiveData<>();

    private BluetoothGattServer mGattServer;

    private BleGattCallback bleGattCallback;


    private HandlerThread handlerThread;
    private Handler handler;
    private static final int INTERVAL = 5000; // 30 seconds

//        private String myStatus;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if ("ACTION_START_ADVERTISE_AND_SCAN".equals(action)) {
                Log.i(TAG, "onStartCommand: ");
                startAdvertiseAndScanAndAuto();
            }
        }
        return START_STICKY;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        preferencesUtil = new PreferencesUtil(this);
        myName = preferencesUtil.getString("name", "이름 없음");


        locationService = new LocationService(this);
        userDeviceInfoService = new UserDeviceInfoService(this);
        myId = userDeviceInfoService.getDeviceId();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

        // PreferencesUtil 인스턴스 생성
        PreferencesUtil preferencesUtil = new PreferencesUtil(this);
        // PreferencesUtil을 사용하여 status 값을 읽기
//            myStatus = preferencesUtil.getString("status", "4");

        if (!mBluetoothAdapter.isLeCodedPhySupported()) {
            return;
        } else {
        }

        deviceArrayList = new ArrayList<>();
        deviceArrayListName = new ArrayList<>();
        bleConnectedDevicesArrayList = new ArrayList<>();
        bleMeshConnectedDevicesMap = new HashMap<>();


        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(characteristic);
        mGattServer.addService(service);

        bleGattCallback = new BleGattCallback();


        sendMessageManager = new SendMessageManager(SERVICE_UUID, CHARACTERISTIC_UUID, userDeviceInfoService, locationService, preferencesUtil, myName);

        //        Intent AutoSendServiceIntent = new Intent(this, AutoSendMessageService.class);
        //        startService(AutoSendServiceIntent);
    }

    // 블루투스 시작 버튼
    public void startAdvertiseAndScanAndAuto() {
        startAdvertising();
        startScanning();
        startAutoSendMessage();
    }

    private void startAdvertising() {
        if (isAdvertising) {
            Log.i(TAG, "Already advertising, not starting new advertisement.");
            return; // 이미 광고 중이면 여기서 리턴
        }

        byte[] userIdBytes = myId.getBytes(StandardCharsets.UTF_8);
        byte[] userNameBytes = myName.getBytes(StandardCharsets.UTF_8);

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addServiceData(new ParcelUuid(SERVICE_UUID), userIdBytes)
                .addServiceData(new ParcelUuid(DEVICEINFO_UUID), userNameBytes)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build();

        if (mBluetoothAdapter.isLeExtendedAdvertisingSupported()) { // Check if extended advertising is supported (Bluetooth 5.1+)
            AdvertisingSetParameters advertisingSetParameters = new AdvertisingSetParameters.Builder()
                    .setLegacyMode(false)
                    .setConnectable(true)
                    .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)
                    .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)
                    .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)

                    .build();

            //            AdvertisingSetCallback bleAdvertisingSetCallback = new BleAdvertisingSetCallback();
            AdvertiseData scanResponse = new AdvertiseData.Builder().build();

            AdvertisingSetCallback bleAdvertisingSetCallback = new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                    super.onAdvertisingSetStarted(advertisingSet, txPower, status);
                    isAdvertising = true; // 광고 상태를 '광고 중'으로 변경
                    Log.i(TAG, "Started extended advertising.");
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    super.onAdvertisingSetStopped(advertisingSet);
                    isAdvertising = false; // 광고 상태를 '광고 중지'로 변경
                    Log.i(TAG, "Stopped extended advertising.");
                }

                // ... 필요한 다른 콜백 메소드 ...
            };

            mBluetoothLeAdvertiser.startAdvertisingSet(
                    advertisingSetParameters,
                    advertiseData,
                    scanResponse,
                    null,
                    null,
                    0,
                    0,
                    bleAdvertisingSetCallback
            );
        } else { // For Bluetooth 5.0 and below
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();

            AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    isAdvertising = true; // 광고 상태를 '광고 중'으로 변경
                    Log.i("BLE", "Advertise success (Legacy)");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    isAdvertising = false; // 광고 상태를 '광고 중지'로 변경
                    Log.e("BLE", "Advertise failed (Legacy), error code: " + errorCode);
                }
            };
            mBluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback);
        }
    }

    private void stopAdvertising() {
        if (!isAdvertising) {
            Log.i(TAG, "No advertising to stop, since it wasn't started.");
            return; // 광고 중이 아니면 여기서 리턴
        }
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertisingSet(null);
            Log.i(TAG, "Bluetooth advertising stopped.");
        }
        isAdvertising = false; // 광고 상태를 '광고 중지'로 변경
    }

    private void startScanning() {
        // 현재 실행 중인 스캔이 있는지 확인하고 중지
        if (mBluetoothLeScanner != null && mBleScanCallback != null) {
            mBluetoothLeScanner.stopScan(mBleScanCallback);
        }

        deviceArrayList.clear();
        deviceArrayListName.clear();
        bluetoothDevices.clear();

        // 기존 콜백 인스턴스를 재사용하거나 필요한 경우 새 인스턴스를 생성
        if (mBleScanCallback == null) {
            mBleScanCallback = new BleServiceScanCallback();
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(false)
                .setPhy(BluetoothDevice.PHY_LE_CODED)
                .build();

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        mBluetoothLeScanner.startScan(filters, settings, mBleScanCallback);

        scanHandler.post(removeExpiredDevicesRunnable);
    }

    private void stopScanning() {
        // BLE 스캐너가 있다면, 스캐닝 중지
        if (mBluetoothLeScanner != null && mBleScanCallback != null) {
            mBluetoothLeScanner.stopScan(mBleScanCallback);
            // 콜백을 null로 설정하여 재사용을 방지
            mBleScanCallback = null;
        }

        // Handler를 사용하여 반복 작업을 취소
        scanHandler.removeCallbacks(removeExpiredDevicesRunnable);

        // 필요한 경우, 여기에 다른 자원 정리 로직을 추가
    }


    public void connectOrDisconnect(String deviceId) {
        BluetoothDevice selectedDevice = bluetoothDevices.get(deviceArrayList.indexOf(deviceId));

        if (bleConnectedDevicesArrayList.contains(selectedDevice.getAddress())) {
            BluetoothGatt gatt = deviceGattMap.remove(selectedDevice.getAddress());
            if (gatt != null) {
                bleConnectedDevicesArrayList.remove(selectedDevice.getAddress());
                bleConnectedDevicesArrayListLiveData.postValue(bleConnectedDevicesArrayList);

                bleMeshConnectedDevicesMap.remove(selectedDevice.getAddress());
                Log.i("disconnect", Integer.toString(bleMeshConnectedDevicesMap.size()));
                bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);

                sendMessageManager.sendMessageDisconnect(gatt);
                sendMessageManager.sendMessageChange(deviceGattMap, bleMeshConnectedDevicesMap);
            }
        } else {
            connectToDevice(selectedDevice);
        }
    }

    public BluetoothDevice getBluetoothDeviceById(String deviceId) {
        for (BluetoothDevice device : bluetoothDevices) {
            if (device.getAddress().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }

    public void disconnect(BluetoothDevice device) {
        if (device != null) {
            BluetoothGatt gatt = deviceGattMap.get(device.getAddress());
            if (gatt != null) {
                gatt.disconnect();
                deviceGattMap.remove(device.getAddress());
                // 필요한 경우 추가적인 연결 해제 로직
            }
        }
    }


    public void disconnect(int position) {
        String address = bleConnectedDevicesArrayList.get(position);

        BluetoothGatt gatt = deviceGattMap.remove(address);
        if (gatt != null) {
            bleConnectedDevicesArrayList.remove(address);
            bleConnectedDevicesArrayListLiveData.postValue(bleConnectedDevicesArrayList);

            bleMeshConnectedDevicesMap.remove(address);
            bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);

            deviceArrayListNameLiveData.postValue(deviceArrayListName);

            sendMessageManager.sendMessageDisconnect(gatt);
            sendMessageManager.sendMessageChange(deviceGattMap, bleMeshConnectedDevicesMap);

        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        BluetoothGatt bluetoothGatt = device.connectGatt(this, false, bleGattCallback, BluetoothDevice.TRANSPORT_AUTO, BluetoothDevice.PHY_LE_CODED);
        deviceGattMap.put(device.getAddress(), bluetoothGatt);
    }

    private void startAutoSendMessage() {
        if (handlerThread == null) {
            handlerThread = new HandlerThread("AutoMessageSenderHandlerThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendMessageBase();
                // 반복적으로 실행될 작업
                handler.postDelayed(this, INTERVAL);
            }
        }, INTERVAL);
    }

    public static void sendMessageBase() {
        sendMessageManager.sendMessageBase(deviceGattMap);
    }

    public void sendMessageHelp() {

        // 여기에 메시지 전송 로직을 구현합니다.
        sendMessageManager.sendMessageHelp(deviceGattMap);
    }

    public void sendMessageChat(String receiverId, String receiverName, String content) {
        // 여기에 메시지 전송 로직을 구현합니다.
        sendMessageManager.sendMessageChat(deviceGattMap, receiverId, receiverName, content);
    }

    public void sendMessageGroupInvite(List<String> receiverIds, String groupId, String groupName) {
        sendMessageManager.sendMessageGroupInvite(deviceGattMap, receiverIds, groupId, groupName);
    }

    private void spreadMessage(String address, String content) {
        Map<String, BluetoothGatt> spreadDeviceGattMap = new HashMap<>();
        spreadDeviceGattMap.putAll(deviceGattMap);
        spreadDeviceGattMap.remove(address);
        sendMessageManager.spreadMessage(spreadDeviceGattMap, content);
    }

    public void createChatRoom(String chatRoomId, String chatRoomName) {

    }


    private Map<String, Long> lastSeenMap = new HashMap<>();
    private static final long EXPIRATION_TIME_MS = 4000;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final Runnable removeExpiredDevicesRunnable = new Runnable() {
        @Override
        public void run() {
            removeExpiredDevices();
            // 예: 10초마다 한 번씩 removeExpiredDevices를 호출합니다.
            scanHandler.postDelayed(this, 1000);
        }
    };

    private void removeExpiredDevices() {
        long currentTime = System.currentTimeMillis();
        List<String> devicesToRemove = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastSeenMap.entrySet()) {
            if (currentTime - entry.getValue() > EXPIRATION_TIME_MS) {
                devicesToRemove.add(entry.getKey());
            }
        }

        for (String deviceId : devicesToRemove) {
            int index = deviceArrayList.indexOf(deviceId);
            if (index != -1) {
                bluetoothDevices.remove(index);
                deviceArrayList.remove(index);
                deviceArrayListName.remove(index);
            }
            lastSeenMap.remove(deviceId);
        }

        if (!devicesToRemove.isEmpty()) {
            deviceArrayListNameLiveData.postValue(deviceArrayListName);
        }
    }


    public class BleServiceScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();

            // 광고 레코드에서 사용자 이름 데이터 가져오기
            byte[] userIdBytes = null;
            byte[] userNameBytes = null;
            if (result.getScanRecord() != null) {
                userIdBytes = result.getScanRecord().getServiceData(new ParcelUuid(SERVICE_UUID));
                userNameBytes = result.getScanRecord().getServiceData(new ParcelUuid(DEVICEINFO_UUID));
            }

            // 사용자 이름이 null이거나 비어 있을 경우 "Unknown Device"로 표시
            String deviceId = (userIdBytes != null && userIdBytes.length > 0) ? new String(userIdBytes) : "Unknown Device";
            String deviceName = (userNameBytes != null && userNameBytes.length > 0) ? new String(userNameBytes) : "Unknown Name";

            for (Map<String, BleMeshConnectedUser> bleMeshConnectedUserMap : bleMeshConnectedDevicesMap.values()) {
                if (bleMeshConnectedUserMap.containsKey(deviceId)) {
                    return;
                }
            }

            int existingDeviceIndex = -1;
            for (int i = 0; i < bluetoothDevices.size(); i++) {
                if (bluetoothDevices.get(i).getAddress().equals(deviceAddress) || deviceArrayList.get(i).equals(deviceId)) {
                    existingDeviceIndex = i;
                    break;
                }
            }

            // 마지막 감지 시간 업데이트
            lastSeenMap.put(deviceId, System.currentTimeMillis());

            if (existingDeviceIndex != -1) {
                bluetoothDevices.set(existingDeviceIndex, device);
                deviceArrayList.set(existingDeviceIndex, deviceId);
                deviceArrayListName.set(existingDeviceIndex, deviceId + "/" + deviceName);
                //                deviceArrayListNameLiveData.postValue(deviceArrayListName); // new code to update LiveData
            } else {
                bluetoothDevices.add(device);
                deviceArrayList.add(deviceId);
                deviceArrayListName.add(deviceId + "/" + deviceName); // your existing code where you add devices
                deviceArrayListNameLiveData.postValue(deviceArrayListName); // new code to update LiveData
            }
        }
    }


    public LiveData<List<String>> getDeviceArrayListNameLiveData() {
        return deviceArrayListNameLiveData;
    }

    public LiveData<List<String>> getBleConnectedDevicesArrayListLiveData() {
        return bleConnectedDevicesArrayListLiveData;
    }

    public LiveData<Map<String, Map<String, BleMeshConnectedUser>>> getBleMeshConnectedDevicesArrayListLiveData() {
        return bleMeshConnectedDevicesMapLiveData;
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Service added successfully");
            } else {
                Log.e("BLE", "Failed to add service. Status: " + status);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.i("onConnectionStateChange", "onConnectionStateChange");
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                String deviceAddress = device.getAddress();
                if (!bleConnectedDevicesArrayList.contains(deviceAddress)) {
                    bleConnectedDevicesArrayList.add(deviceAddress);
                    bleConnectedDevicesArrayListLiveData.postValue(bleConnectedDevicesArrayList);

                    if (!deviceGattMap.containsKey(deviceAddress)) {
                        connectToDevice(device);
                    } else {
                        // 기존 BluetoothGatt 객체 재사용
                        BluetoothGatt gatt = deviceGattMap.get(deviceAddress);
                        // 필요한 경우 gatt 객체를 사용하여 통신
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String disconnectedDevice = device.getAddress();
                if (!bleConnectedDevicesArrayList.contains(disconnectedDevice)) {
                    return;
                }
                bleConnectedDevicesArrayList.remove(disconnectedDevice);
                bleConnectedDevicesArrayListLiveData.postValue(bleConnectedDevicesArrayList);

                BluetoothGatt gatt = deviceGattMap.remove(device.getAddress());
                if (gatt != null) {
                    gatt.close();
                }

                bleMeshConnectedDevicesMap.remove(device.getAddress());
                bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                sendMessageManager.sendMessageChange(deviceGattMap, bleMeshConnectedDevicesMap);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (SERVICE_UUID.equals(characteristic.getService().getUuid()) && CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String message = new String(value);
                Log.i("수신 메시지", message);

                String[] parts = message.split("/");
                String messageType = parts[0];
                String senderId = parts[1];


                if (senderId.equals(myId)) return;

                // 처음 연결 시 내 메시 네트워크 유저 정보 교환
                if (messageType.equals("init")) {
                    String maxSize = parts[2];
                    String nowSize = parts[3];
                    String content = parts[4];

                    String[] users = content.split("@");
                    Map<String, BleMeshConnectedUser> insert = new HashMap<>();
                    for (String user : users) {
                        String[] data = user.split("-");
                        String dataId = data[0];
                        if (dataId.equals(myId)) {
                            continue;
                        }

                        if (deviceArrayList.contains(dataId)) {
                            int removeIndex = deviceArrayList.indexOf(dataId);
                            deviceArrayList.remove(removeIndex);
                            deviceArrayListName.remove(removeIndex);
                            bluetoothDevices.remove(removeIndex);
                            deviceArrayListNameLiveData.postValue(deviceArrayListName);
                        }

                        BleMeshConnectedUser existingUser = null;
                        if (bleMeshConnectedDevicesMap.containsKey(device.getAddress())) {
                            existingUser = bleMeshConnectedDevicesMap.get(device.getAddress()).get(senderId);
                        }

                        boolean isSelected = existingUser != null ? existingUser.getIsSelected() : false;

                        BleMeshConnectedUser meshConnectedUser = new BleMeshConnectedUser(dataId, data[1], data[2], data[3], Double.parseDouble(data[4]), Double.parseDouble(data[5]), isSelected);
                        insert.put(dataId, meshConnectedUser);
                    }
                    if (!bleMeshConnectedDevicesMap.containsKey(device.getAddress())) {
                        bleMeshConnectedDevicesMap.put(device.getAddress(), insert);
                        if (nowSize.equals(maxSize)) {
                            bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                        }
                    } else if (nowSize.equals(maxSize)) {
                        bleMeshConnectedDevicesMap.get(device.getAddress()).putAll(insert);
                        bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                    } else {
                        bleMeshConnectedDevicesMap.get(device.getAddress()).putAll(insert);
                    }

                    spreadMessage(device.getAddress(), message);
                }

                // 지속적 위치, 상태 정보 뿌리기
                else if (messageType.equals("base")) {
                    BleMeshConnectedUser existingUser = null;
                    if (bleMeshConnectedDevicesMap.containsKey(device.getAddress())) {
                        existingUser = bleMeshConnectedDevicesMap.get(device.getAddress()).get(senderId);
                    }

                    boolean isSelected = existingUser != null ? existingUser.getIsSelected() : false;
//                    chatDatabaseManager.createChatMessage(senderId, senderId, parts[2], "parts[8]", parts[3]);
                    spreadMessage(device.getAddress(), message);
                    BleMeshConnectedUser bleMeshConnectedUser = new BleMeshConnectedUser(senderId, parts[2], parts[3], parts[4], Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), isSelected);

                    if (bleMeshConnectedDevicesMap.containsKey(device.getAddress())) {
                        bleMeshConnectedDevicesMap.get(device.getAddress()).put(senderId, bleMeshConnectedUser);
                        bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                    }
                }

                // 모두에게 구조요청
                else if (messageType.equals("help")) {
                    GoodNewsApplication goodNewsApplication = (GoodNewsApplication) getApplicationContext();
                    if (!goodNewsApplication.isInBackground()) {
                        foresendNotification(parts);
                    } else {
                        // 앱이 백그라운드에 있을 때 푸시 알림 보내기
                        String nameBack = parts.length > 2 ? parts[2] : "이름 없음";
                        sendNotification(nameBack);
                    }
//                        sendNotification(message);
                    spreadMessage(device.getAddress(), message);
                }

                // 특정 대상에게 채팅
                else if (messageType.equals("chat")) {
                    GoodNewsApplication goodNewsApplication = (GoodNewsApplication) getApplicationContext();
                    String targetId = parts[7];
                    String targetName = parts[8];
                    String senderName = parts[2];
                    String content = parts[9];
                    String time = parts[3];

                    Boolean isRead = nowChatRoomID.equals(senderId) ? true : false;

                    if (myId.equals(targetId)) {
                        chatRepository.addMessageToChatRoom(senderId, senderName, senderId, senderName, content, time, isRead);

                        if (!senderId.equals(nowChatRoomID)) {
                            if (!goodNewsApplication.isInBackground()) {
                                foresendNotification(parts);
                            } else {
                                String nameBack = parts.length > 2 ? parts[2] : "이름 없음";
                                String contentBack = parts.length > 9 ? parts[9] : "내용 없음";
                                sendChatting(nameBack, contentBack);
                            }
                        }
                    } else if (myFamilyId.equals(targetId)) {
                        chatRepository.addMessageToChatRoom(targetId, "가족", senderId, senderName, content, time, isRead);
                        spreadMessage(device.getAddress(), message);
                    } else if (myGroupIds.contains(targetId)) {
                        chatRepository.addMessageToChatRoom(targetId, "그룹이름", senderId, senderName, content, time, isRead);
                        if (!goodNewsApplication.isInBackground()) {
                            foresendNotification(parts);
                        } else {
                            String nameBack = parts.length > 2 ? parts[2] : "이름 없음";
                            String contentBack = parts.length > 9 ? parts[9] : "내용 없음";
                            sendChatting(nameBack, contentBack);
                        }

                        spreadMessage(device.getAddress(), message);

                    } else {
                        spreadMessage(device.getAddress(), message);
                    }

                } else if (messageType.equals("invite")) {
                    ArrayList<String> groupMembers = new ArrayList<>(Arrays.asList(parts[2].split("@")));

                    if (groupMembers.contains(myId)) {
                        // 여기서 그룹에 참여
                        String groupId = parts[3];
                        String groupName = parts[4];

                        List<BleMeshConnectedUser> membersList = bleMeshConnectedDevicesMap.values().stream()
                                .flatMap(users -> groupMembers.stream().map(users::get).filter(Objects::nonNull))
                                .collect(Collectors.toList());

//                        groupRepository.addMembersToGroup(groupId, groupName, membersList);
                    }

                    spreadMessage(device.getAddress(), message);

                } else if (messageType.equals("disconnect")) {
                    BluetoothGatt gatt = deviceGattMap.remove(device.getAddress());
                    gatt.close();

                    bleConnectedDevicesArrayList.remove(device.getAddress());
                    bleConnectedDevicesArrayListLiveData.postValue(bleConnectedDevicesArrayList);

                    bleMeshConnectedDevicesMap.remove(device.getAddress());
                    bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);

                    sendMessageManager.sendMessageChange(deviceGattMap, bleMeshConnectedDevicesMap);
                } else if (messageType.equals("change")) {
                    Log.i("bleMeshConnectedDevicesMap", message);
                    String maxSize = parts[2];
                    String nowSize = parts[3];
                    String content = parts[4];

                    String[] users = content.split("@");
                    Map<String, BleMeshConnectedUser> insert = new HashMap<>();
                    for (String user : users) {
                        String[] data = user.split("-");
                        String dataId = data[0];
                        if (dataId.equals(myId)) {
                            continue;
                        }

                        if (deviceArrayList.contains(dataId)) {
                            int removeIndex = deviceArrayList.indexOf(dataId);
                            deviceArrayList.remove(removeIndex);
                            deviceArrayListName.remove(removeIndex);
                            bluetoothDevices.remove(removeIndex);
                            deviceArrayListNameLiveData.postValue(deviceArrayListName);

                        }
                        BleMeshConnectedUser existingUser = null;
                        if (bleMeshConnectedDevicesMap.containsKey(device.getAddress())) {
                            existingUser = bleMeshConnectedDevicesMap.get(device.getAddress()).get(senderId);
                        }

                        boolean isSelected = existingUser != null ? existingUser.getIsSelected() : false;
                        BleMeshConnectedUser meshConnectedUser = new BleMeshConnectedUser(dataId, data[1], data[2], data[3], Double.parseDouble(data[4]), Double.parseDouble(data[5]), isSelected);
                        insert.put(dataId, meshConnectedUser);
                    }
                    if (nowSize.equals("1")) {
                        bleMeshConnectedDevicesMap.put(device.getAddress(), insert);
                        if (nowSize.equals(maxSize)) {
                            bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                        }
                    } else if (nowSize.equals(maxSize)) {
                        bleMeshConnectedDevicesMap.get(device.getAddress()).putAll(insert);
                        bleMeshConnectedDevicesMapLiveData.postValue(bleMeshConnectedDevicesMap);
                    } else {
                        bleMeshConnectedDevicesMap.get(device.getAddress()).putAll(insert);
                    }

                    spreadMessage(device.getAddress(), message);
                }
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        // ... 필요한 경우 다른 콜백 메서드 추가 ...
    };

    //구조요청 알림
    private void sendNotification(String messageContent) {
        // Notification Channel 생성 (Android O 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "My Notification Channel";
            String description = "Channel for My App";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("MY_CHANNEL_ID", name, importance);
            channel.setDescription(description);
            // 채널을 시스템에 등록
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "MY_CHANNEL_ID")
                .setSmallIcon(R.drawable.good_news_logo) // 알림 아이콘 설정
                .setContentTitle("구조 요청") // 알림 제목
                .setContentText(messageContent + "님이 구조를 요청했습니다.") // 'message'는 받은 메시지의 내용
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // 알림 표시
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(alter++, builder.build()); // 'notificationId'는 각 알림을 구별하는 고유 ID

    }

    //채팅 알림(백그라운드)
    private void sendChatting(String messageContent, String contentBack) {
        // Notification Channel 생성 (Android O 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "My Notification Channel";
            String description = "Channel for My App";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("MY_CHANNEL_ID", name, importance);
            channel.setDescription(description);
            // 채널을 시스템에 등록
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "MY_CHANNEL_ID")
                .setSmallIcon(R.drawable.good_news_logo) // 알림 아이콘 설정
                .setContentTitle(messageContent) // 알림 제목
                .setContentText(contentBack) // 'message'는 받은 메시지의 내용
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // 알림 표시
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(alter++, builder.build()); // 'notificationId'는 각 알림을 구별하는 고유 ID

    }

    //포그라운드
    public void foresendNotification(String[] parts) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
                View layout = inflater.inflate(R.layout.custom_toast, null);
                View chatLayout = inflater.inflate(R.layout.custom_toast_chat, null);

                // 커스텀 레이아웃의 파라미터 설정
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layout.setLayoutParams(layoutParams);

                // 커스텀 레이아웃의 뷰에 접근하여 설정
                TextView senderName = layout.findViewById(R.id.toast_name);
                TextView time = layout.findViewById(R.id.toast_time);

                TextView nameChat = chatLayout.findViewById(R.id.toast_chat_name);
                TextView context = chatLayout.findViewById(R.id.toast_chat_text);
                TextView timeChat = chatLayout.findViewById(R.id.toast_chat_time);

                String name = parts.length > 2 ? parts[2] : "이름 없음";
                if (parts[0].equals("help")) {
                    String content = name + "님께서 구조를 요청했습니다.";
                    senderName.setText(content);

                } else if (parts[0].equals("chat")) {
                    nameChat.setText(name);
                    String content = parts.length > 9 ? parts[9] : "내용 없음";
                    context.setText(content);
                }


                String currentTime = new SimpleDateFormat("a hh:mm", Locale.KOREA).format(Calendar.getInstance().getTime());
                time.setText(currentTime);
                timeChat.setText(currentTime);

                // 시스템 알림 사운드 재생
                try {
//                                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//                                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
//                                    r.play();
                    MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.toast_alarm);
                    mediaPlayer.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Toast toast = new Toast(getApplicationContext());
                toast.setDuration(Toast.LENGTH_SHORT);
                if (parts[0].equals("help")) {
                    toast.setView(layout);
                } else if (parts[0].equals("chat")) {
                    toast.setView(chatLayout);
                }
                toast.setGravity(Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0);
                toast.show();

//                                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    public class BleGattCallback extends BluetoothGattCallback {
        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "PHY updated successfully.");
            } else {
                Log.e("BLE", "Failed to update PHY. Error code: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                String[] parts = new String(characteristic.getValue()).split("/");
                String type = parts[0];
                if ("disconnect".equals(type)) {
                    gatt.close();
                } else if ("chat".equals(type)) {
                    chatRepository.addMessageToChatRoom(parts[7], parts[8], myId, myName, parts[9], parts[3], true);
                }

                Log.i("송신 메시지", new String(characteristic.getValue()));
            } else {
                Log.e("BLE", "Failed to send message to " + gatt.getDevice().getAddress() + ". Error code: " + status);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected to GATT server.");
                Log.i("BLE", "Attempting to start service discovery:" + gatt.discoverServices());

//                deviceGattMap.put(gatt.getDevice().getAddress(), gatt);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            boolean result = gatt.requestMtu(400);
            Log.i("BLE", "MTU change request result: " + result);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Services discovered.");

                if (gatt.getDevice().getBondState() == 12) {
                    sendMessageManager.sendMessageInit(gatt, bleMeshConnectedDevicesMap);
                }
            } else {
                Log.w("BLE", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "MTU changed to: " + mtu);
                sendMessageManager.sendMessageInit(gatt, bleMeshConnectedDevicesMap);
            } else {
                Log.w("BLE", "MTU change failed, status: " + status);
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        // 광고 중지 로직
        stopAdvertising();
        stopScanning();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // HandlerThread 종료
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }

        // BluetoothGattServer 연결 닫기
        if (mGattServer != null) {
            mGattServer.close();
            mGattServer = null;
        }

        // 모든 BluetoothGatt 연결 닫기
        for (BluetoothGatt gatt : deviceGattMap.values()) {
            if (gatt != null) {
                gatt.close();
            }
        }
        deviceGattMap.clear();
        EventBus.getDefault().unregister(this);
    }

    //채팅
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCurrentActivityEvent(CurrentActivityEvent event) {
        String currentActivityName = event.getActivityName();
        if ("none".equals(currentActivityName)) {
            nowChatRoomID = currentActivityName;
        } else {
            nowChatRoomID = currentActivityName;
        }
    }


    //채팅
    private ChatDatabaseManager chatDatabaseManager = new ChatDatabaseManager();
    private ChatRepository chatRepository = new ChatRepository(chatDatabaseManager);

    public MutableLiveData<List<ChatMessage>> getChatRoomMessages(String chatRoomId) {
        return chatRepository.getChatRoomMessages(chatRoomId);
    }

    public LiveData<List<String>> getAllChatRoomIds() {
        return chatRepository.getAllChatRoomIds();
    }

    public void updateIsReadStatus(String chatRoomId) {
        chatRepository.updateIsReadStatus(chatRoomId);
        chatRepository.getChatRoomMessages(chatRoomId);
    }


    //    private MutableLiveData<Map<String, Map<String, BleMeshConnectedUser>>> bleMeshConnectedDevicesMapLiveData = new MutableLiveData<>();
    public BleMeshConnectedUser getBleMeshConnectedUser(String userId) {
        Log.i("BleMeshConnectedUser", userId);
        BleMeshConnectedUser returnUser = null;
        for (Map<String, BleMeshConnectedUser> innerMap : bleMeshConnectedDevicesMap.values()) {
            if (innerMap.containsKey(userId)) {
                returnUser = innerMap.get(userId);
                Log.i("BleMeshConnectedUser", userId);
            }
        }
        return returnUser;
    }

    public MutableLiveData<BleMeshConnectedUser> getBleMeshConnectedUserWithId(String userId) {
        MutableLiveData<BleMeshConnectedUser> userLiveData = new MutableLiveData<>();

        bleMeshConnectedDevicesMapLiveData.observeForever(new Observer<Map<String, Map<String, BleMeshConnectedUser>>>() {
            @Override
            public void onChanged(Map<String, Map<String, BleMeshConnectedUser>> bleMeshConnectedDevicesMap) {
                for (Map<String, BleMeshConnectedUser> innerMap : bleMeshConnectedDevicesMap.values()) {
                    if (innerMap.containsKey(userId)) {
                        userLiveData.setValue(innerMap.get(userId));
                        break; // 일치하는 사용자를 찾았으므로 반복 중단
                    }
                }
            }
        });

        return userLiveData;
    }
}




//    private GroupDatabaseManager groupDatabaseManager = new GroupDatabaseManager();
//    private GroupRepository groupRepository = new GroupRepository(groupDatabaseManager);
//
//    public void addMembersToGroup(String groupName, List<String> members){
//
//        Map<String, BleMeshConnectedUser> allConnectedUser = new HashMap<>();
//        for(Map<String, BleMeshConnectedUser> users : bleMeshConnectedDevicesMap.values()){
//            allConnectedUser.putAll(users);
//            Log.i("연결된사용자수", Integer.toString(users.size()));
//        }
//
//
//        List<BleMeshConnectedUser> membersList=new ArrayList<>();
//        for(String memberId : members){
//            Log.i("memberId", memberId);
//            if(allConnectedUser.containsKey(memberId)){
//                Log.i("allConnectedUser", allConnectedUser.get(memberId).toString());
//                membersList.add(allConnectedUser.get(memberId));
//
//            }
//        }
//        Log.i("membersList", membersList.toString());
//        Date now = new Date();
//        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmssSSS");
//        String formattedDate = sdf.format(now);
//        String groupId = "group"+myId+formattedDate;
//
//        groupRepository.addMembersToGroup(groupId, groupName, membersList);
//
//        List<String> membersId = new ArrayList<>();
//        for(BleMeshConnectedUser member : membersList){
//            membersId.add(member.getUserId());
//        }
//
//        sendMessageManager.sendMessageGroupInvite(deviceGattMap, membersId, groupId, groupName);
