package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name VirtualDispatch1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail siegfried.rasthofer@cased.de
 * 
 * @description This example contains a leakage of the imei in the clickButton() callback.
 *  The data source is placed into the onCreate() callback method in this class. The data sink is placed in the
 *  logData() method of the DataLeak class.
 * @dataflow onCreate: source -> data -> onClick -> DataLeak:logData -> sink 
 * @number_of_leaks 1
 * @challenges the analysis must be able to handle invoke-virtual statements. Additionally the clickButton() 
 * callback must be correctly considered as a callback.  
 */
public class VirtualDispatch1 extends Activity {

	private String imei;
	private int counter = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_virtual_dispatch1);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
				imei = telephonyManager.getDeviceId(); //source
			}
		});
	}

	public void clickButton(View view){
		++counter;
		
		NoDataLeak data = null;
		
		if(counter%2 == 0)
			data = new NoDataLeak("no leak");
		else
			data = new DataLeak(imei);
		
		data.logData();
	}
	

}
