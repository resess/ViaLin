package edu.mit.array_copy;

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
 * @testcase_name ArrayCopy
 * 
 * @description Testing System.arraycopy()
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - The analysis tool must have a model for System.arraycopy()
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
                String[] array = new String[1];
                array[0] = imei;
                String[] arraycopy = new String[1];
                System.arraycopy(array, 0, arraycopy, 0, 1);

                Log.i("DroidBench", arraycopy[0]); //sink
            }
        });
    }
}
