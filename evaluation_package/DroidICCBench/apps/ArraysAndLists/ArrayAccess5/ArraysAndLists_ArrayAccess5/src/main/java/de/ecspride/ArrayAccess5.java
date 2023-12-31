package de.ecspride;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
/**
 * @testcase_name ArrayAccess3
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail Steven.Arzt@cased.de
 * 
 * @description Sensitive data is written into an array, but only the constant size of the array
 * 		is leaked, not the data itself
 * @dataflow -
 * @number_of_leaks 0
 * @challenges The analysis must correctly distinguish between the size of an array and its elements
 */
public class ArrayAccess5 extends Activity {
	public static String[] arrayData;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_array_access1);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                arrayData = new String[1];
                arrayData[0] = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId(); //source

                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage("+49 1234", null, "Size is: " + arrayData.length, null, null);  //sink, leak
            }
        });


    }    
}
