package edu.mit.icc_intent_component_name;

import edu.mit.icc_intent_component_name.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.content.ComponentName;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name ICC-Intent-Component-Name 
 * 
 * @description   Testing the intent resolution of component name 
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges The analysis tool must be able to resolve Intent's component from a component name and follow the taint to another Activity. 
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

				ComponentName cn = new ComponentName(OutFlowActivity.this, "edu.mit.icc_intent_component_name.InFlowActivity");

				Intent i = new Intent();
				i.setComponent(cn);
				i.putExtra("DroidBench", imei);

				startActivity(i);
			}
		});
	}

}
