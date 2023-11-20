package edu.wayne.cs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

/**
 * @testcase_name Reflection7
 * @author Wayne State University,
 * @author_mail zhenyu.ning@wayne.edu
 * 
 * @description Unusual way to get class.
 * @dataflow onCreate: source -> sink
 * @number_of_leaks 1
 * @challenges The analysis must recognize that all type of reflections.
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
                reflectionTest();
            }
        });
    }


    private void reflectionTest(){

        try {
            Class<?> clz = Class.forName(getString(R.string.class_name));
            Method method = clz.getMethod(getString(R.string.method_name));

            Class<?> clz2 = method.getDeclaringClass();
            BaseClass bc = (BaseClass) clz2.newInstance();
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            bc.imei = telephonyManager.getDeviceId(); // source
            Log.d("DroidBench", (String) method.invoke(bc)); // sink
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}
