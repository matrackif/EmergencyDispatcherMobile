

package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import jade.android.AndroidHelper;
import jade.android.MicroRuntimeService;
import jade.android.MicroRuntimeServiceBinder;
import jade.android.RuntimeCallback;
import jade.core.MicroRuntime;
import jade.core.Profile;
import jade.util.Logger;
import jade.util.leap.HashMap;
import jade.util.leap.Properties;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;

public class MainActivity extends Activity {
	private Logger logger = Logger.getJADELogger(this.getClass().getName());

	private MicroRuntimeServiceBinder microRuntimeServiceBinder;
	private ServiceConnection serviceConnection;
	private static java.util.HashMap<String, LatLng> knownLocationsOfAgents = new java.util.HashMap<>(); // AID.LocalName -> LatLng
	static final int DISPATCH_REQUEST = 0;
	static final int SETTINGS_REQUEST = 1;

	private MyReceiver myReceiver;
	private MyHandler myHandler;
	public static final String USER = "User";
	public static final String POLICE = "Police";
	private TextView infoTextView;

	private String nickname;
	private static String type;
	public static final String ACTION_SEND_LAT_LONG = "jade.demo.user_dispatcher.SEND_LAT_LNG";

	Spinner postSpinner;
	public static String getType()
	{
		return type;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		myReceiver = new MyReceiver();
		//REGISTERING OUR RECEIVER WITH OUT ACTION
		IntentFilter killFilter = new IntentFilter();
		killFilter.addAction("jade.demo.user_dispatcher.KILL");
		registerReceiver(myReceiver, killFilter);

		IntentFilter showDispatcherFilter = new IntentFilter();
		showDispatcherFilter.addAction("jade.demo.user_dispatcher.SHOW_DISPATCHER");
		registerReceiver(myReceiver, showDispatcherFilter);

		IntentFilter sendLatLongFilter = new IntentFilter();
		sendLatLongFilter.addAction(ACTION_SEND_LAT_LONG);
		registerReceiver(myReceiver, sendLatLongFilter);

		myHandler = new MyHandler();

		setContentView(R.layout.main);

		Button button = (Button) findViewById(R.id.button_chat);
		button.setOnClickListener(buttonChatListener);

		postSpinner = (Spinner)findViewById(R.id.edit_post);

		ImageView loginLogo = (ImageView) findViewById(R.id.imageView);
		loginLogo.setImageResource(R.mipmap.ic_launcher);


		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.posts_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		postSpinner.setAdapter(adapter);
		postSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				((TextView) parent.getChildAt(0)).setTextSize(20);
				//type = (String) ((TextView) parent.getChildAt(0)).getText();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});


		infoTextView = (TextView) findViewById(R.id.infoTextView);
		infoTextView.setText("");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		unregisterReceiver(myReceiver);

		logger.log(Level.INFO, "Destroy activity!");
	}

	private static boolean checkName(String name) {
		if (name == null || name.trim().equals("")) {
			return false;
		}
		return true;
	}
	public static java.util.HashMap<String,LatLng> getKnownAgentLocations(){
		return knownLocationsOfAgents;
	}
	private OnClickListener buttonChatListener = new OnClickListener() {
		public void onClick(View v) {
			final EditText nameField = (EditText) findViewById(R.id.edit_nickname);
			nickname = nameField.getText().toString();
			if (!checkName(nickname)) {
				logger.log(Level.INFO, "Invalid nickname!");
				myHandler.postError(getString(R.string.msg_nickname_not_valid));
			} else {
				try {
					SharedPreferences settings = getSharedPreferences(
							"jadeChatPrefsFile", 0);
					String host = settings.getString("defaultHost", "");
					String port = settings.getString("defaultPort", "");
					infoTextView.setText(getString(R.string.msg_connecting_to)
							+ " " + host + ":" + port + "...");
					type = postSpinner.getSelectedItem().toString();
					startDispatcher(nickname, host, port, agentStartupCallback);

				} catch (Exception ex) {
					logger.log(Level.SEVERE, "Unexpected exception creating user_dispatcher agent!");
					infoTextView.setText(getString(R.string.msg_unexpected));
				}
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent showSettings = new Intent(MainActivity.this,
					SettingsActivity.class);
			MainActivity.this.startActivityForResult(showSettings,
					SETTINGS_REQUEST);
			return true;
		case R.id.menu_exit:
			finish();
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == DISPATCH_REQUEST) {
			if (resultCode == RESULT_CANCELED) {
				// The user_dispatcher activity was closed.
				infoTextView.setText("");
				logger.log(Level.INFO, "Stopping Jade...");
				microRuntimeServiceBinder
						.stopAgentContainer(new RuntimeCallback<Void>() {
							@Override
							public void onSuccess(Void thisIsNull) {
							}

							@Override
							public void onFailure(Throwable throwable) {
								logger.log(Level.SEVERE, "Failed to stop the "
										+ ClientAgent.class.getName()
										+ "...");
								agentStartupCallback.onFailure(throwable);
							}
						});
			}
		}
	}

	private RuntimeCallback<AgentController> agentStartupCallback = new RuntimeCallback<AgentController>() {
		@Override
		public void onSuccess(AgentController agent) {
		}

		@Override
		public void onFailure(Throwable throwable) {
			logger.log(Level.INFO, "Nickname already in use!");
			myHandler.postError(getString(R.string.msg_nickname_in_use));
		}
	};

	public void ShowDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setMessage(message).setCancelable(false)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private class MyReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			//We got here because some class called the "context.SendBroadcast()" method
			//This is how the other classes can communicate with the MainActivity
			////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			///IMPORTANT: EVERY TIME WE ADD A NEW ACTION WE HAVE TO REGISTER IT WITH THE BROADCAST RECEIVER IN MainActivity.java (onCreate())///
			////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			String action = intent.getAction();
			logger.log(Level.INFO, "Received intent " + action);

			if (action.equalsIgnoreCase("jade.demo.user_dispatcher.KILL")) {
				finish();
			}
			else if (action.equalsIgnoreCase("jade.demo.user_dispatcher.SHOW_DISPATCHER")) {

				if(Objects.equals(type, USER))
				{
					Intent showUserDispatcher = new Intent(MainActivity.this,
							UserDispatcherActivity.class);
					showUserDispatcher.putExtra("nickname", nickname);
					showUserDispatcher.putExtra("type", type);
					MainActivity.this
							.startActivityForResult(showUserDispatcher, DISPATCH_REQUEST);
				}
				if(Objects.equals(type, POLICE))
				{
					Intent showPoliceDispatcher = new Intent(MainActivity.this,
							PoliceDispatcherActivity.class);
					showPoliceDispatcher.putExtra("nickname", nickname);
					showPoliceDispatcher.putExtra("type",type);
					MainActivity.this
							.startActivityForResult(showPoliceDispatcher, DISPATCH_REQUEST);
				}

			}
			else if (action.equalsIgnoreCase(ACTION_SEND_LAT_LONG)){
				//Someone sent us their latitude and longitude, decode the message and do the appropriate action
				//To see the format of this message, see the handleReceivedMessage() method in ClientAgent.java
				//The format is something like: <SPEAKER>:LATITUDE_LONGITUDE
				//So we have to split the string twice (lol)
				try{
					String sentence = intent.getStringExtra("sentence");
					String[] speakerAndLatLong = sentence.split(":");
					String speaker = speakerAndLatLong[0];
					String lat = speakerAndLatLong[1].split("_")[0];
					String lng = speakerAndLatLong[1].split("_")[1];
					Log.i("LatLng", "The speaker: " + speaker + " has sent us lat and long: (" + lat + "," + lng + ")");
					//TODO store latitude and longitude of agents somewhere for future use
					double latAsDouble = Double.parseDouble(lat);
					double lngAsDouble = Double.parseDouble(lng);
					LatLng latLng = new LatLng(latAsDouble, lngAsDouble);
					knownLocationsOfAgents.put(speaker,latLng);
				} catch(Exception e){
					Log.e("ERROR", "Could not parse the latitude/longitude message correctly: " + e.toString());
				}



			}
		}
	}

	private class MyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			if (bundle.containsKey("error")) {
				infoTextView.setText("");
				String message = bundle.getString("error");
				ShowDialog(message);
			}
		}

		public void postError(String error) {
			Message msg = obtainMessage();
			Bundle b = new Bundle();
			b.putString("error", error);
			msg.setData(b);
			sendMessage(msg);
		}
	}

	public void startDispatcher(final String nickname, final String host,
								final String port,
								final RuntimeCallback<AgentController> agentStartupCallback) {

		final Properties profile = new Properties();
		profile.setProperty(Profile.MAIN_HOST, host);
		profile.setProperty(Profile.MAIN_PORT, port);
		profile.setProperty(Profile.MAIN, Boolean.FALSE.toString());
		profile.setProperty(Profile.JVM, Profile.ANDROID);

		if (AndroidHelper.isEmulator()) {
			// Emulator: this is needed to work with emulated devices
			profile.setProperty(Profile.LOCAL_HOST, AndroidHelper.LOOPBACK);
		} else {
			profile.setProperty(Profile.LOCAL_HOST,
					AndroidHelper.getLocalIPAddress());
		}
		// Emulator: this is not really needed on a real device
		profile.setProperty(Profile.LOCAL_PORT, "2000");

		if (microRuntimeServiceBinder == null) {
			serviceConnection = new ServiceConnection() {
				public void onServiceConnected(ComponentName className,
						IBinder service) {
					microRuntimeServiceBinder = (MicroRuntimeServiceBinder) service;
					logger.log(Level.INFO, "Gateway successfully bound to MicroRuntimeService");
					startContainer(nickname, profile, agentStartupCallback);
				};

				public void onServiceDisconnected(ComponentName className) {
					microRuntimeServiceBinder = null;
					logger.log(Level.INFO, "Gateway unbound from MicroRuntimeService");
				}
			};
			logger.log(Level.INFO, "Binding Gateway to MicroRuntimeService...");
			bindService(new Intent(getApplicationContext(),
					MicroRuntimeService.class), serviceConnection,
					Context.BIND_AUTO_CREATE);
		} else {
			logger.log(Level.INFO, "MicroRumtimeGateway already binded to service");
			startContainer(nickname, profile, agentStartupCallback);
		}
	}

	private void startContainer(final String nickname, Properties profile,
			final RuntimeCallback<AgentController> agentStartupCallback) {
		if (!MicroRuntime.isRunning()) {
			microRuntimeServiceBinder.startAgentContainer(profile,
					new RuntimeCallback<Void>() {
						@Override
						public void onSuccess(Void thisIsNull) {
							logger.log(Level.INFO, "Successfully start of the container...");
							startAgent(nickname, agentStartupCallback);
						}

						@Override
						public void onFailure(Throwable throwable) {
							logger.log(Level.SEVERE, "Failed to start the container...");
						}
					});
		} else {
			startAgent(nickname, agentStartupCallback);
		}
	}

	private void startAgent(final String nickname,
			final RuntimeCallback<AgentController> agentStartupCallback) {
		microRuntimeServiceBinder.startAgent(nickname,
				ClientAgent.class.getName(),
				new Object[] { getApplicationContext() },
				new RuntimeCallback<Void>() {
					@Override
					public void onSuccess(Void thisIsNull) {

						logger.log(Level.INFO, "Successfully start of the "
								+ ClientAgent.class.getName() + "...");
						try {
							agentStartupCallback.onSuccess(MicroRuntime
									.getAgent(nickname));
						} catch (ControllerException e) {
							// Should never happen
							agentStartupCallback.onFailure(e);
						}
					}

					@Override
					public void onFailure(Throwable throwable) {
						logger.log(Level.SEVERE, "Failed to start the "
								+ ClientAgent.class.getName() + "...");
						agentStartupCallback.onFailure(throwable);
					}
				});
	}

}
