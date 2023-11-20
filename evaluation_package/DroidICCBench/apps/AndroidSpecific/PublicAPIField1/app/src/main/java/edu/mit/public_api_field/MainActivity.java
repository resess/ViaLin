package edu.mit.public_api_field;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Public-API-Field
 * 
 * @description Track flows through an API field setter and a direct field access
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - Must have accurate modeling for API classes that expose fields
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
                float fx = Float.valueOf(imei.substring(0, 8));
                float fy = Float.valueOf(imei.substring(8));
                PointF point = new PointF(fx, fy);

                Log.i("DroidBench", "IMEI: " + point.x + "" + point.y);  //sink, leak
            }
        });
         

    }
}
