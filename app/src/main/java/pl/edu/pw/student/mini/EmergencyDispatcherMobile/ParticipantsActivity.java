package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.StringReader;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.MicroRuntime;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.StringACLCodec;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

public class ParticipantsActivity extends ListActivity {
	private Logger logger = Logger.getJADELogger(this.getClass().getName());

	private MyReceiver myReceiver;

	private String nickname;
	private ClientInterface clientInterface;
	LocationManager locationManager;
	LocationListener locationListener;


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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		myReceiver = new MyReceiver();

		IntentFilter refreshParticipantsFilter = new IntentFilter();
		refreshParticipantsFilter
				.addAction("jade.demo.user_dispatcher.REFRESH_PARTICIPANTS");
		registerReceiver(myReceiver, refreshParticipantsFilter);

		setContentView(R.layout.participants);

		setListAdapter(new ArrayAdapter<String>(this, R.layout.participant,
				clientInterface.getParticipantNames()));

		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		listView.setOnItemClickListener(listViewtListener);

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


	}

	private OnItemClickListener listViewtListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position,
								long id) {
			TextView procTextView = (TextView) parent.getChildAt(position);
			String[] procItem = procTextView.getText().toString().split("_");
			logger.log(Logger.INFO, procItem[0]);
			String procName = procItem[0];

			logger.log(Logger.WARNING, "Name is :" + procName);
			AID aid_rec = new AID();

			String aux = procItem[2];

			StringACLCodec codec = new StringACLCodec(new StringReader(aux), null);
			try {
				aid_rec = codec.decodeAID();
				logger.log(Logger.WARNING, "Scam AID:" + aid_rec);
			} catch (ACLCodec.CodecException e) {
				e.printStackTrace();
			}
			if(MainActivity.getType().equalsIgnoreCase(MainActivity.USER)){
				clientInterface.handleSpoken(UserDispatcherActivity.REQUEST_POLICE, aid_rec, ACLMessage.REQUEST);
			}
			//TODO if police then maybe send backup?
			//finish();
		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();

		unregisterReceiver(myReceiver);

		logger.log(Level.INFO, "Destroy activity!");
	}

	private class MyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			logger.log(Level.INFO, "Received intent " + action);
			if (action.equalsIgnoreCase("jade.demo.user_dispatcher.REFRESH_PARTICIPANTS")) {
				setListAdapter(new ArrayAdapter<String>(
						ParticipantsActivity.this, R.layout.participant,
						clientInterface.getParticipantNames()));
			}
		}
	}

}
