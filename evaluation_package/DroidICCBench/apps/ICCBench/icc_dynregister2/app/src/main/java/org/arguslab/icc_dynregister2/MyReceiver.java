package org.arguslab.icc_dynregister2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context arg0, Intent arg1) {
        Log.d("leak", "" + arg1.getStringExtra("id")); //sink
    }
}
