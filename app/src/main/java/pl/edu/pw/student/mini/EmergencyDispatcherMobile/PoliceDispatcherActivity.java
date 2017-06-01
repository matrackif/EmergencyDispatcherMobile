package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import jade.core.MicroRuntime;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

public class PoliceDispatcherActivity extends FragmentActivity implements OnMapReadyCallback {

    private Logger logger = Logger.getJADELogger(this.getClass().getName());

    static final int PARTICIPANTS_REQUEST = 0;
    private MyReceiver myReceiver;

    private String nickname;
    private String type;
    private ClientInterface clientInterface;
    private GoogleMap mMap;
    private ArrayList<MarkerOptions> markerOptionsList = new ArrayList<>();
    private Handler timerHandler = new Handler();
    private Runnable agentLocationUpdaterRunnable = new Runnable() {
        @Override
        public void run() {
            markerOptionsList.clear();
            java.util.HashMap<String,LatLng> agentLocations = MainActivity.getKnownAgentLocations();
            for(Map.Entry<String,LatLng> entry : agentLocations.entrySet()){
                LatLng latLng = entry.getValue();
                String agentName = entry.getKey();
                //Log.d("LocationUpdater()", "Found agent with name: " + entry.getKey() + "and at lat/long: " + latLng.latitude + "," + latLng.longitude);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title(agentName);
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                markerOptionsList.add(markerOptions);
            }
            for(MarkerOptions markerOptions : markerOptionsList){
                Marker m = mMap.addMarker(markerOptions);
                m.setDraggable(true);
            }
            timerHandler.postDelayed(this, 1000); //Update locations every second
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

        setContentView(R.layout.police_dispatcher);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        timerHandler.post(agentLocationUpdaterRunnable);

    }
    @Override
    protected void onPause(){
        super.onPause();
        timerHandler.removeCallbacks(agentLocationUpdaterRunnable);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(myReceiver);

        logger.log(Level.INFO, "Destroy activity!");
    }

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
                Intent showParticipants = new Intent(PoliceDispatcherActivity.this,
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

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //If location permission is not allowed handle it here
            return;
        }
        mMap.setMyLocationEnabled(true);
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
                PoliceDispatcherActivity.this);
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
