package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
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

import jade.core.AID;
import jade.core.MicroRuntime;
import jade.util.Logger;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

public class PoliceDispatcherActivity extends FragmentActivity implements OnMapReadyCallback {

    private Logger logger = Logger.getJADELogger(this.getClass().getName());

    static final int PARTICIPANTS_REQUEST = 0;
    private MyReceiver myReceiver;
    public static final String ACTION_REQUEST_HELP = "jade.demo.user_dispatcher.REQUEST_HELP";
    private String nickname;
    private String type;
    private ClientInterface clientInterface;
    private GoogleMap mMap;
    private ArrayList<MarkerOptions> markerOptionsList = new ArrayList<>();
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler timerHandler = new Handler();
    private Runnable agentLocationUpdaterRunnable = new Runnable() {
        @Override
        public void run() {
            MarkerOptions currentMarkerOptions = null;
            LatLng latLng = null;
            String agentName = null;
            String oldAgentName = null;
            markerOptionsList.clear();
            mMap.clear();
            //clearing the old markers but flickering can be seen cause this method runs every second which is not good
            java.util.HashMap<String, LatLng> agentLocations = MainActivity.getKnownAgentLocations();
            for (Map.Entry<String, LatLng> entry : agentLocations.entrySet()) {
                latLng = entry.getValue();
                agentName = entry.getKey();

                //Log.d("LocationUpdater()", "Found agent with name: " + entry.getKey() + "and at lat/long: " + latLng.latitude + "," + latLng.longitude);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title(agentName);
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                markerOptionsList.add(markerOptions);
            }
            for (MarkerOptions markerOptions : markerOptionsList) {

                currentMarkerOptions = markerOptions;
                Marker m = mMap.addMarker(currentMarkerOptions);
                m.setDraggable(true);
                //TODO: We need to make it run only once or when we receive a job so we can accept the job properly.

            }
            timerHandler.postDelayed(this, 1000); //Update locations every second
        }
    };
    private Runnable sendCurrentLocationRunnable = new Runnable() {
        @Override
        public void run() {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //handle if location is not on
                Log.e("GPS_ERROR", "Could not get GPS permission");
                return;
            }
            Location location = locationManager.getLastKnownLocation(locationManager.NETWORK_PROVIDER);
            String latLongString = null;
            if (location != null) {
                latLongString = location.getLatitude() + "_" + location.getLongitude();
                clientInterface.handleSpoken(latLongString);
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

        IntentFilter requestHelpFilter = new IntentFilter();
        requestHelpFilter.addAction(ACTION_REQUEST_HELP);
        registerReceiver(myReceiver, requestHelpFilter);

        setContentView(R.layout.police_dispatcher);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
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
        timerHandler.post(agentLocationUpdaterRunnable);
        timerHandler.postDelayed(sendCurrentLocationRunnable, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(agentLocationUpdaterRunnable); // TODO not sure if necessary
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(myReceiver!=null){
            unregisterReceiver(myReceiver);
            logger.log(Level.INFO, "Destroy activity!");
        }


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
        Location location = locationManager.getLastKnownLocation(locationManager.NETWORK_PROVIDER);
        if(location!=null)
        {
            mapInit(location);
        }
    }


    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logger.log(Level.INFO, "Received intent " + action);
            try {
                if (action.equalsIgnoreCase(ACTION_REQUEST_HELP)) {
                    String sentence = intent.getStringExtra("sentence");
                    final String speaker = sentence.split(":")[0];
                    Log.d("MyReceiver", "Agent with name: " + speaker + " needs help.");
                    final AID agentInNeed = new AID(speaker, false);
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    //Yes button clicked
                                    //TODO send REQUEST_ACCEPTED message
                                    clientInterface.handleSpoken("I will help you", agentInNeed); // This is for testing with the desktop ChatClient
                                    break;

                                case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    //TODO send REQUEST_REJECTED message
                                    clientInterface.handleSpoken("I will not help you", agentInNeed); // This is for testing with the desktop ChatClient
                                    break;
                            }
                        }
                    };

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage("Client with name " + speaker + " needs help, assist him?").setPositiveButton("Yes", dialogClickListener)
                            .setNegativeButton("No", dialogClickListener).show();


                }
            } catch (Exception e) {
                Log.e("ERROR", "Could not parse the help message or another error occured: " + e.toString());
            }
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

    private void mapInit(Location location) {
        double latitude = location.getLatitude();
        double longitude=location.getLongitude();
        LatLng loc = new LatLng(latitude, longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 14.0f));

    }




}
