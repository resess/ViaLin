package lu.uni.snt.serval;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * @testcase_name IntentSource1 
 * @version 0.1
 * @author Serval, SnT, University of Luxembourg
 * @author_mail li.li@uni.lu
 * 
 * @description The value of a source is stored in an intent which is a parameter in onActivityResult. The activity use startActivityForResult to call some Intent back.
 * @dataflow intent->startActivityForResult, intent->Log
 * @number_of_leaks 2
 * @challenges the analysis must be able to track the taint in the intent and recognize the onActivityResult(-1,-1,intent) source. 
 *
 */
public class IntentSourceStart extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_activity1);
		Intent m = new Intent();
		m.putExtra("MESSAGE", "m");
		setResult(1, m);
		finish();
	}

	
}
