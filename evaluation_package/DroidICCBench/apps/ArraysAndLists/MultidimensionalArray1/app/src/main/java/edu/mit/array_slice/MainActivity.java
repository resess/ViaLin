package edu.mit.array_slice;

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
 * @testcase_name ArraySlice 
 * 
 * @description Testing whether an element in a multidimensional array is tracked
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges The analysis tool has to be able to track an element within a multidimensional array
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
                String imei = mgr.getDeviceId(); //source
                String[][] array = new String[1][1];
                array[0][0] = imei;

                String[] slice = array[0];

                Log.i("DroidBench", slice[0]); //sink
            }
        });
    }
}
