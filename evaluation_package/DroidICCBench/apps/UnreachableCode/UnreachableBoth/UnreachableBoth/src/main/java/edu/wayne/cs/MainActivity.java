package edu.wayne.cs;

import java.util.Random;

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
 * @testcase_name UnreachableBoth
 * @author Wayne State University,
 * @author_mail zhenyu.ning@wayne.edu
 * 
 * @description Both source and sink are unreachable.
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
        Random r = new Random();
        String id = "none";
        if (r.nextInt(30) < 40) {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            id = tm.getDeviceId(); // unreachable source
        } else {
            id = "None";
        }


        switch (r.nextInt(10)) {
            case 21:
                Log.i("DroidBench", id); // unreachable sink
                break;
        }
    }
}
