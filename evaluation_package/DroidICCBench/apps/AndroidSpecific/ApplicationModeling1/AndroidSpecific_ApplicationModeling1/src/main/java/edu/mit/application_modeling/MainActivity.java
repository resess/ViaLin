package edu.mit.application_modeling;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Application-Modeling
 * 
 * @description Test if modeling correctly instantiates a single application object from the manifest
 * and passes the object correctly to calls of getApplication()
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges The analysis tool has to be able to resolve explicit Intent carrying tainted sensitive information
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
                String imei = mgr.getDeviceId();	//source

                ((MyApplication)getApplication()).imei = imei;

                Intent i = new Intent(MainActivity.this, AnotherActivity.class);
            }
        });


    }
}
