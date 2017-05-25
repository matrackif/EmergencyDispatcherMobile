
package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.util.logging.Level;

import jade.core.MicroRuntime;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.O2AException;
import jade.wrapper.StaleProxyException;


public class UserDispatcherActivity extends Activity {
	private Logger logger = Logger.getJADELogger(this.getClass().getName());

	static final int PARTICIPANTS_REQUEST = 0;

	private MyReceiver myReceiver;

	private String nickname;
	private ClientInterface clientInterface;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			nickname = extras.getString("nickname");
		}

		try {
			clientInterface = MicroRuntime.getAgent(nickname)
					.getO2AInterface(ClientInterface.class);
		} catch (StaleProxyException e) {
			showAlertDialog(getString(R.string.msg_interface_exc), true);
		} catch (ControllerException e) {
			showAlertDialog(getString(R.string.msg_controller_exc), true);
		}

		myReceiver = new MyReceiver();

		setContentView(R.layout.user_dispatcher);

		Button buttonPolice = (Button) findViewById(R.id.button_police);
		buttonPolice.setOnClickListener(PoliceSendListener);
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();

		unregisterReceiver(myReceiver);

		logger.log(Level.INFO, "Destroy activity!");
	}

	private OnClickListener PoliceSendListener = new OnClickListener() {
		public void onClick(View v) {

			String message = "HELP!!!";
			if (message != null && !message.equals("")) {
				try {
					clientInterface.handleSpoken(message);

				} catch (O2AException e) {
					showAlertDialog(e.getMessage(), false);
				}
			}

		}
	};
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.room_menu, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_participants:
			Intent showParticipants = new Intent(UserDispatcherActivity.this,
					ParticipantsActivity.class);
			showParticipants.putExtra("nickname", nickname);
			startActivityForResult(showParticipants, PARTICIPANTS_REQUEST);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PARTICIPANTS_REQUEST) {
			if (resultCode == RESULT_OK) {
				// TODO: A participant was picked. Send a private message.
			}
		}
	}
	private class MyReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			logger.log(Level.INFO, "Received intent " + action);

		}
	}
	private void showAlertDialog(String message, final boolean fatal) {
		AlertDialog.Builder builder = new AlertDialog.Builder(
				UserDispatcherActivity.this);
		builder.setMessage(message)
				.setCancelable(false)
				.setPositiveButton("Ok",
						new DialogInterface.OnClickListener() {
							public void onClick(
									DialogInterface dialog, int id) {
								dialog.cancel();
								if(fatal) finish();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();		
	}
}