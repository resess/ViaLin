package edu.mit.clinit;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Clinit
 * 
 * @description Clinit (static initializer test)
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - The order of execution of static initializers is not defined in Java.  This 
 * test stresses a particular order to link a flow.
 */
public class MainActivity extends Activity {
    public static MainActivity v;
    public String s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	v = this;
	
	super.onCreate(savedInstanceState);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                s = "";
                Test t = new Test();	//could call static initializer if has been called previously

                Log.i("DroidBench", s);  //sink, possible leak depending on runtime execution of Test's clinit
            }
        });
    }
}

class Test {
    static {
	TelephonyManager mgr = (TelephonyManager) MainActivity.v.getSystemService(Activity.TELEPHONY_SERVICE);
	MainActivity.v.s = mgr.getDeviceId();  //source
    }    
}
