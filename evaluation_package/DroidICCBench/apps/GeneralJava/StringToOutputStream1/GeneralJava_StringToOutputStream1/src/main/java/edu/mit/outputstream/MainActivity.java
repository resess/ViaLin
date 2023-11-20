package edu.mit.outputstream;

import java.io.ByteArrayOutputStream;

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
 * @testcase_name OutputStream
 * 
 * @description tainted value is written to an output stream and then read back as a string that is leaked
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges   The analysis tool has to be able to track tainted value through different stream/memory operations 
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
                byte[] bytes = imei.getBytes();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(bytes, 0, bytes.length);

                String outString = out.toString();

                Log.i("DroidBench", outString);
            }
        });
    }
}
