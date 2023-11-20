package org.arguslab.icc_implicit_src_nosink;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.view.Menu;

/**
 * @testcase_name ICC_Implicit_Src_NoSink
 * @author Fengguo Wei & Sankardas Roy
 * @author_mail fgwei521@gmail.com & sroy@ksu.edu
 *
 * @description The value v of a source is sent to component FooActivity via implicit ICC.
 * 				In FooActivity, it will retrieve value v but not leak it.
 * @dataflow source -> imei -> MainActivity's intent -> sink (implicit ICC)
 * @number_of_leaks 1
 * @challenges The analysis must be able to resolve implicit (Action) ICC calls and handle data flow
 * 				across different components.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            }
        }

    }

    private void leakImei() {
        TelephonyManager tel = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String imei = tel.getDeviceId(); // source

        Intent i = new Intent("amandroid.impliciticctest_action.testaction");
        i.putExtra("data", imei);
        startActivity(i); // sink
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                leakImei();
                return;
            }
        }
    }
}