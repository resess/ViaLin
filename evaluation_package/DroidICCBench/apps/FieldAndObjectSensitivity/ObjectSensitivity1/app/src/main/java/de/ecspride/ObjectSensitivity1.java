package de.ecspride;

import java.util.LinkedList;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name ObjectSensitivity1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail siegfried.rasthofer@cased.de
 * 
 * @description Two lists are created, a tainted value is added to one of them while a constant string is added to the other one.
 *  The first element of the list with the untainted object is sent to a sink.
 * @dataflow -
 * @number_of_leaks 0
 * @challenges the analysis must be able to distinguish between two objects of the same type that are initialized by the same constructor.
 */
public class ObjectSensitivity1 extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_sensitivity1);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LinkedList<String> list1 = new LinkedList<String>();
                LinkedList<String> list2 = new LinkedList<String>();
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                list1.add(telephonyManager.getSimSerialNumber()); //source
                list2.add("123");

                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage("+49 1234", null, list2.get(0), null, null); //sink, no leak
            }
        });
    }    
}
