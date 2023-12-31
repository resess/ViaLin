package com.example.location_broadcast;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class location_broadcast extends BroadcastReceiver implements LocationListener
{

	protected LocationManager locationManager;
    protected LocationListener locationListener;
    protected Context context;
    String loc = " Location: ";
    
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES =5; 
    private static final long MIN_TIME_BW_UPDATES = 1000 * 5; 
    
	@Override
	public void onReceive(Context context, Intent arg1)
	{
		locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, MIN_TIME_BW_UPDATES,MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
		Location location = new Location("");
		location.setLongitude(1.0d);
		location.setLatitude(2.0d);
		onLocationChanged(location);
        
        Intent in = new Intent("com.example.collector");
        in.putExtra(Intent.EXTRA_TEXT, loc);
        in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        in.setType("text/plain");
        context.startActivity(in);
        
	}
	
	 @Override
	    public void onLocationChanged(Location location) {
	        loc = loc.concat("Latitude:");
	        loc = loc.concat(Double.toString(location.getLatitude()));
	        loc = loc.concat(", Longitude:");
	        loc = loc.concat(Double.toString(location.getLongitude()));
			Log.i("DroidBench", "onLocationChanged called");
	    }
	 
	 @Override
	    public void onProviderDisabled(String provider) {
	        Log.d("Latitude", "disable");
	    }

	    @Override
	    public void onProviderEnabled(String provider) {
	        Log.d("Latitude", "enable");
	    }

	    @Override
	    public void onStatusChanged(String provider, int status, Bundle extras) {
	        Log.d("Latitude","status");
	    }

	
}
