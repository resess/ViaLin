package edu.mit.icc_intent_passed_through_api;

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
 * @testcase_name ICC-Intent-Passed-Through-API
 * 
 * @description   Testing an Intent passed through a linked-list then used to start an Activity 
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges  The analysis tool has to be able to track an Intent through a list operation 
 */
public class OutFlowActivity extends Activity {


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

				Intent i = new Intent(OutFlowActivity.this, InFlowActivity.class);
				i.putExtra("DroidBench", imei);

				List<Intent> iList = new LinkedList<Intent>();
				iList.add(i);

				Intent i2 = iList.get(0);

				startActivity(i2);
			}
		});


    }
}
