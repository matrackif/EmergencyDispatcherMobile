
package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.StringReader;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.MicroRuntime;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.StringACLCodec;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

public class ParticipantsActivity extends ListActivity {
	private Logger logger = Logger.getJADELogger(this.getClass().getName());

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
	}

	private OnItemClickListener listViewtListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			TextView procTextView =(TextView) parent.getChildAt(position);
			String[] procItem = procTextView.getText().toString().split("_");
			logger.log(Logger.INFO,procItem[0]);
			String procName = procItem[0];

			logger.log(Logger.WARNING,"Name is :"+procName);
			AID aid_rec = new AID();

			String aux = procItem[2];

			StringACLCodec codec = new StringACLCodec(new StringReader(aux), null);
			try {
				aid_rec = codec.decodeAID();
				logger.log(Logger.WARNING,"Scam AID:"+aid_rec);
			} catch (ACLCodec.CodecException e) {
				e.printStackTrace();
			}

			clientInterface.handleSpoken("HELP! Specific ",aid_rec);

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
