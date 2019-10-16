package com.example.aircon_controller;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button btnConnect, btnUp, btnDown;
    TextView tvMode, tvCurTemp, tvDesTemp, tvStatus;

    BluetoothAdapter mBluetoothAdapter;
    static final int REQUEST_ENABLE_BT = 10;
    Set<BluetoothDevice> mDevice;
    int mPairedDeviceCount = 0;

    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;
    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';

    Thread mWorkerThread = null;
    byte[] readBuffer;
    int readBufferPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.button_connect);
        btnUp = findViewById(R.id.button_up);
        btnDown = findViewById(R.id.button_down);
        tvMode = findViewById(R.id.textView_mode);
        tvCurTemp = findViewById(R.id.textView_cur_tmp);
        tvDesTemp = findViewById(R.id.textView_desire_tmp);
        tvStatus = findViewById(R.id.textView_status);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkBluetooth();
            }
        });

        tvMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData("changemode");
            }
        });

        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData("up");
            }
        });

        btnDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData("down");
            }
        });
    }

    void checkBluetooth(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 블루투스 기능이 없는 경우 null
        if(mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if(!mBluetoothAdapter.isEnabled()){
                Toast.makeText(getApplicationContext(), "블루투스를 켜라 짜식아", Toast.LENGTH_SHORT).show();
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
            } else {
                selectDevice();
            }
        }
    }

    void selectDevice(){
        // 블루투스 연결된 장치의 객체를 가져옴
        mDevice = mBluetoothAdapter.getBondedDevices();
        // 페어된 디바이스의 개수
        mPairedDeviceCount = mDevice.size();

        if(mPairedDeviceCount == 0){
            Toast.makeText(getApplicationContext(),"페어링된 장치가 없습니다.",Toast.LENGTH_SHORT).show();
            finish();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");

        List<String> listItems = new ArrayList<String>();
        for(BluetoothDevice device : mDevice){
            listItems.add(device.getName());
        }
        listItems.add("취소");

        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int item) {
                if(item == mPairedDeviceCount){
                    Toast.makeText(getApplicationContext(),"연결할 장치를 선택하지 않았습니다.",Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    connectToSelectedDevice(items[item].toString());
                }
            }
        });
        builder.setCancelable(false);
        AlertDialog alert = builder.create();
        alert.show();
    }

    void connectToSelectedDevice(String selectedDeviceName){
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        try {
            mSocket = mRemoteDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            mSocket.connect();

            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
            beginListenForData();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),"블루투스 연결 중 오류가 발생했습니다.",Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    BluetoothDevice getDeviceFromBondedList(String name){
        // 선택된 블루투스 디바이스의 객체 자체를 반환하는 함수
        BluetoothDevice selectedDevice = null;

        for(BluetoothDevice device : mDevice){
            if(name.equals(device.getName())){
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    void sendData(String msg){
        msg += mStrDelimiter;

        try {
            mOutputStream.write(msg.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void beginListenForData(){
        final Handler handler = new Handler();

        readBuffer = new byte[1024];

        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        int byteAvailable = mInputStream.available();

                        if(byteAvailable > 0){
                            byte[] packetBytes = new byte[byteAvailable];
                            mInputStream.read(packetBytes);

                            for(int i = 0; i < byteAvailable; i++){
                                byte b = packetBytes[i];

                                if(b == mCharDelimiter){
                                    // 개행문자가 나오면(문자열의 끝이 나오면)
                                    // 자바(UTF-8)와 STM32(ASCII)의 인코딩이 다르기 때문에 해줘야한다.
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    //final String data = new String(encodedBytes, "US-ASCII");
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // 수신된 문자(data)를 여기서 처리

                                            String[] splitLists = data.split("\\@");
                                            // splitLists[0] = mode, [1] = current Tmp, [2] = desire Tmp, [3] = status

                                            int index = data.length();

                                            if(index == 9){
                                                Log.d("ABC", "splitLists[0] : " + splitLists[0]);
                                                Log.d("ABC", "splitLists[1] : " + splitLists[1]);
                                                Log.d("ABC", "splitLists[2] : " + splitLists[2]);
                                                Log.d("ABC", "splitLists[3] : " + splitLists[3] + "\n");

                                                if(splitLists[0].equals("0")) {
                                                    tvMode.setText("냉방");
                                                }else{
                                                    tvMode.setText("난방");
                                                }
                                                tvCurTemp.setText(splitLists[1]);
                                                tvDesTemp.setText(splitLists[2]);
                                                if(splitLists[3].equals("0")){
                                                    tvStatus.setText(" ");
                                                }else if(splitLists[3].equals("1")){
                                                    tvStatus.setText("냉방 중");
                                                }else if(splitLists[3].equals("2")){
                                                    tvStatus.setText(" ");
                                                }else if(splitLists[3].equals("3")){
                                                    tvStatus.setText("난방 중");
                                                }

                                            }else{
                                                Log.d("ABC", "received incorrect value " + data);
                                            }
                                        }
                                    });
                                }
                                else{
                                    // 개행문자가 아니면 버퍼에 넣기
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),"데이터 수신 중 오류가 발생했습니다.",Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
        });
        // start 하면 개행문자가 나오기 전까지 계속 데이터를 수신
        mWorkerThread.start();
    }

    @Override
    protected void onDestroy() {
        // 앱이 종료될 때 소켓과 쓰레드를 닫는다
        try {
            mWorkerThread.interrupt();
            mInputStream.close();
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}
