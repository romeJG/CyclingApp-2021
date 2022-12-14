package com.cometchat.pro.androiduikit.ui.gpstracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.cometchat.pro.androiduikit.CheckoutActivityJava;
import com.cometchat.pro.androiduikit.MainActivityTrackingNavigation;
import com.cometchat.pro.androiduikit.Views.DashboardActivity;
import com.cometchat.pro.androiduikit.Views.Login1;
import com.cometchat.pro.androiduikit.Views.SessionManager;
import com.cometchat.pro.androiduikit.model.subscribe.Subscribe;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.cometchat.pro.androiduikit.LogItem;
import com.cometchat.pro.androiduikit.R;
import com.cometchat.pro.androiduikit.RunningLog;
import com.cometchat.pro.androiduikit.RunningLogDB;
import com.cometchat.pro.androiduikit.RunningLogDao;
import com.cometchat.pro.androiduikit.directionhelpers.FetchURL;
import com.cometchat.pro.androiduikit.directionhelpers.TaskLoadedCallback;

import static android.content.Context.LOCATION_SERVICE;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static java.lang.Integer.getInteger;
import static java.lang.Integer.parseInt;

/**
 * GPS Tracker fragment will start when GPS tracker navigation is clicked
 */
public class GpsTrackerFragment extends Fragment implements OnMapReadyCallback, LocationListener, View.OnClickListener, TaskLoadedCallback {
    //Set variables
    String access_code, name;
    TextView accessCode1;
    private SessionManager sessionManager;
    public static GoogleMap mMap;
    private LocationManager locationManager;
    private MarkerOptions mo;
    private Marker marker;
    private Location loc1, loc2;
    private LatLng myCoordinates, address_latLng;
    public static Polyline currentPolyline;

    private boolean previousLocation, reset_check, pause_check;
    private float distanceInMeters, paused_distance;

    TextView distance_view;
    EditText search_location;
    Button start, reset, save, home, subs;
    ImageView getDirection, search;

    private Chronometer chronometer;
    private boolean running;
    private long pauseOffset;
    private String runTitle = "";

    private ArrayList<LogItem> RunningLogArray;

    RunningLogDB db;
    RunningLogDao runningLogDao;

    final static int PERMISSION_ALL = 1;
    final static String[] PERMISSIONS = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    private static Context context = null;

    private int limit = 99999;

    /**
     * Create GPS tracker view when the fragment start
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return rendered view
     */
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gpstracker, null, false);
        context = getActivity();

        sessionManager = new SessionManager(getActivity()); //TINITIGNAN KUNG MAY SESSION
        if(!sessionManager.isLoggedIn()){
            moveToLogin();  ; // REKTA SA LOGIN XML KUNG WALANG SESSION
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) this.getChildFragmentManager()
                .findFragmentById(R.id.gpsmap);
        mapFragment.getMapAsync(this);

        // Set location manager and create marker options
        locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);
        mo = new MarkerOptions().position(new LatLng(0, 0)).title("My Current Location");

        // Check SDK version and request permission to get location if not granted
        if (Build.VERSION.SDK_INT >= 23 && !isPermissionGranted()) {
            requestPermissions(PERMISSIONS, PERMISSION_ALL);
        } else {
            requestLocation();
        }

        // Show alert if location is not enabled
        if (!isLocationEnabled()) {
            showAlert(1);
        }

        // Declare all views and buttons variables
        start = (Button) root.findViewById(R.id.start_btn);
        reset = (Button) root.findViewById(R.id.reset_btn);
        save = (Button) root.findViewById(R.id.save_btn);
        home = (Button) root.findViewById(R.id.btnMainFeature);
        subs = (Button) root.findViewById(R.id.btnSubmitSubs);



        search = (ImageView) root.findViewById(R.id.search_btn);
        getDirection = (ImageView) root.findViewById(R.id.direction_btn);

        distance_view = (TextView) root.findViewById(R.id.distance_view);
        search_location = (EditText) root.findViewById(R.id.search_location);

        chronometer = (Chronometer) root.findViewById(R.id.chronometer);

        // Set onClickListener for all views and buttons
        getDirection.setOnClickListener(this);
        start.setOnClickListener(this);
        reset.setOnClickListener(this);
        save.setOnClickListener(this);
        search.setOnClickListener(this);
        home.setOnClickListener(this);
        subs.setOnClickListener(this);


        // Set initial values
        loc1 = new Location("");
        loc2 = new Location("");
        reset_check = true;

        // Setup database access layer and read item from database
        db = RunningLogDB.getDatabase(getActivity().getApplicationContext());
        runningLogDao = db.RunningLogDao();
        readItemsFromDatabase();



        return root;
    }

    /**
     * Get URL for calling Google Map Direction API
     * @param origin initial location latitude and longitude
     * @param dest destination latitude and longitude
     * @param directionMode walking/driving/bicycling
     * @return url string
     */
    private String getUrl(LatLng origin, LatLng dest, String directionMode) {
        //Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        //Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        //Mode
        String mode = "mode=" + directionMode;
        //Build parameter to the web service
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        //Output format
        String output = "json";

        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);

        return url;
    }

    /**
     * Onclick methods for all buttons
     * @param view
     */
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            // When start/pause button is clicked
            case R.id.start_btn:
                // Check if the chronometer is not running (when click "Start")
                if (!running) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Do you want to limit your ride's distance?");

                    // Set up the input
                    final EditText input = new EditText(getContext());
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);

                    // Set up the button for SAVE
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            limit = parseInt(input.getText().toString());
                            Log.d("TAGgggg", "onClickll: ".concat(input.getText().toString()));
                        }
                    }).setNegativeButton("No", new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    });
                    builder.show();
                    // Change button text to 'pause' and start chronometer
                    start.setText("Pause");
                    chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
                    chronometer.start();
                    running = true;

                    // If on reset is true, reset all values
                    if (reset_check) {
                        distanceInMeters = 0;
                        distance_view.setText("0 km");
                        distance_view.setVisibility(View.VISIBLE);
                        reset_check = false;
                    }

                    // reset pause value
                    paused_distance = 0;
                    pause_check = false;
                } else { // When chronometer is running (when click "Pause")
                    // Change button text to 'start' and stop chronometer
                    start.setText("Start");
                    chronometer.stop();
                    pauseOffset = SystemClock.elapsedRealtime() - chronometer.getBase();
                    running = false;

                    // set values on pause
                    reset_check = false;
                    paused_distance = distanceInMeters;
                    pause_check = true;
                }

                break;

            // When reset button is clicked
            case R.id.reset_btn:
                // Reset all the values
                chronometer.setBase(SystemClock.elapsedRealtime());
                pauseOffset = 0;

                distanceInMeters = 0;
                distance_view.setText("0 km");
                paused_distance = 0;
                pause_check = true;
                reset_check = true;
                break;

            case R.id.btnMainFeature:
                // Reset all the values
                access_code = sessionManager.getUserDetail().get(SessionManager.ACCESS); //0
                int number = parseInt(access_code);
                // Reset all the values

                if(number == 1){
                    Intent intent1 = new Intent(getActivity(), DashboardActivity.class);
                    startActivity(intent1);
                    Toast toast2 = Toast.makeText(getActivity(), "Welcome to Dashboard", Toast.LENGTH_LONG);
                    toast2.setGravity(Gravity.CENTER, 0, 0);
                    toast2.show();
                }else{
                    Toast.makeText(getActivity(),"Account isn't subscribed yet!",Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.btnSubmitSubs:
                access_code = sessionManager.getUserDetail().get(SessionManager.ACCESS); //0
                int number1 = parseInt(access_code);
                if(number1 == 1){
                    Toast.makeText(getActivity(),"You already subscribed!",Toast.LENGTH_LONG).show();
                }else{
                    startActivity(new Intent(getActivity(), CheckoutActivityJava.class));
                }



            // When search button is clicked
            case R.id.search_btn:
                String loc = search_location.getText().toString();
                List<Address> addressList = new ArrayList<>();

                // If search input is not null
                if (loc != null || loc.equals("")) {
                    Geocoder geocoder = new Geocoder(getContext());
                    try {
                        Log.d("Run", "YESSSSS");
                        // Get address list from the location name using Geocoder
                        addressList = geocoder.getFromLocationName(loc, 1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else { // If search input is null, display toast
                    Toast toast = Toast.makeText(getActivity(), "Please fill in the search field", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    break;
                }

                hideKeyboard();

                // Get top address of the list and set marker and camera on the position
                if (!loc.equals("") && !addressList.equals(null)){
                    try {
                        Address address = addressList.get(0);
                        address_latLng = new LatLng(address.getLatitude(), address.getLongitude());
                        marker.setPosition(address_latLng);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(address_latLng, 15));
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                break;

            // When get direction button is clicked
            case R.id.direction_btn:
                // If address result from search bar is not null
                if (address_latLng != null && myCoordinates != null) {
                    // Set marker option of current location, and destination location
                    MarkerOptions place1 = new MarkerOptions().position(myCoordinates);
                    MarkerOptions place2 = new MarkerOptions().position(address_latLng);

                    // Get URL for calling direction API between two locations
                    String url = getUrl(place1.getPosition(), place2.getPosition(), "walking");
                    // Call direction helper FetchURL and display the route
                    new FetchURL(getContext()).execute(url, "walking");
                } else {
                    Toast.makeText(getActivity(), "Something went wrong!", Toast.LENGTH_LONG).show();
                }

                break;

            // When save button is clicked
            case R.id.save_btn:
                // Set up dialog asking for title of the run
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Title of your Run");

                // Set up the input
                final EditText input = new EditText(getContext());
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                // Set up the button for SAVE
                builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                    /**
                     * When user click SAVE, save run info to log
                     * @param dialog
                     * @param which
                     */
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Set title and time
                        runTitle = input.getText().toString();
                        String time = (String) chronometer.getText();

                        // Create running log item from distance, time, and title, and add to list
                        LogItem log = new LogItem(Double.valueOf(distanceInMeters), time, runTitle);
                        RunningLogArray.add(0, log);

                        // Save list to database and display toast
                        saveItemsToDatabase();
                        Toast toast = Toast.makeText(getActivity(), "The record has been saved", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }
                });
                // Set up the button for CANCEL
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    /**
                     * When user click CANCEL, close dialog
                     * @param dialog
                     * @param which
                     */
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
                break;

            default:
                break;
        }
    }

    /**
     * This callback is triggered when the map is ready to be used.
     * @param googleMap
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        marker = mMap.addMarker(mo);

        // Display current location, set marker to my house's location and zoom
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mMap.setMyLocationEnabled(true);
        marker.setPosition(new LatLng(-33.8897, 151.19));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
    }

    /**
     * This callback is triggered when location is changed
     * Display accumulating distance from initial location to current location
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            if (!previousLocation) { // If there is no previous location
                // Set current location as loc1
                loc1.setLatitude(location.getLatitude());
                loc1.setLongitude(location.getLongitude());

                distanceInMeters = 0;
                previousLocation = true;
            } else if (pause_check) { // When chronometer is on pause, distance displayed remain the same

                @SuppressLint("DefaultLocale") String pausedDistanceFormat = String.format("%.2f", paused_distance);
                distance_view.setText(pausedDistanceFormat.concat(" km"));
            } else { // If previous location exists
                // Set loc1 to previous location
                loc1.setLatitude(loc2.getLatitude());
                loc1.setLongitude(loc2.getLongitude());

                // Set loc2 to current location
                loc2.setLatitude(location.getLatitude());
                loc2.setLongitude(location.getLongitude());

                // Get distance from loc1 and loc2 and display
                distanceInMeters = distanceInMeters + (loc1.distanceTo(loc2) / 1000);
                //format to 2 decimal places
                @SuppressLint("DefaultLocale") String distanceFormat = String.format("%.2f", distanceInMeters);
                distance_view.setText(distanceFormat.concat(" km"));
            }
        }

        // Set current location and move camera along
        myCoordinates = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(myCoordinates));
        if (distanceInMeters >= limit){
            pause_check = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("want to keep cycling? because your limit is done!");

            // Set up the input
            final EditText input = new EditText(getContext());

            // Set up the button for SAVE
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    limit = 999999;
                    pause_check =false;
                }
            }).setNegativeButton("No Save my Run", new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int id) {
                    // Set up dialog asking for title of the run
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Title of your Run");

                    // Set up the input
                    final EditText input = new EditText(getContext());
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    builder.setView(input);

                    // Set up the button for SAVE
                    builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                        /**
                         * When user click SAVE, save run info to log
                         * @param dialog
                         * @param which
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Set title and time
                            runTitle = input.getText().toString();
                            String time = (String) chronometer.getText();

                            // Create running log item from distance, time, and title, and add to list
                            LogItem log = new LogItem(Double.valueOf(distanceInMeters), time, runTitle);
                            RunningLogArray.add(0, log);

                            // Save list to database and display toast
                            saveItemsToDatabase();
                            Toast toast = Toast.makeText(getActivity(), "The record has been saved", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        }
                    });
                    // Set up the button for CANCEL
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        /**
                         * When user click CANCEL, close dialog
                         * @param dialog
                         * @param which
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
            });
            builder.show();
        }
    }

    /**
     * onStatusChanged method from LocationListener
     * @param s
     * @param i
     * @param bundle
     */
    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    /**
     * onProviderEnabled method from LocationListener
     * @param s
     */
    @Override
    public void onProviderEnabled(String s) {
    }

    /**
     * onProviderDisabled method from LocationListener
     * @param s
     */
    @Override
    public void onProviderDisabled(String s) {
    }

    /**
     * Request for location update
     */
    private void requestLocation() {
        // Get location data according to this criteria of accuracy and power
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_HIGH);

        // Get provider with set criteria and request for location update
        String provider = locationManager.getBestProvider(criteria, true);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(provider, 5000, 10, this);
    }

    /**
     * Check if location provider (GPS or Network) is enabled
     * @return boolean
     */
    private boolean isLocationEnabled(){
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Check if permission to access location has been granted
     * @return boolean
     */
    private boolean isPermissionGranted(){
        if(checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            return true;
        } else{
            return false;
        }
    }

    /**
     * Show dialog alert for user to change location setting
     * @param status
     */
    private void showAlert(final int status){
        String message, title, btnText;
        if(status == PERMISSION_ALL){
            message = "Your Locations Settings is set to 'Off'.\nPlease enable location to use this app";
            title = "Enable Location";
            btnText = "Location Settings";
        } else{
            message = "Please allow this app to access location";
            title = "Permission access";
            btnText = "Grant";
        }

        // Setup alert dialog
        final AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        dialog.setCancelable(false);
        dialog.setTitle(title)
                .setMessage(message)
                .setPositiveButton(btnText, new DialogInterface.OnClickListener() {
                    /**
                     * When user agree to grant permission
                     * @param dialog
                     * @param id
                     */
                    public void onClick(DialogInterface dialog, int id) {
                        // Start new activity to location setting
                        if(status == PERMISSION_ALL){
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        } else{ // Request permission
                            requestPermissions(PERMISSIONS, PERMISSION_ALL);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    /**
                     * When click cancel, close dialog
                     * @param dialog
                     * @param id
                     */
                    public void onClick(DialogInterface dialog, int id){
                        getActivity().finish();
                    }
                });
        dialog.show();
    }


    /**
     * Method from TaskLoadedCallback interface
     * Add polyline of route to map
     * @param values
     */
    @Override
    public void onTaskDone(Object... values) {
        if(currentPolyline != null){
            currentPolyline.remove();
            currentPolyline = mMap.addPolyline((PolylineOptions)values[0]);
        } else{
            currentPolyline = mMap.addPolyline((PolylineOptions)values[0]);
        }
    }

    /**
     * Hide keyboard
     */
    public void hideKeyboard() {
        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
    }

    private void moveToLogin() {
        sessionManager.logoutSession();
        Intent intent = new Intent(getActivity(), Login1.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);

    }

    /**
     * Read item from database
     */
    private void readItemsFromDatabase(){
        try {
            new AsyncTask<Void, Void, Void>() {
                /**
                 * Get running log items from database and add to RunningLogArray list
                 * @param voids
                 * @return void
                 */
                @Override
                protected Void doInBackground(Void... voids) {
                    List<RunningLog> itemsFromDB = runningLogDao.listAll();
                    RunningLogArray = new ArrayList<LogItem>();
                    if (itemsFromDB != null & itemsFromDB.size() > 0) {
                        for (RunningLog item : itemsFromDB) {
                            RunningLogArray.add(item.getRunningLogItem());
                        }
                    }
                    return null;
                }
            }.execute().get();
        }
        catch(Exception ex) {
            Log.e("readItemsFromDatabase", ex.getStackTrace().toString());
        }
    }

    /**
     * Save items to database
     */
    public void saveItemsToDatabase(){
        new AsyncTask<Void, Void, Void>() {
            /**
             * Delete all items and
             * Insert all items from RunningLogArray list to database
             * @param voids
             * @return
             */
            @Override
            protected Void doInBackground(Void... voids) {
                runningLogDao.deleteAll();

                for (LogItem l : RunningLogArray) {
                    RunningLog item = new RunningLog(l.getDistance(),l.getTime(),l.getPace(),l.getDate(),l.getSpeed(), l.getTitle());
                    runningLogDao.insert(item);
                }
                return null;
            }
        }.execute();
    }
}