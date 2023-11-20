package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name FactoryMethods1
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description This example obtains a LocationManager from a factory method contained
 * 	in the Android operating system, reads out the location, and leaks it.
 * @dataflow onCreate: source -> data -> sink 
 * @number_of_leaks 2
 * @challenges The analysis must be able to handle factory methods contained in
 * 	the operating system.
 */
public class FactoryMethods1 extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multi_handlers1);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Criteria crit = new Criteria();
				crit.setAccuracy(Criteria.ACCURACY_FINE);
				LocationManager locationManager = (LocationManager)
						getSystemService(Context.LOCATION_SERVICE);
				Location data = locationManager.getLastKnownLocation(locationManager.getBestProvider(crit, true));

				Log.d("Latitude", "Latitude: " + data.getLatitude()); //sink, leak
				Log.d("Longtitude", "Longtitude: " + data.getLongitude()); //sink, leak
			}
		});


	}
	
}
