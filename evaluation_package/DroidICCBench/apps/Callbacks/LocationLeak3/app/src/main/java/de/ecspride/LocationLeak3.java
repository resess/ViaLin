package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name LocationLeak3
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail steven.arzt@cased.de
 * 
 * @description This example contains a location information leakage in the onResume() callback method.
 *  The data source is placed into the onLocationChanged() callback method in a separate class
 *  which sets the data into a field of the activity. Activity and callback are decoupled using an
 *  interface.
 * @dataflow onLocationChanged: source -> data -> onResume -> sink 
 * @number_of_leaks 1
 * @challenges the analysis must be able to emulate the Android activity lifecycle correctly,
 *  integrate the callback method onLocationChanged, detect the callback methods as source
 *  and connect the callback class to the activity via the interface.
 */
public class LocationLeak3 extends Activity implements IDataProvider {

	private String data = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multi_handlers1);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LocationListener locationListener = new MyLocationListener(LocationLeak3.this);
				LocationManager locationManager = (LocationManager)
						getSystemService(Context.LOCATION_SERVICE);
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
			}
		});
	}

	@Override
    protected void onResume (){
    	super.onResume();
    	Log.d("Location", "Location: " + data); //sink, leak
    }

	@Override
	public void setData(String data) {
		this.data = data;
	}
}
