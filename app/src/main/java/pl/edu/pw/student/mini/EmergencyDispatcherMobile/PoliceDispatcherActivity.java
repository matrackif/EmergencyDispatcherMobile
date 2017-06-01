package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.Manifest;
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
import android.support.v4.app.FragmentActivity;
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
    LocationManager locationManager;
    LocationListener locationListener;
    private ArrayList<MarkerOptions> markerOptionsList = new ArrayList<>();
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

//            if(!(latLng == null || agentName == null || currentMarkerOptions==null))
//            {
//            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//
//                return;
//            }
//            Location location = locationManager.getLastKnownLocation(locationManager.NETWORK_PROVIDER);
//            Location agentLocation = new Location(locationManager.NETWORK_PROVIDER);
//            agentLocation.setLatitude(latLng.latitude);
//            agentLocation.setLongitude(latLng.longitude);
//            double jobDistance = location.distanceTo(agentLocation)/1000;
//                AlertDialog.Builder alert = new AlertDialog.Builder(getApplicationContext());
//                alert.setMessage("Agent name:" + agentName + " needs some help "+jobDistance +"Km away" );
//                alert.setTitle("New Job Alert!");
//                final MarkerOptions finalCurrentMarkerOptions = currentMarkerOptions;
//
//                alert.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int whichButton) {
//                        Marker m = mMap.addMarker(finalCurrentMarkerOptions);
//                        m.setDraggable(true);
//                        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.0f));
//                    }
//                });
//
//                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int whichButton) {
//
//                    }
//                });
//                alert.show();

//            }

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

    }
    @Override
    protected void onPause(){
        super.onPause();
        timerHandler.removeCallbacks(agentLocationUpdaterRunnable);
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
