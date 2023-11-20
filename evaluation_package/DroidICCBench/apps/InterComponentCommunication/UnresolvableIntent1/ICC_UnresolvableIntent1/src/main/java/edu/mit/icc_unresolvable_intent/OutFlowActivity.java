package edu.mit.icc_unresolvable_intent;

import java.util.ArrayList;
import java.util.Random;

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
 * @testcase_name ICC-Unresolvable-Intent
 * 
 * @description An intent is created with a random selection of 2 constant strings to start an Activity.
 * @dataflow source -> sink
 * @number_of_leaks 2 
 * @challenges   The analysis tool has to be able to identify unresolvable Intent and not associate the unresolved Intent with any Activity 
 */
public class OutFlowActivity extends Activity {

//    private static Random rnd = new Random(System.currentTimeMillis());
	private static String[] actions = new String[] {"edu.mit.icc_unresolvable_intent.ACTION", "edu.mit.icc_unresolvable_intent.EDIT"};

	private int nextAction = 0;

	String nextString(int index) {
        return actions[index];
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				String imei = telephonyManager.getDeviceId(); //source

				Intent i = new Intent(nextString(nextAction));
				i.putExtra("DroidBench", imei);

				startActivity(i);
				nextAction = (nextAction + 1) % 2;
			}
		});


	}

}
