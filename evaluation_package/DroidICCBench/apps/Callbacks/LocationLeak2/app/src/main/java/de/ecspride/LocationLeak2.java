package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name LocationLeak2
 * @version 0.1
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail siegfried.rasthofer@cased.de
 * 
 * @description This example contains a location information leakage in the onResume() callback method.
 *  The data source is placed into the onLocationChanged() callback method, especially the parameter "loc".
 *  In contrast to LocationLeak1 the activity implements the Listener directly (no inner class).
 * @dataflow onLocationChanged: source -> latitude, longtitude; onResume: latitude -> sink, longtitude -> sink 
 * @number_of_leaks 2
 * @challenges the analysis must be able to emulate the Android activity lifecycle correctly,
 *  integrate the callback method onLocationChanged and detect the callback methods as source.
 */
public class LocationLeak2 extends Activity implements LocationListener{
	private String latitude = "";
	private String longtitude = "";
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_leak2);

		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PackageManager.PERMISSION_GRANTED);

		((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, LocationLeak2.this);
			}
		});
    }

    @Override
    protected void onResume (){
    	super.onResume();
    	
    	Log.d("Latitude", "Latitude: " + latitude); //sink, leak
    	Log.d("Longtitude", "Longtitude: " + longtitude); //sink, leak
    }

    
	@Override  
	 public void onLocationChanged(Location loc) {  //source
		double lat = loc.getLatitude();
		double lon = loc.getLongitude();
			
		this.latitude =  Double.toString(lat);
		this.longtitude = Double.toString(lon);
	 }  

	 @Override  
	 public void onProviderDisabled(String provider) {}  

	 @Override  
	 public void onProviderEnabled(String provider) { }  

	 @Override  
	 public void onStatusChanged(String provider, int status, Bundle extras) {}
}
