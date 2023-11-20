package org.arguslab.icc_implicit_nosrc_nosink;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
/**
 * @testcase_name ICC_Explicit_NoSrc_NoSink
 * @author Fengguo Wei & Sankardas Roy
 * @author_mail fgwei521@gmail.com & sroy@ksu.edu
 *
 * @description Insensitive v is sent to component FooActivity via explicit ICC.
 * 				In FooActivity, it will retrieve value v but not leak it.
 * @dataflow v -> MainActivity's intent -> FooActivity's intent -> v
 * @number_of_leaks 0
 * @challenges The analysis must be able to resolve explicit ICC calls and handle data flow
 * 				across different components.
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
                String v = "noSrc";

                Intent i = new Intent("amandroid.impliciticctest_action.testaction");
                i.putExtra("data", v);
                startActivity(i); // sink
            }
        });

    }

}