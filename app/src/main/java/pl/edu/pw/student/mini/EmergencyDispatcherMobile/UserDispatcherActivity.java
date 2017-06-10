
package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.util.logging.Level;

import jade.core.MicroRuntime;
import jade.lang.acl.ACLMessage;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.O2AException;
import jade.wrapper.StaleProxyException;


public class UserDispatcherActivity extends Activity {
	private Logger logger = Logger.getJADELogger(this.getClass().getName());

	static final int PARTICIPANTS_REQUEST = 0;

	private MyReceiver myReceiver;

	private String nickname;
	private String type;
	private ClientInterface clientInterface;
	private LocationManager locationManager;
	private LocationListener locationListener;
	private Handler timerHandler = new Handler();
	public static final String REQUEST_POLICE = "REQUESTING_POLICE";
	public static final String REQUEST_AMBULANCE = "REQUESTING_AMBULANCE";
	public static final String REQUEST_ELECTRICITY = "REQUESTING_ELECTRICIAN";
	public static final String REQUEST_FIRE_DEPARTMENT = "REQUESTING_FIREFIGHTER";
	private Runnable sendCurrentLocationRunnable = new Runnable() {
		@Override
		public void run() {
			if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				//handle if location is not on
				Log.e("GPS_ERROR", "Could not get GPS permission");
				return;
			}
			Location location =  locationManager.getLastKnownLocation(locationManager.NETWORK_PROVIDER);
			String latLongString = null;
			if(location != null) {
				latLongString = location.getLatitude() + "_" + location.getLongitude();
				clientInterface.handleSpoken(latLongString, ACLMessage.INFORM);
			}
			timerHandler.postDelayed(this, 5000); //broadcast location every  5 sec
			//TODO send this info to chat manager and let him broadcast it instead (or maybe not?)
		}
	};
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			nickname = extras.getString("nickname");
			type = extras.getString("type");
		}

		try {
			clientInterface = MicroRuntime.getAgent(nickname)
					.getO2AInterface(ClientInterface.class);
			clientInterface.setType(type);
		} catch (StaleProxyException e) {
			showAlertDialog(getString(R.string.msg_interface_exc), true);
		} catch (ControllerException e) {
			showAlertDialog(getString(R.string.msg_controller_exc), true);
		}

		myReceiver = new MyReceiver();

		setContentView(R.layout.user_dispatcher);
		//Adding handlers to all the buttons

		Button buttonPolice = (Button) findViewById(R.id.button_police);
		buttonPolice.setOnClickListener(PoliceSendListener);

		Button buttonAmbulance = (Button) findViewById(R.id.button_ambulance);
		buttonAmbulance.setOnClickListener(AmbulanceSendListener);

		Button buttonElectricity = (Button) findViewById(R.id.button_electricity);
		buttonElectricity.setOnClickListener(ElectricitySendListener);

		Button buttonFire = (Button) findViewById(R.id.button_fire);
		buttonFire.setOnClickListener(FireSendListener);

		locationManager = (LocationManager)
				getSystemService(Context.LOCATION_SERVICE);
		locationListener = new LocationListener() {
			@Override
			public void onLocationChanged(Location location) {

			}

			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {

			}

			@Override
			public void onProviderEnabled(String provider) {
				Toast.makeText(getApplicationContext(), "Gps is turned on!! ",
						Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onProviderDisabled(String provider) {
				Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				startActivity(intent);
				Toast.makeText(getApplicationContext(), "Gps is turned off!! ",
						Toast.LENGTH_SHORT).show();

			}
		};
		timerHandler.postDelayed(sendCurrentLocationRunnable, 5000);
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(myReceiver);
		logger.log(Level.INFO, "Destroy activity!");
	}

	private OnClickListener PoliceSendListener = new OnClickListener() {
		public void onClick(View v) {
				try {
					Log.d("PoliceSendListener", "Send help button clicked so we are sending help message now");
					clientInterface.handleSpoken(REQUEST_POLICE, ACLMessage.REQUEST);
				} catch (O2AException e) {
					showAlertDialog(e.getMessage(), false);
				}
		}
	};
	private OnClickListener FireSendListener = new OnClickListener() {
		public void onClick(View v) {
			try {
				Log.d("PoliceSendListener", "Send help button clicked so we are sending help message now");
				clientInterface.handleSpoken(REQUEST_FIRE_DEPARTMENT, ACLMessage.REQUEST);
			} catch (O2AException e) {
				showAlertDialog(e.getMessage(), false);
			}
		}
	};
	private OnClickListener ElectricitySendListener = new OnClickListener() {
		public void onClick(View v) {
			try {
				Log.d("PoliceSendListener", "Send help button clicked so we are sending help message now");
				clientInterface.handleSpoken(REQUEST_ELECTRICITY, ACLMessage.REQUEST);
			} catch (O2AException e) {
				showAlertDialog(e.getMessage(), false);
			}
		}
	};
	private OnClickListener AmbulanceSendListener = new OnClickListener() {
		public void onClick(View v) {
			try {
				Log.d("PoliceSendListener", "Send help button clicked so we are sending help message now");
				clientInterface.handleSpoken(REQUEST_AMBULANCE, ACLMessage.REQUEST);
			} catch (O2AException e) {
				showAlertDialog(e.getMessage(), false);
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