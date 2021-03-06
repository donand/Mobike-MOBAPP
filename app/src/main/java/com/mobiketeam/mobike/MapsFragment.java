package com.mobiketeam.mobike;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.CardView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.maps.android.clustering.ClusterManager;
import com.mobiketeam.mobike.utils.Crypter;
import com.mobiketeam.mobike.utils.POI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This Fragment implements the route recording controls.
 */

public class MapsFragment extends android.support.v4.app.Fragment implements
        NewLocationListener, View.OnClickListener {

    public static final String POI_LATITUDE = "com.mobike.mobike.poi_latitude";
    public static final String POI_LONGITUDE = "com.mobike.mobike.poi_longitude";
    public static final String POI_RECORDING = "com.mobike.mobike.poi_recording";
    public static final String ALL_POIS_URL = "http://mobike.ddns.net/SRV/pois/retrieveall";

    private Location mCurrentLocation;
    private ActionBarActivity activity;

    private static final int SUMMARY_REQUEST = 1;
    private static final int REQUEST_PLACE_PICKER = 2;
    private static final int POI_CREATION_REQUEST = 3;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LinearLayout buttonLayout;
    private ImageButton pause, stop, resume;
    private ImageButton start;
    private CardView currentLengthDurationCard;

    private ArrayList<Marker> viewpoint = new ArrayList<>();
    private ArrayList<Marker> gasStation = new ArrayList<>();
    private ArrayList<Marker> restaurant = new ArrayList<>();
    private ArrayList<Marker> other = new ArrayList<>();

    private enum State {BEGIN, RUNNING, PAUSED, STOPPED} // All the possible states

    private State state;        // The current state
    private static final String TAG = "MapsFragment";
    protected static final float CAMERA_ZOOM_VALUE = 15;    // The value of the map zoom. It must
    // be between 2 (min zoom) and 21 (max)

    private Polyline route;     // The currently recording route to be drawn in the map
    private List<LatLng> points;    // the points of the route

    private GPSService gpsService;  //the reference to the Service
    private boolean registered; //true if at least one location was inserted in the database
    private boolean back; // true if i'm returned from SummaryActivity


    /**
     * This method is called when the activity is created.
     * It initializes the layout, the map the route to be drawn on the map and the state.
     *
     * @param savedInstanceState bundle for previous saved state
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //bisogna ripristinare lo stato salvato, quindi bottoni, percorso sulla mappa e variabili

        /*mResolvingError = savedInstanceState != null &&
                savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);*/
        // checks the GPS status and, it is disabled, shows the user an alert
        checkGPSStatus();
        registered = false;
        gpsService = new GPSService(getActivity(), this);
        Log.v(TAG, "onCreate()");
    }

    /**
     * Fragment lifecycle method, starts download of all POIs and setup the UI
     */
    @Override
    public void onStart() {
        super.onStart();
        if (!back) {
            setUp();
            downloadPOIs(ALL_POIS_URL);
        }
        Log.v(TAG, "onStart()");
    }

    /**
     * Initializes the UI
     */
    private void setUp() {
        buttonLayout = (LinearLayout) getView().findViewById(R.id.button_layout);
        start = (ImageButton) getView().findViewById(R.id.start_button);
        state = State.BEGIN;
        start.setOnClickListener(this);
        ((ImageButton) getActivity().findViewById(R.id.places_nearby_button)).setOnClickListener(this);
        ((ImageButton) getActivity().findViewById(R.id.new_poi_button)).setOnClickListener(this);

        CheckBox checkBox = (CheckBox) getActivity().findViewById(R.id.viewpoint_checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!isChecked)
                    showHiddenPOIs(viewpoint);
                else
                    hideDisplayedPOIs(viewpoint);
            }
        });

        checkBox = (CheckBox) getActivity().findViewById(R.id.gas_station_checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!isChecked)
                    showHiddenPOIs(gasStation);
                else
                    hideDisplayedPOIs(gasStation);
            }
        });

        checkBox = (CheckBox) getActivity().findViewById(R.id.restaurant_checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!isChecked)
                    showHiddenPOIs(restaurant);
                else
                    hideDisplayedPOIs(restaurant);
            }
        });

        checkBox = (CheckBox) getActivity().findViewById(R.id.other_checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!isChecked)
                    showHiddenPOIs(other);
                else
                    hideDisplayedPOIs(other);
            }
        });

        //    setUpMapIfNeeded();
    }

    private void showHiddenPOIs(ArrayList<Marker> array) {
        for (Marker m: array)
            m.setVisible(false);
    }

    private void hideDisplayedPOIs(ArrayList<Marker> array) {
        for (Marker m: array)
            m.setVisible(true);
    }

    /**
     * Fragment lifecycle method
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_maps, container, false);
        Log.v(TAG, "onCreateView()");
        return rootView;
    }

    /**
     * This method checks the GPS status and if it is not enabled, shows the user a dialog.
     */
    private void checkGPSStatus() {
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isGPSEnabled) {
            showSettingsAlert();
        }
    }

    /**
     * This method updates the map, making it center on the last location known.
     *
     * @param location the last location known.
     */
    public void updateCamera(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        // zooming to the current location
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM_VALUE); //zoom value between 2(min zoom)-21(max zoom)
        mMap.animateCamera(update);
    }

    /**
     * Fragment lifecycle method
     */
    @Override
    public void onResume() {
        super.onResume();
        activity = (ActionBarActivity) getActivity();

        // Keep the screen always on for this activity
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setUpMapIfNeeded();
        Log.v(TAG, "onResume()");
    }

    /**
     * Fragment lifecycle method
     */
    @Override
    public void onPause() {
        super.onPause();

        // Return to the default settings, the screen can go off for inactivity
        getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.v(TAG, "onPause()");
    }

    /**
     * Fragment lifecycle method
     */
    @Override
    public void onStop() {
        super.onStop();
        Log.v(TAG, "onStop()");
    }

    /**
     * This method creates the items of the options menu.
     *
     * @param menu the options menu
     */

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_map, menu);
        super.onCreateOptionsMenu(menu, inflater);
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
            mMap = ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This method sets the map up, initializing the route.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        // Enabling MyLocation Layer of Google Map
        mMap.setMyLocationEnabled(true);
        //initialises the route on the map
        route = mMap.addPolyline(new PolylineOptions().width(6).color(Color.BLUE));
        points = new ArrayList<>();
    }

    /**
     * This method updates the map, adding the last known location to the route drawn on it.
     *
     * @param location the last known location.
     */
    private void updateUIRoute(Location location, float length, long duration) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        points.add(latLng);
        route.setPoints(points);
        TextView l = (TextView) getActivity().findViewById(R.id.current_length);
        TextView d = (TextView) getActivity().findViewById(R.id.current_duration);
        if (l != null)
            l.setText(String.format("%.01f", length / 1000) + " km");
        if (d != null)
            d.setText(String.valueOf(duration / 3600) + " h " + String.valueOf((duration / 60) % 60) + " m " + String.valueOf(duration % 60) + " s");
    }

    /**
     * Send a HTTP GET request at this url
     *
     * @param url
     */
    private void downloadPOIs(String url) {
        RequestQueue queue = Volley.newRequestQueue(getActivity());
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        savePOIs(response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.v(TAG, "Errore nel download di tutti i POIs");
            }
        });
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
        Log.v(TAG, "downloadPOIs url: " + url);
    }

    /**
     * Saves downloaded POIs
     *
     * @param result
     */
    private void savePOIs(String result) {
        // cancella i POIs nel DB
        GPSDatabase db = new GPSDatabase(getActivity());
        db.deleteTableALLPOI();

        //Save POIs in DB
        Crypter crypter = new Crypter();
        JSONArray array;
        JSONObject poi;
        String title;
        int type;
        double latitude, longitude;
        try {
            array = new JSONArray(crypter.decrypt(new JSONObject(result).getString("pois")));
            for (int i = 0; i < array.length(); i++) {
                poi = array.getJSONObject(i);
                title = poi.getString("title");
                type = POI.stringToIntType(poi.getString("type"));
                latitude = poi.getDouble("lat");
                longitude = poi.getDouble("lon");
                db.insertRowAllPOI(latitude, longitude, title, type);
            }
        } catch (JSONException e) {
        }
        db.close();
        Log.v(TAG, "savePOIs()");
        displayAllPOIs();
    }

    /**
     * Displays all the POIs on the map
     */
    private void displayAllPOIs() {
        GPSDatabase db = new GPSDatabase(getActivity());
        JSONArray array = db.getAllPOITableInJSON();
        JSONObject poi;
        String title, category;
        double lat, lon;
        try {
            for (int i = 0; i < array.length(); i++) {
                poi = array.getJSONObject(i);
                lat = poi.getDouble("latitude");
                lon = poi.getDouble("longitude");
                title = poi.getString("title");
                category = POI.intToStringTypeLocalized(getActivity(), poi.getInt("category"));

                Marker marker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon))
                        .title(title).snippet(category).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

                switch (poi.getInt("category")) {
                    case 0:
                        viewpoint.add(marker);
                        break;
                    case 1:
                        gasStation.add(marker);
                        break;
                    case 2:
                        restaurant.add(marker);
                        break;
                    case 3:
                        other.add(marker);
                        break;
                }
            }
        } catch (JSONException e) {
        }
        Log.v(TAG, "displayAllPOIs()");
    }

    /**
     * Called when a button is clicked
     *
     * @param view
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_button:
                startButtonPressed(view);
                break;
            case R.id.pause_button:
                pauseButtonPressed(view);
                break;
            case R.id.resume_button:
                resumeButtonPressed(view);
                break;
            case R.id.stop_button:
                stopButtonPressed(view);
                break;
            case R.id.places_nearby_button:
                searchPlacesNearby();
                break;
            case R.id.new_poi_button:
                if (mCurrentLocation == null)
                    mCurrentLocation = gpsService.getLocation();
                if (mCurrentLocation != null)
                    poiCreation();
                else
                    Toast.makeText(getActivity(), getResources().getString(R.string.no_recorded_positions_message), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /**
     * This method is invoked when the "Start" button is pressed.
     * It changes the button in the lower part of the screen
     * and makes the recording of the route start.
     *
     * @param view the view
     */
    private void startButtonPressed(View view) {
        if (view.getId() == R.id.start_button) {
            /*if (!mGoogleApiClient.isConnected()){mGoogleApiClient.connect(); }*/
            view.setVisibility(View.GONE);
            pause = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.pause_button, buttonLayout, false);
            stop = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(pause);
            buttonLayout.addView(stop);

            RelativeLayout cardLayout = (RelativeLayout) getActivity().findViewById(R.id.current_length_duration_card_layout);
            currentLengthDurationCard = (CardView) getActivity().getLayoutInflater().inflate(R.layout.current_length_duration_card, cardLayout, false);
            cardLayout.addView(currentLengthDurationCard);
            Animation animation = AnimationUtils.makeInChildBottomAnimation(getActivity());
            animation.setDuration(300);
            currentLengthDurationCard.startAnimation(animation);

            TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, currentLengthDurationCard.getHeight(), 0);
            translateAnimation.setDuration(300);
            pause.startAnimation(translateAnimation);
            stop.startAnimation(translateAnimation);
            getActivity().findViewById(R.id.new_poi_button).setAnimation(translateAnimation);
            getActivity().findViewById(R.id.places_nearby_button).setAnimation(translateAnimation);

            pause.setOnClickListener(this);
            stop.setOnClickListener(this);
            state = State.RUNNING;
            gpsService.register();
            mCurrentLocation = gpsService.getLocation();
        }
    }

    /**
     * This method is invoked when the "Pause" button is pressed.
     * The layout of the lower part of the screen changes and the route recording stops.
     *
     * @param view the view
     */
    private void pauseButtonPressed(View view) {
        if (view.getId() == R.id.pause_button) {
            pause.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            resume = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.resume_button, buttonLayout, false);
            stop = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(resume);
            buttonLayout.addView(stop);
            resume.setOnClickListener(this);
            stop.setOnClickListener(this);
            state = State.PAUSED;
            gpsService.stopRegistering();
        }
    }

    /**
     * This method is invoked when the "Resume" button is pressed.
     * The layout of the lower part of the screen changes and the route recording starts again.
     *
     * @param view the view
     */
    private void resumeButtonPressed(View view) {
        if (view.getId() == R.id.resume_button) {
            resume.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            pause = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.pause_button, buttonLayout, false);
            stop = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.stop_button, buttonLayout, false);
            buttonLayout.addView(pause);
            buttonLayout.addView(stop);
            pause.setOnClickListener(this);
            stop.setOnClickListener(this);
            state = State.RUNNING;
            gpsService.register();
            mCurrentLocation = gpsService.getLocation();
        }
    }

    /**
     * This method is invoked when the "Stop" button is pressed.
     * The route recording is definitevely stopped and the SummaryActivity starts.
     *
     * @param view the view
     */
    private void stopButtonPressed(View view) {
        if (view.getId() == R.id.stop_button) {
            final View.OnClickListener onClickListener = this;
            TextView titleView = ((TextView) getActivity().getLayoutInflater().inflate(R.layout.list_dialog_title, null, false));
            titleView.setText(getResources().getString(R.string.stop));
            if (registered) {
                //alert dialog to stop the registration
                new AlertDialog.Builder(getActivity())
                        .setCustomTitle(titleView)
                        .setMessage(getResources().getString(R.string.stop_registration))
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // stop the registration
                                stopRegistration(onClickListener);
                                Intent intent = new Intent(getActivity(), SummaryActivity.class);
                                startActivityForResult(intent, SUMMARY_REQUEST);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            } else {
                // dialog to stop the registration if there are no recorded positions
                new AlertDialog.Builder(getActivity())
                        .setCustomTitle(titleView)
                        .setMessage(getResources().getString(R.string.stop_registration_no_positions))
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // stop the registration
                                stopRegistration(onClickListener);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        }
    }

    /**
     * Stops the registration of the route
     *
     * @param onClickListener
     */
    private void stopRegistration(View.OnClickListener onClickListener) {
        if (resume != null) resume.setVisibility(View.GONE);
        if (pause != null) pause.setVisibility(View.GONE);
        stop.setVisibility(View.GONE);
        start = (ImageButton) getActivity().getLayoutInflater().inflate(R.layout.start_button, buttonLayout, false);
        buttonLayout.addView(start);
        start.setOnClickListener(onClickListener);

        //CardView card = (CardView) getActivity().findViewById(R.id.current_length_duration_card);
        /*Animation animation = AnimationUtils.makeInChildBottomAnimation(getActivity());
        animation.setDuration(100);
        card.startAnimation(animation);*/

        TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, -currentLengthDurationCard.getHeight(), 0);
        translateAnimation.setDuration(300);
        //card.startAnimation(translateAnimation);
        start.startAnimation(translateAnimation);
        getActivity().findViewById(R.id.new_poi_button).setAnimation(translateAnimation);
        getActivity().findViewById(R.id.places_nearby_button).setAnimation(translateAnimation);

        currentLengthDurationCard.setVisibility(View.GONE);


        /*if (state == State.RUNNING) {
            state = State.STOPPED;
            gpsService.stopRegistering();
        } else {
            state = State.STOPPED;
        }*/

        state = State.STOPPED;
        gpsService.stopRegistering();
    }

    /**
     * Search places nearby using Google Places API
     */
    private void searchPlacesNearby() {
        // Construct an intent for the place picker
        try {
            PlacePicker.IntentBuilder intentBuilder = new PlacePicker.IntentBuilder();
            Intent intent = intentBuilder.build(getActivity());
            // Start the intent by requesting a result,
            // identified by a request code.
            startActivityForResult(intent, REQUEST_PLACE_PICKER);

        } catch (GooglePlayServicesRepairableException e) {
            // ...
        } catch (GooglePlayServicesNotAvailableException e) {
            // ...
        }
    }

    /**
     * Starts POICreationActivity to create a new POI
     */
    private void poiCreation() {
        Intent intent = new Intent(getActivity(), POICreationActivity.class);
        intent.putExtra(POI_LATITUDE, mCurrentLocation.getLatitude());
        intent.putExtra(POI_LONGITUDE, mCurrentLocation.getLongitude());
        intent.putExtra(POI_RECORDING, (state == State.RUNNING) && registered);

        startActivityForResult(intent, POI_CREATION_REQUEST);

        Log.v(TAG, "poiCreation(), recording: " + ((state == State.RUNNING) && registered));
        /*
        Toast.makeText(getActivity(),
                "recording: " + ((state == State.RUNNING) && registered) + ", registered: " + registered + ", state == State.RUNNING: " + (state == State.RUNNING),
                Toast.LENGTH_LONG).show();
        */
    }

    /**
     * This method shows an alert inviting the user to activate the GPS in the settings menu.
     */
    public void showSettingsAlert() {
        TextView titleView = ((TextView) getActivity().getLayoutInflater().inflate(R.layout.list_dialog_title, null, false));
        titleView.setText(getResources().getString(R.string.gps_settings_dialog_title));
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());

        // Setting Dialog Title
        alertDialog.setCustomTitle(titleView);

        // Setting Dialog Message
        alertDialog.setMessage(getResources().getString(R.string.gps_settings_dialog_message));

        // On pressing Settings button
        alertDialog.setPositiveButton(getResources().getString(R.string.settings), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton(getResources().getString(R.string.gps_settings_dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    /**
     * This method is called by the service whenever a new location is updated.
     * It makes the camera update and, if registering, the last location known
     * to be added to the route to be added on the map.
     *
     * @param location The last location updated.
     */
    public void onNewLocation(Location location, float length, long duration) {
        if (location != null) {
            Log.v(TAG, "Location accuracy: " + String.valueOf(location.getAccuracy()));
            //    Toast.makeText(getActivity(), "Location accuracy: " + String.valueOf(location.getAccuracy()), Toast.LENGTH_SHORT).show();
        }
        updateCamera(location);
        updateUIRoute(location, length, duration);
        mCurrentLocation = location;
    }

    /**
     * Set the variable "registered" to true
     */
    public void setRegistered() {
        registered = true;
    }

    /**
     * This method is called whenever the app comes back to MapsActivity from another activity started for result
     *
     * @param requestCode boh
     * @param resultCode  dunno
     * @param data        nada
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SUMMARY_REQUEST) {
            resetState();
            Log.v(TAG, "onActivityResult()");
        } else if (requestCode == REQUEST_PLACE_PICKER && resultCode == Activity.RESULT_OK) {
            // The user has selected a place. Extract the name and address.
            displayPickedPlace(data);
        } else if (requestCode == POI_CREATION_REQUEST && resultCode == Activity.RESULT_OK) {
            displayCreatedPOI(data);
        }
    }

    /**
     * Reset the recording state
     */
    private void resetState() {
        points = new ArrayList<>();
        route.setPoints(points);

        GPSDatabase db = new GPSDatabase(getActivity());
        db.deleteTableLoc();
        db.deleteTablePOI();
        mCurrentLocation = null;
        registered = false;
        gpsService.setDistanceToZero();
        db.close();

        ((TextView) getActivity().findViewById(R.id.current_length)).setText(getResources().getString(R.string.initial_length));
        ((TextView) getActivity().findViewById(R.id.current_duration)).setText(getResources().getString(R.string.initial_duration));

        back = true;
    }

    /**
     * Displays picked place taken from Google Places API
     *
     * @param data
     */
    private void displayPickedPlace(Intent data) {
        final Place place = PlacePicker.getPlace(data, getActivity());

        final CharSequence name = place.getName();
        final CharSequence address = place.getAddress();
        String attributions = PlacePicker.getAttributions(data);
        if (attributions == null) {
            attributions = "";
        }

        String title = name.toString();
        String snippet = address + "\n" + Html.fromHtml(attributions);

        mMap.addMarker(new MarkerOptions().position(place.getLatLng())
                .title(title).snippet(snippet));
    }

    /**
     * Displays the created POI
     *
     * @param data
     */
    private void displayCreatedPOI(Intent data) {
        Bundle b = data.getExtras();
        String title = b.getString(POICreationActivity.POI_TITLE);
        Double latitude = b.getDouble(POICreationActivity.POI_LATITUDE);
        Double longitude = b.getDouble(POICreationActivity.POI_LONGITUDE);
        String[] types = getResources().getStringArray(R.array.poi_categories);
        String category = types[b.getInt(POICreationActivity.POI_CATEGORY)];

        if (b.getBoolean(POICreationActivity.POI_IS_ASSOCIATED))
            mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude))
                    .title(title).snippet(category).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        else
            mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude))
                    .title(title).snippet(category).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

}


/**
 * This interface makes it possible to communicate between GPSService and MapsActivity, giving
 * a reference to a MapsActivity object, which implements NewLocationListener [see GPSService]
 */
interface NewLocationListener {
    public void onNewLocation(Location location, float length, long duration);

    public void updateCamera(Location location);

    public void setRegistered();
}
