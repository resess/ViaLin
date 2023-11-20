package edu.mit.pattern_matcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * @testcase_name PatternMatcher
 * 
 * @description Test common usage of pattern and matcher in Java
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - flows through multiple object allocated in API code
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

                Pattern p = Pattern.compile("(.*)");
                Matcher matcher = p.matcher(imei);

                if (matcher.matches()) {
                    String match = matcher.group(1);
                    Log.i("DroidBench", match);
                }
            }
        });
    }
}
