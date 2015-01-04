package com.mobike.mobike;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * This Activity implements the route recording controls.
 */

public class MapsActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener{

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final String DIALOG_ERROR = "dialog_error";
    private boolean mResolvingError = false;
    private static final int SUMMARY_REQUEST = 1;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LinearLayout buttonLayout;
    private Button start, pause, stop, resume;
    private enum State {BEGIN, RUNNING, PAUSED, STOPPED} // All the possible states
    private State state;        // The current state
    private static final String TAG = "MapsActivity";
    protected static final float CAMERA_ZOOM_VALUE = 15;    // The value of the map zoom. It must
    // be between 2 (min zoom) and 21 (max)
    private static final long MIN_DISTANCE_BW_UPDATES_METERS = 5;
    private static final long MIN_TIME_BW_UPDATES_MS = 1000 * 5; // Minimum time between updates in milliseconds

    private Polyline route;     // The currently recording route to be drawn in the map
    private List<LatLng> points;    // the points of the route

    private Location previousLocation;
    private float totalDistance;

    private boolean isGPSEnabled;

    /**
     * This method is called when the activity is created.
     * It initializes the layout, the map the route to be drawn on the map and the state.
     *
     * @param savedInstanceState I don't know
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        mResolvingError = savedInstanceState != null &&
                savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        setUpMapIfNeeded();
        buttonLayout = (LinearLayout) findViewById(R.id.button_layout);
        start = (Button) findViewById(R.id.start_button);
        state = State.BEGIN;
        LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if(!isGPSEnabled){ showSettingsAlert(); }
        route = mMap.addPolyline(new PolylineOptions().width(6).color(Color.BLUE));
        points = new ArrayList<>();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    public void onConnected(Bundle connectionHint) {
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        mLocationRequest = createLocationRequest();
        totalDistance = 0;
        updateDatabase(mCurrentLocation, true);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.v(TAG, "onConnectionFailed()");

        if (mResolvingError) {
            // Already attempting to resolve an error.
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.v(TAG, "onConnectionSuspended()");
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10*1000);
        mLocationRequest.setFastestInterval(MIN_TIME_BW_UPDATES_MS);
        mLocationRequest.setSmallestDisplacement(MIN_DISTANCE_BW_UPDATES_METERS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    // This code is about building the error dialog

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode, this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((LoginActivity)getActivity()).onDialogDismissed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Keep the screen always on for this activity
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setUpMapIfNeeded();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Return to the default settings, the screen can go off for inactivity
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart()");
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    /**
     * This method creates the items of the options menu.
     * @param menu the options menu
     * @return true
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.map_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This method sets the map up, displaying the user's position and zooming on it.
     *
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        // Enabling MyLocation Layer of Google Map
        mMap.setMyLocationEnabled(true);
        Location mLocation = mMap.getMyLocation();
        LatLng latLng = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
        mMap.addMarker(new MarkerOptions().position(latLng).title("Start"));
        // zooming to the current location
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM_VALUE); //zoom value between 2(min zoom)-21(max zoom)
        mMap.animateCamera(update);
    }

    public void onLocationChanged(Location location){
        updateDatabase(location, false);
        updateUI(location);
    }

    private void updateUI(Location location){
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM_VALUE);
        mMap.animateCamera(update);
        points.add(latLng);
        route.setPoints(points);
    }

    public void updateDatabase(Location location, boolean firstRow)
    {
        GPSDatabase myDatabase = new GPSDatabase(this);
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        double alt = location.getAltitude();
        myDatabase.open();

        if(!firstRow){
            totalDistance = totalDistance + previousLocation.distanceTo(location);
        }
        myDatabase.insertRow(lat, lng, alt, totalDistance);
        previousLocation = location;
        myDatabase.close();
    }


    /**
     * This method is invoked when the "Start" button is pressed.
     * It changes the button in the lower part of the screen
     * and creates the GPSTracker object, which constructor takes as arguments
     * the context and the MapsActivity object (because it implements NewLocationsListener).
     * Once created, the GPSTracker object makes the route recording start.
     *
     * @param view the view
     */
    public void startButtonPressed(View view) {
        if (view.getId() == R.id.start_button) {
            start.setVisibility(View.GONE);
            pause = (Button) getLayoutInflater().inflate(R.layout.pause_button, buttonLayout, false);
            stop = (Button) getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(pause);
            buttonLayout.addView(stop);
            //        startService(new Intent(this, TrackingService.class));
            state = State.RUNNING;
            startLocationUpdates();
        }
    }

    /**
     * This method is invoked when the "Pause" button is pressed.
     * The layout of the lower part of the screen changes and the route recording stops.
     *
     * @param view the view
     */
    public void pauseButtonPressed(View view) {
        if (view.getId() == R.id.pause_button) {
            pause.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            resume = (Button) getLayoutInflater().inflate(R.layout.resume_button, buttonLayout, false);
            stop = (Button) getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(resume);
            buttonLayout.addView(stop);
            state = State.PAUSED;
            stopLocationUpdates();
        }
    }

    /**
     * This method is invoked when the "Resume" button is pressed.
     * The layout of the lower part of the screen changes and the route recording starts again.
     * @param view the view
     */
    public void resumeButtonPressed(View view) {
        if (view.getId() == R.id.resume_button) {
            resume.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            pause = (Button) getLayoutInflater().inflate(R.layout.pause_button, buttonLayout, false);
            stop = (Button) getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(pause);
            buttonLayout.addView(stop);
            state = State.RUNNING;
            startLocationUpdates();
        }
    }

    /**
     * This method is invoked when the "Stop" button is pressed.
     * The route recording is definetely stopped and the SummaryActivity starts.
     *
     * @param view the view
     */
    public void stopButtonPressed(View view) {
        if (view.getId() == R.id.stop_button) {
            if (resume != null) resume.setVisibility(View.GONE);
            if (pause != null) pause.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            start = (Button) getLayoutInflater().inflate(R.layout.start_button, buttonLayout, false);
            buttonLayout.addView(start);

            if(state == State.RUNNING) {
                state = State.STOPPED;
                stopLocationUpdates();
            }
            else { state = State.STOPPED;}


            Intent intent = new Intent(this, SummaryActivity.class);
            startActivityForResult(intent, SUMMARY_REQUEST);


        }
    }

    /**
     * This method shows an alert inviting the user to activate the GPS in the settings menu.
     */
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        // Setting Dialog Title
        alertDialog.setTitle("GPS in settings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    /**
     *this method is called whenever the app comes back to MapsActivity from SummaryActivity
     * @param requestCode boh
     * @param resultCode dunno
     * @param data nada
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        points = new ArrayList<>();
        route.setPoints(points);

        Log.v(TAG, "onActivityResult()");
    }
}
