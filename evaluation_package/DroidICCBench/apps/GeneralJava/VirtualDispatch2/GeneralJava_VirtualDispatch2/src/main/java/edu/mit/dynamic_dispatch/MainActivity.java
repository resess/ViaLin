package edu.mit.dynamic_dispatch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Dynamic-Dispatch
 * 
 * @description Testing dispatching of overiding methods
 * @dataflow source -> sink
 * @number_of_leaks 1
 * @challenges The analysis tool has to be able to differentiate the base and the derived class objects
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);

        ((Button) findViewById(R.id.trigger)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                B.mgr = (TelephonyManager) MainActivity.this.getSystemService(Activity.TELEPHONY_SERVICE);

                Test test1 = new Test();
                Test test2 = new Test();
                A b = new B();
                A c = new C();

                SmsManager smsmanager = SmsManager.getDefault();

                smsmanager.sendTextMessage("+49 1234", null, test1.method(b), null, null); //sink, leak
                Log.i("DroidBench", test2.method(c)); //sink, no leak
            }
        });
     }
}

class Test {
    public String method(A a) {        
        return a.f();  // uses the context insensitive pta for call targets
    }
}

class A {
    public String f() {
        return "untainted";
    }
}

class B extends A {
    public static TelephonyManager mgr;
    public String f() {
        return mgr.getDeviceId(); //source
    }
}

class C extends A {
    public String f() {
        return "not tainted";
    }
}
