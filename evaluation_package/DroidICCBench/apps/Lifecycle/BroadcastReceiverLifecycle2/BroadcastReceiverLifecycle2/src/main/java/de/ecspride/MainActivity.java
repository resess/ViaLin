package de.ecspride;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name BroadcastReceiverLifecycle1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail Steven.Arzt@cased.de
 * 
 * @description The sensitive data is read in onCreate() and sent out in a dynamically registered
 *   broadcast receiver.
 * @dataflow source -> imei -> sink
 * @number_of_leaks 1
 * @challenges The analysis must be able to handle the dynamic registration of broadcast
 *   receivers
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
				String imei = ((TelephonyManager) getApplicationContext().getSystemService
						(Context.TELEPHONY_SERVICE)).getDeviceId(); //source

				IntentFilter filter = new IntentFilter();
				filter.addAction("de.ecspride.MyAction");

				registerReceiver(new MyReceiver(imei), filter);
				Intent intent = new Intent();
				intent.setAction("de.ecspride.MyAction");
				sendBroadcast(intent);
			}
		});
		

	}
	
	private class MyReceiver extends BroadcastReceiver {
		
		private final String deviceId;
		
		public MyReceiver(String deviceId) {
			this.deviceId = deviceId;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("DroidBench", deviceId);
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
