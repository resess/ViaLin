package de.ecspride;

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
 * @testcase_name Exceptions3
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description tainted data is created, but the exception handler which would send it out is never invoked
 * @dataflow source -> imei -> /
 * @number_of_leaks 0
 * @challenges the analysis must precisely model which exceptions can occur and which ones can't
 */
public class Exceptions3 extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_exceptions3);


		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				String imei = "";
				try {
					TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
					imei = telephonyManager.getDeviceId(); //source
					int[] arr = new int[42];
					if (arr[32] > 0)
						imei = "";
				}
				catch (RuntimeException ex) {
					SmsManager sm = SmsManager.getDefault();
					sm.sendTextMessage("+49 1234", null, imei, null, null); //sink, leak
				}
			}
		});

	}

}
