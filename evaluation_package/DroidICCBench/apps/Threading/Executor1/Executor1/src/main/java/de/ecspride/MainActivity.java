package de.ecspride;

import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Threading_Executor1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description Sensitive data is read in onCreate() and send out in a dedicated thread started
 *   using Java's Executor mechanism.
 * @dataflow onCreate: source -> MyThread.run() -> sink
 * @number_of_leaks 1
 * @challenges The analysis must be able to correctly handle Java's Executor mechanism.
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
				threadingTest();
			}
		});
	}


	private void threadingTest(){

		TelephonyManager telephonyManager = (TelephonyManager)
				getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		Executors.newCachedThreadPool().execute(new MyRunnable(telephonyManager.getDeviceId()));
	}


	private class MyRunnable implements Runnable {

		private final String deviceId;
		
		public MyRunnable(String deviceId) {
			this.deviceId = deviceId;
		}

		@Override
		public void run() {
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
