package edu.mit.non_sink_argument_flow;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Non-Sink-Argument-Flow
 * 
 * @description Flow to sink is through memory reachable from receiver, but not through argument
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges   The analysis tool has to be able to track taint flown to sink through a receiver
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

                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.command("cmd", imei);
                    pb.start();  //leak (assuming cmd is a leak)
                } catch (Exception e) {

                }
            }
        });



    }
}
