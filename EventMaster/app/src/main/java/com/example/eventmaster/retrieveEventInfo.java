package com.example.eventmaster;


import android.app.AlertDialog;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnSuccessListener;


/**
 * Initializes the Event Information Screen
 * Allows entrant to join waitlist notifying firebase
 * Notifies Event's 'waitlist list' of the user that joined specific event
 */

public class retrieveEventInfo extends AppCompatActivity {
    /**
     * Initializes the Event Information Screen
     *
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     * @param hashedData the hash data of the event that is needed in order to access proper data
     * @param deviceID the facilities device ID
     * @param event the event // dont think I need this
     * Displays the event information of the chosen event
     */
    private FirebaseFirestore db;
    TextView eventName;
    TextView eventDescription;
    TextView eventFinalDate;
    ImageView eventPoster;
    AppCompatButton joinWaitlistButton;
    private ImageButton backButton;
    ActivityResultLauncher<Intent> joinWaitlistResultLauncher;
    private Profile user;
    private String name;
    private String email;
    private String phone_number;
    private Long waitlistCapacity;

    private boolean geolocation;
    private double latitude;
    private double longitude;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private LocationCallback locationCallback;

    private ActivityResultLauncher<Intent> ProfileActivityResultLauncher;
    private ActivityResultLauncher<Intent> notificationActivityResultLauncher;
    private ActivityResultLauncher<Intent> settingsResultLauncher;
    private ActivityResultLauncher<Intent> MainActivityResultLauncher;


    /**
     * Creates the event information screen where the user can view event details, name and poster
     * There is also a button that allows them to join the waiting list for the event, this will update the firestore database
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ModeActivity.applyTheme(this);
        setContentView(R.layout.event_details_screen);
        eventName = findViewById(R.id.event_name);
        eventDescription = findViewById(R.id.event_decription);
        eventFinalDate = findViewById(R.id.event_decription);
        eventPoster = findViewById(R.id.event_poster);
        joinWaitlistButton = findViewById(R.id.join_waitlist_button);
        backButton = findViewById(R.id.back);

        db = FirebaseFirestore.getInstance();


        Intent intentMain = getIntent();
        user = (Profile) intentMain.getSerializableExtra("User");

        // will need to access user device id but just hardcoded for now
        String userDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        //retrieves the data for the profile
        db.collection("profiles").document(userDeviceId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            name = documentSnapshot.getString("name");
                            email = documentSnapshot.getString("email");
                            phone_number = documentSnapshot.getString("phone number");
                        } else {
                            Log.d("Firestore", "Document does not exist");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("Firestore", "Error getting profile information", e);
                    }
                });


        // retrieve information after scanning
        Intent intent = getIntent();
        String hashedData = intent.getStringExtra("hashed_data");
        String deviceID = intent.getStringExtra("deviceID");  // facility id
        String event = intent.getStringExtra("event");
        String posterUrl = intent.getStringExtra("posterUrl");

        Intent intent2 = new Intent(retrieveEventInfo.this, JoinWaitlistScreen.class);
        intent2.putExtra("hashed_data", hashedData);
        intent2.putExtra("deviceID", deviceID);
        intent2.putExtra("event", event);
        intent2.putExtra("posterUrl", posterUrl);

        // Ensure the received data is not null
        if (hashedData != null && deviceID != null) {
            retrieveEventInfo(hashedData, deviceID, event);

        } else {
            //Toast.makeText(this, "Failed to retrieve event data.", Toast.LENGTH_SHORT).show();
        }

        ActivityCompat.requestPermissions(this, new String[]{ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION},
                PackageManager.PERMISSION_GRANTED);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // links the description screen to the join waitlist screen
        joinWaitlistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAndCompareDocumentCount(deviceID, userDeviceId, hashedData, deviceID, posterUrl);
                if (geolocation) {
                    buttonGetLocation();
                }
            }
        });

        LocationRequest locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 100)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(100)
                .build();
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                // Handle live location updates if necessary
            }
        };

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationServices.getFusedLocationProviderClient(getApplicationContext())
                .requestLocationUpdates(locationRequest, locationCallback, null);




        // Set result launchers to set up navigation buttons on the bottom of the screen
        settingsResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Profile updatedUser = (Profile) result.getData().getSerializableExtra("User");
                        if (updatedUser != null) {
                            user = updatedUser; // Apply the updated Profile to MainActivity's user
                            Log.d("MainActivity", "User profile updated: " + user.getName());
                        }
                    }

                });

        MainActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Profile updatedUser = (Profile) result.getData().getSerializableExtra("User");
                        if (updatedUser != null) {
                            user = updatedUser; // Apply the updated Profile to MainActivity's user
                            Log.d("MainActivity", "User profile updated: " + user.getName());
                        }
                    }

                });

        notificationActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Profile updatedUser = (Profile) result.getData().getSerializableExtra("User");
                        if (updatedUser != null) {
                            user = updatedUser; // Apply the updated Profile to MainActivity's user
                            Log.d("MainActivity", "User profile updated: " + user.getName());
                        }
                    }

                });

        ProfileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Profile updatedUser = (Profile) result.getData().getSerializableExtra("User");
                        if (updatedUser != null) {
                            user = updatedUser; // Apply the updated Profile to MainActivity's user
                            Log.d("MainActivity", "User profile updated: " + user.getName());
                        }
                    }
                });

        backButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("User", user);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Initialize BottomNavigationView
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        // Disable tint for specific menu item
        Menu menu = bottomNavigationView.getMenu();
        MenuItem qrCodeItem = menu.findItem(R.id.nav_scan_qr);
        Drawable qrIcon = qrCodeItem.getIcon();
        qrIcon.setTintList(null);  // Disable tinting for this specific item
        // Set up navigation item selection listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Intent newIntent;

            if (item.getItemId() == R.id.nav_Home) {
                newIntent = new Intent(retrieveEventInfo.this, MainActivity.class);
                newIntent.putExtra("User", user);
                MainActivityResultLauncher.launch(newIntent);
                return true;
            } else if (item.getItemId() == R.id.nav_Settings) {
                newIntent = new Intent(retrieveEventInfo.this, SettingsScreen.class);
                newIntent.putExtra("User", user);
                settingsResultLauncher.launch(newIntent);
                return true;
            } else if (item.getItemId() == R.id.nav_Notifications) {
                newIntent = new Intent(retrieveEventInfo.this, Notifications.class);
                newIntent.putExtra("User", user);
                notificationActivityResultLauncher.launch(newIntent);
                return true;
            } else if (item.getItemId() == R.id.nav_Profile) {
                newIntent = new Intent(retrieveEventInfo.this, ProfileActivity.class);
                newIntent.putExtra("User", user);
                ProfileActivityResultLauncher.launch(newIntent);
                return true;
            } else if (item.getItemId() == R.id.nav_scan_qr) {
                // Open QRScanFragment without simulating button click
                openQRScanFragment();
                return true;
            }
            return false;
        });

    }

    private void openQRScanFragment() {
        // Open QRScanFragment without simulating button click
        Intent intent = new Intent(this, QRScanFragment.class);
        intent.putExtra("User", user);  // Pass the user information if needed
        startActivity(intent);

    }

    public void buttonGetLocation() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                } else {
                    latitude = 0;
                    longitude = 0;
                }
                //Toast.makeText(retrieveEventInfo.this, "This is the longitude and latitude" + longitude + latitude, Toast.LENGTH_SHORT).show();
            }
        });


    }

    /**
     * Retrieves the event data(name, description, poster)
     * @param hashedData the hash data of the event that is needed in order to access proper data
     * @param deviceID the device ID of the entrant that is needed to access data in the firebase
     * @param event the event name that is needed to access data in the firebase
     */
   //  check if the hash data matches any of the hash data in the firebase when the qr code is scanned
    private void retrieveEventInfo(String hashedData, String deviceID, String event) {
        db.collection("facilities")
                .document(deviceID)
                .collection("My Events")
                // todo event name
                .whereEqualTo("hash", hashedData)
                .whereEqualTo("eventName", event)  // added this
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                        if (!task.getResult().isEmpty()){  // check something is in result
                            for (DocumentSnapshot document : task.getResult()) {
                                // Retrieve data directly from the document
                               // Toast.makeText(retrieveEventInfo.this, "Data is passed!!!", Toast.LENGTH_SHORT).show();
                                String eventName = document.getString("eventName");
                                String eventDescription = document.getString("eventDescription");
                                String eventPosterUrl = document.getString("posterUrl");
                                String waitlistCountdown = document.getString("waitlistCountdown");
                                waitlistCapacity = document.getLong("waitlistCapacity");
                                geolocation = document.getBoolean("geolocationEnabled");

                                // Call the display method with the retrieved data
                                displayEventInfo(eventName, eventDescription, eventPosterUrl, waitlistCountdown);
                                joinWaitlistButton.setVisibility(View.VISIBLE);
                            }
                    } else {
                        //Toast.makeText(retrieveEventInfo.this, "Event does not exist", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    //Toast.makeText(retrieveEventInfo.this, "Error retrieving event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }





    /**
     * Displays the event name, description and poster
     * @param eventName Display the event name on screen
     * @param eventDescription Display the event description that is scrollable on screen
     * @param eventPosterUrl Display the proper event poster on screen
     */
    private void displayEventInfo(String eventName, String eventDescription, String eventPosterUrl, String waitlistCountdown ) {
        TextView eventNameTextView = findViewById(R.id.event_name);
        TextView eventDescriptionTextView = findViewById(R.id.event_decription);
        TextView countdownTextView = findViewById(R.id.event_open_time);
        TextView geolocationTextView = findViewById(R.id.geolocation);


        // Set the text for the TextViews
        eventNameTextView.setText(eventName);
        eventDescriptionTextView.setText(eventDescription);
        countdownTextView.setText("Event is Open until: " + waitlistCountdown);

        if (geolocation) {
            geolocationTextView.setText("Event requires geolocation");
        } else {
            geolocationTextView.setText("Event does not require geolocation");
        }

        if (eventPosterUrl == null){
            eventPoster.setImageResource(R.drawable.default_poster); // set default poster
        }
        else {
            // upload the image from the firebase
            try {
                Glide.with(this)
                        .load(eventPosterUrl)
                        .into(eventPoster);
            } catch (Exception e) {
                Log.e("RetrieveEventInfo", "Error loading image: " + e.getMessage());
            }
        }
    }

    /**
     * Sends data to JoinWaitlist screen
     * @param hashedData
     * @param deviceID
     * @param event
     * @param posterUrl
     */

    private void fetchEventData(String hashedData, String deviceID, String event, String posterUrl) {
        Intent intent2 = new Intent(retrieveEventInfo.this, JoinWaitlistScreen.class);
        intent2.putExtra("hashed_data", hashedData);
        intent2.putExtra("deviceID", deviceID);
        intent2.putExtra("event", event);
        intent2.putExtra("posterUrl", posterUrl);

        startActivity(intent2);

    }

    /**
     * Adds a collection to firebase of the events that the entrant waitlisted
     * @param userDeviceId entrant's user id
     * entrants document
     */

    private void joinWaitlistEntrant(String userDeviceId, String hashedData, String deviceID, String posterUrl) {
        String event = eventName.getText().toString();
        Map<String, Object> WaitlistEvents = new HashMap<>();
        WaitlistEvents.put("eventName", event);
        WaitlistEvents.put("hashed_data", hashedData);
        WaitlistEvents.put("deviceID", deviceID);
        WaitlistEvents.put("posterUrl", posterUrl);
        WaitlistEvents.put("eventDescription", eventDescription.getText().toString());
        WaitlistEvents.put("geolocationEnabled", geolocation);

        // add event to the entrants list of waitlisted events
        db.collection("entrants")
                .whereEqualTo("deviceId", userDeviceId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String entrantId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("entrants")
                                .document(entrantId)
                                .collection("Waitlisted Events")
                                .document(event)
                                .set(WaitlistEvents)
                                .addOnSuccessListener(aVoid -> {
                                    //Toast.makeText(retrieveEventInfo.this, "Successfully joined waitlist", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Firestore", "Error adding event to waitlist: " + e.getMessage());
                                    //Toast.makeText(retrieveEventInfo.this, "Error joining waitlist", Toast.LENGTH_SHORT).show();
                                });
                        db.collection("entrants")
                                .document(entrantId)
                                .collection("Unsampled Events")
                                .document(event)
                                .set(WaitlistEvents)
                                .addOnSuccessListener(bVoid -> {
                                    Log.d("Firestore", "Successfully added to unsampled list: ");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Firestore", "Error adding event to unsampled list: " + e.getMessage());
                                    //Toast.makeText(retrieveEventInfo.this, "Error joining waitlist", Toast.LENGTH_SHORT).show();
                                });

                    }else{
                        Toast.makeText(retrieveEventInfo.this, "Waitlist not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(retrieveEventInfo.this, "Error querying entrants", Toast.LENGTH_SHORT).show());
    }


    /**
     * Adds user id to a collection of users waitlisted in Firebase
     * @param userDeviceId the user joining the waitlist
     * @param deviceId the facility id correlated to the event
     */
    private void joinWaitlistOrganizer(String userDeviceId, String deviceId) {
        String event = eventName.getText().toString();

        Map<String, Object> entrantData = new HashMap<>();
        entrantData.put("entrantId", userDeviceId);
        entrantData.put("email", email);
        entrantData.put("name", name);
        entrantData.put("phone number", phone_number);
        entrantData.put("latitude", latitude);
        entrantData.put("longitude", longitude);

        db.collection("facilities")
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String facilityId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("facilities")
                                .document(facilityId)
                                .collection("My Events")
                                .document(event)
                                .collection("waitlist list")
                                .document(userDeviceId)
                                .set(entrantData)
                                .addOnSuccessListener(aVoid1 -> {
                                    //Toast.makeText(retrieveEventInfo.this, "Entrant added to waitlist successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Toast.makeText(retrieveEventInfo.this, "Error adding entrant to waitlist", Toast.LENGTH_SHORT).show());
                        db.collection("facilities")
                                .document(facilityId)
                                .collection("My Events")
                                .document(event)
                                .collection("unsampled list")
                                .document(userDeviceId)
                                .set(entrantData)
                                .addOnSuccessListener(aVoid1 -> {
                                    Toast.makeText(retrieveEventInfo.this, "Entrant added to unsampled list successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Toast.makeText(retrieveEventInfo.this, "Error adding entrant to unsampled list", Toast.LENGTH_SHORT).show());
                    } else {
                        //Toast.makeText(retrieveEventInfo.this, "Entrant not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(retrieveEventInfo.this, "Error querying entrant", Toast.LENGTH_SHORT).show());
    }


    /**
     * Checks if waitlist is full
     * If true, entrant is unable to join
     * If false, entrant will successfully join waitlist
     * @param deviceId
     * @param userDeviceId
     * @param hashedData
     * @param deviceID
     * @param posterUrl
     */
    private void getAndCompareDocumentCount(String deviceId, String userDeviceId, String hashedData, String deviceID, String posterUrl) {
        int targetCount = Math.toIntExact(waitlistCapacity);
        String event = eventName.getText().toString();

        db.collection("facilities")
                .document(deviceId)
                .collection("My Events")
                .document(event)
                .collection("waitlist list")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot documents = task.getResult();
                        int count = (documents != null) ? documents.size() : 0;

                        // Implement comparison logic
                        if (count >= targetCount) {
                            Toast.makeText(retrieveEventInfo.this, "Waitlist is full, unable to join", Toast.LENGTH_SHORT).show();
                            // Additional action when count is greater
                        } else if (count < targetCount) {
                            Intent intent = new Intent(retrieveEventInfo.this, JoinWaitlistScreen.class);
                            intent.putExtra("User", user);
                            intent.putExtra("hashed_data", hashedData);
                            intent.putExtra("deviceID", deviceID);
                            intent.putExtra("event", event);
                            fetchEventData(hashedData, deviceID, event, posterUrl);
                            joinWaitlistEntrant(userDeviceId, hashedData, deviceID, posterUrl);
                            joinWaitlistOrganizer(userDeviceId, deviceID);
                            startActivity(intent);
                        }
                    } else {
                        FirebaseFirestoreException e = (FirebaseFirestoreException) task.getException();
                        System.err.println("Error getting documents: " + e);
                    }
                });
    }




}

