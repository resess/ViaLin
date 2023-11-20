package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.widget.Button;

/**
 * @testcase_name Button3
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description A new callback is registered in another callback's handler. The second handler
 * 	leaks the data obtained by the first handler.
 * @dataflow Button1Listener: source -> imei; Button2Listener: imei -> sink
 * @number_of_leaks 1
 * @challenges The analysis must be able to detect callback handlers registered
 * 	in other callback handlers.
 */
public class MainActivity extends Activity {
	
	String imei = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

		Button button1 = (Button) findViewById(R.id.button1);
		button1.setOnClickListener(new Button1Listener(this));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
