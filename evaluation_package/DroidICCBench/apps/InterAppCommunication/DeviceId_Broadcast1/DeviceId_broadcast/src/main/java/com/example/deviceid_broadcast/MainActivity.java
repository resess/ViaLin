package com.example.deviceid_broadcast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name DeviceId_Broadcast
 * @version 0.1
 * @author Malaviya National Institute of Technology Jaipur, India 
 * @author_mail er.shwetabhandari@gmail.com
 * 
 * @description The device id is sent to a broadcast receiver and from there on to
 * the collector app
 * @dataflow deviceid -> 
 * @number_of_leaks 1
 * @challenges The analysis must correctly handle broadcast receivers as well
 * as inter-app communication through intents 
 */
public class MainActivity extends Activity {

	//ImageView im;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //im = (ImageView)findViewById(R.id.imageView1);
        
       // Uri uri = Uri.parse("android.resource://com.example.deviceid_broadcast/drawable/ic_launcher");
       // im.setImageURI(uri);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BroadcastReceiver br = new broadcast_deviceid();
                IntentFilter filter = new IntentFilter("com.example.deviceid_broadcast.broadcast_deviceid");
                registerReceiver(br, filter);

                Intent in = new Intent("com.example.deviceid_broadcast.broadcast_deviceid");
                sendBroadcast(in);
            }
        });


    }  
}
