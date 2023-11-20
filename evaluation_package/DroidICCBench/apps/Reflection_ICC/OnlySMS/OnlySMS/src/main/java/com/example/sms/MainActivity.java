package com.example.sms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * @testcase_name OnlyTelephony
 * @version 0.1
 * @author Jyoti Gajrani, Malviya National Institute of Technology, Jaipur (INDIA) 
 * @author_mail jyotigajrani@gmail.com
 * 
 * @description Data is obtained and sent to Activity2 where it is leaked.
 * Sink is reflected. Sink is SMS.
 * @dataflow onCreate: source -> intent (imei) -> Activity2 -> sink
 * @number_of_leaks 1
 * @challenges The analysis must be able to handle reflective method invocation
 * and inter-component communication.
 */
public class MainActivity extends Activity {

	TextView text;

	TelephonyManager telephonyManager;
	String id = null;
	TextView tv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS, Manifest.permission.INTERNET}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				reflectionIccTest();
			}
		});
	}

	private void reflectionIccTest(){
		tv = (TextView) findViewById(R.id.textView1);

		TelephonyManager telephonyManager = (TelephonyManager) this
				.getSystemService(Context.TELEPHONY_SERVICE);
		id = telephonyManager.getDeviceId(); // source

		Intent i = new Intent(this, Activity2.class);
		i.putExtra("imei", id);
		startActivity(i);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
