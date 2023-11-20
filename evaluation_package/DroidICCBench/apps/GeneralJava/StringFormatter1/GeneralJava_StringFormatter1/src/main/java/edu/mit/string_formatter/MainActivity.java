package edu.mit.string_formatter;

import java.util.Formatter;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name String Format
 * 
 * @description Test String Formatter
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - Modeling of StringBuffer and StringFormatter
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
                String imei = mgr.getDeviceId();  //source
                StringBuffer buf = new StringBuffer();

                Formatter formatter = new Formatter(buf);
                formatter.format("%s", imei);
                formatter.close();

                Log.i("DroidBench", buf.toString()); //sink, leak
            }
        });


    }
}
