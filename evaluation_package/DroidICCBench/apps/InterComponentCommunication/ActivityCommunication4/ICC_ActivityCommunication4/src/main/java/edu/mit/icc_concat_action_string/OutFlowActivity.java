package edu.mit.icc_concat_action_string;

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
 * @testcase_name ICC-Concat-Action-String
 * 
 * @description   Testing if string concatenation can be analyzed in the Intent.ACTION field
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges   The analysis tool needs to be able to analyze constant string with concatenation operation and able to resolve the Intent for the resulted string and follow tainted data to the next Activity
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

				Intent i = new Intent("edu.mit.icc_concat_action_string" + ".ACTION");
				i.putExtra("DroidBench", imei);

				startActivity(i);
			}
		});


	}

}
