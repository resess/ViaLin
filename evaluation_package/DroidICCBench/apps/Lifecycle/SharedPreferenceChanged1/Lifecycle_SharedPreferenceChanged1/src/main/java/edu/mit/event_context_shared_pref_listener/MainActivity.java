package edu.mit.event_context_shared_pref_listener;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Event-Context-Shared-Pref-Listener
 * 
 * @description Test that an event from the runtime is called with the appropriate context (argument)
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - In this case, the change listener has to be called with the shared preferences 
 * that are changed.
 */
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    
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


                SharedPreferences settings = getSharedPreferences("settings", 0);
                settings.registerOnSharedPreferenceChangeListener(MainActivity.this);

                SharedPreferences.Editor editor = settings.edit();
                editor.putString("imei", imei);
                editor.commit();
            }
        });


    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String imei = sharedPreferences.getString(key, "");
        Log.i("DroidBench", imei);
    }
}
