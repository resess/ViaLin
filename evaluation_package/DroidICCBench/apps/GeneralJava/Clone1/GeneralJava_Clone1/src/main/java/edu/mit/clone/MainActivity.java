package edu.mit.clone;

import java.util.LinkedList;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Clone
 * 
 * @description Tesging LinkedList.clone
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - must model clone of list
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TelephonyManager mgr = (TelephonyManager) MainActivity.this.getSystemService(TELEPHONY_SERVICE);
                String imei = mgr.getDeviceId();
                LinkedList<String> list = new LinkedList<String>();
                list.add(imei);

                LinkedList<String> list2 = (LinkedList<String>)list.clone();

                Log.i("DroidBench", list2.get(0));
            }
        });


    }
}
