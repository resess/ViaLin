package edu.mit.button_object_allocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Button-Object-Allocation
 * 
 * @description Test correct modeling of button object maintained by the runtime and delivered to onClick events.
 *  handler is defined via XML.
 * @number_of_leaks 1
 * @challenges Must correctly model that a Button is represented by a single object in the runtime, and that object 
 * is delivered to multiple calls of onClick
 */
public class Button1 extends Activity {
	private static String imei = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button1);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setHint("");

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                imei = telephonyManager.getDeviceId(); //source
                sendMessage(view);
            }
        });


    }

    public void sendMessage(View view){
	    Log.i("DroidBench", ((Button)view).getHint().toString());  //sink on second call to sendMessage(), second click of button
	    ((Button)view).setHint(imei);
    }
}
