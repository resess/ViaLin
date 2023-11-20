package edu.wayne.cs;

import java.util.Random;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.support.v4.app.ActivityCompat;

/**
 * @testcase_name UnreachableSink1
 * @author Wayne State University,
 * @author_mail zhenyu.ning@wayne.edu
 * 
 * @description The sink is unreachable.
 * @dataflow
 * @number_of_leaks 0
 * @challenges The analysis should detect that some branches are unreachable.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unreachableFlowTest();
            }
        });


    }

    private void unreachableFlowTest(){
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String id = tm.getDeviceId(); // source

        Random r = new Random();
        switch (r.nextInt(10)) {
            case 21:
                Log.d("DroidBench", id); // unreachable sink
                break;
        }
    }
}
