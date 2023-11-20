package edu.mit.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

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
 * @testcase_name Serialization
 * 
 * @description Test serialization end to end flow.
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges - must model serialization
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TelephonyManager mgr = (TelephonyManager) MainActivity.this.getSystemService(TELEPHONY_SERVICE);
                String imei = mgr.getDeviceId(); //source
                S s1 = new S(imei);

                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(out);
                    oos.writeObject(s1);
                    oos.close();

                    byte[] bytes = out.toByteArray();

                    ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                    ObjectInputStream iis = new ObjectInputStream(in);
                    S s2 = (S)iis.readObject();
                    iis.close();

                    Log.i("DroidBench", s2.toString());//sink
                } catch (Exception e) {
                }
            }
        });


    }
}

class S implements Serializable {
	
    private static final long serialVersionUID = -1155152173616606359L;

    private String message;
	
    public S(String message) {
        this.message = message;
    }
		
    public String toString() {
        return message;
    }
}
