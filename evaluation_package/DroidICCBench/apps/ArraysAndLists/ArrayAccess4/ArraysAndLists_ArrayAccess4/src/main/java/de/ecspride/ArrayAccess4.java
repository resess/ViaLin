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
 * @description Sensitive data is written into a field of an object, this object is then stored in an
 * 		array and read back again. The field that gets passed to the sink, is however, a different one
 * 		than the one that was tainted.
 * @dataflow -
 * @number_of_leaks 0
 * @challenges The analysis must correctly handle fields of objects inside arrays. 
 */
public class ArrayAccess4 extends Activity {
	public static A[] arrayData;
	
	private class A {
		@SuppressWarnings("unused")
		private String b;
		private String c;
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_array_access1);


        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                A a = new A();
                A b = new A();
                A c = new A();

                a.b = "Hello world";
                a.c = "Empty";

                b.b = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId(); //source
                b.c = "foo";

                c.b = "constant string";
                c.c = "not here";

                arrayData = new A[] { a, b, c };

                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage("+49 1234", null, arrayData[0].c, null, null);  //sink, leak
            }
        });
        

    }    
}
