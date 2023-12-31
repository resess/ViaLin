package edu.uta;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import edu.uta.ServiceEventSequence1.LocalBinder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Lifecycle_ServiceEventSequence1
 * 
 * @description   Testing if information leak can be detected which occurs through possible flows between the service callbacks.  
 * @dataflow onStartCommand :source -> onBind -> onStartCommand: sink
 * @number_of_leaks 1
 * @challenges  The analysis tool must be able to detect data leaks which are triggered by different ordering of events. 
 * 
 */

public class MainActivity extends Activity {
    ServiceEventSequence1 mService;
    boolean mBound = false;
    boolean unBindReturnedTrue = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("BindingActivity", "onCRRRRREATTEEEeeEE");

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ServiceEventSequence1.class);

                startService(intent);

                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

                startService(intent);
            }
        });
    }
    
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
  
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
 
            mBound = false;
        }
    };
    
}