package com.example.eventmaster;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
public class CreateEventActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private String deviceId;
    // UI Elements
    private EditText eventNameInput;
    private EditText eventDescriptionInput;
    private EditText eventCapacityInput;
    private EditText waitlistCapacityInput;
    private EditText waitlistCountdownInput;
    private AppCompatButton createEventButton;
    private AppCompatButton uploadPosterButton;
    private ImageButton notificationButton; // For notification icon
    private ImageButton settingsButton; // For settings icon
    private ImageButton profileButton; // For profile icon
    private ImageButton viewEventsButton; // For view events icon

    private Uri posterUri; // To hold the URI of the selected poster
    private String posterDownloadUrl = null; // To hold the download URL of the uploaded poster

    private static final String DEVICE_ID = "1fa04f4ce1623bee"; // Replace with the actual device ID
    private static final int PICK_IMAGE_REQUEST = 1; // Request code for image selection

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event); // Ensure this layout exists

        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Initialize UI elements
        eventNameInput = findViewById(R.id.eventName);
        eventDescriptionInput = findViewById(R.id.eventDescription);
        eventCapacityInput = findViewById(R.id.eventCapacity);
        waitlistCapacityInput = findViewById(R.id.waitlistCapacity);
        waitlistCountdownInput = findViewById(R.id.waitlistCountdown);
        createEventButton = findViewById(R.id.createEventButton);
        uploadPosterButton = findViewById(R.id.Upload_poster_button);
        Profile user = new Profile(deviceId,"Vansh");
        // Initialize navigation buttons
        ImageButton notificationButton = findViewById(R.id.notifications);
        ImageButton settingsButton = findViewById(R.id.settings);
        ImageButton profileButton = findViewById(R.id.profile);
        ImageButton viewEventsButton = findViewById(R.id.view_events);
        ImageButton backButton = findViewById(R.id.back_button); // Initialize back button


        // Set click listeners for navigation
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(CreateEventActivity.this, Notifications.class);
            startActivity(intent);
        });

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(CreateEventActivity.this, SettingsScreen.class);
            startActivity(intent);
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(CreateEventActivity.this, ProfileActivity.class);
            intent.putExtra("User", user);
            startActivity(intent);
        });

        viewEventsButton.setOnClickListener(v -> {
            Intent intent = new Intent(CreateEventActivity.this, ViewCreatedEventsActivity.class);
            startActivity(intent);
        });
        // Set click listener for the back button
        backButton.setOnClickListener(v -> {
            finish(); // Close the current activity and return to the previous one
        });

        // Set up the upload poster button
        uploadPosterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser(); // Open the image chooser when the button is clicked
            }
        });

        // Set up the create event button
        createEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEvent(); // Handle event creation
            }
        });
    }

    // Open the file chooser to select an image for the poster
    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // Handle the result of image selection
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            posterUri = data.getData(); // Store the URI of the selected poster
            Toast.makeText(this, "Poster selected", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to create an event and send it to Firebase Firestore and Firebase Storage
    private void createEvent() {
        String eventName = eventNameInput.getText().toString();
        String eventDescription = eventDescriptionInput.getText().toString();
        String eventCapacity = eventCapacityInput.getText().toString();
        String waitlistCapacity = waitlistCapacityInput.getText().toString();
        String waitlistCountdown = waitlistCountdownInput.getText().toString();

        // Input validation
        if (eventName.isEmpty() || eventDescription.isEmpty() || eventCapacity.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // First, upload the poster (if one is selected)
        if (posterUri != null) {
            StorageReference posterRef = storageRef.child("event_posters/" + eventName + "_poster.jpg");

            posterRef.putFile(posterUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        posterRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            posterDownloadUrl = uri.toString(); // Save the download URL
                            // Once the poster is uploaded, create the event in Firestore
                            saveEventToFirestore(eventName, eventDescription, eventCapacity, waitlistCapacity, waitlistCountdown);
                        });
                    })
                    .addOnFailureListener(e -> Toast.makeText(CreateEventActivity.this, "Poster upload failed", Toast.LENGTH_SHORT).show());
        } else {
            // No poster selected, just save the event
            saveEventToFirestore(eventName, eventDescription, eventCapacity, waitlistCapacity, waitlistCountdown);
        }
    }

    // Method to save event details to Firestore
    private void saveEventToFirestore(String eventName, String eventDescription, String eventCapacity, String waitlistCapacity, String waitlistCountdown) {
        // Create a map for event data
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventName", eventName);
        eventData.put("eventDescription", eventDescription);
        eventData.put("eventCapacity", Integer.parseInt(eventCapacity));
        eventData.put("waitlistCapacity", waitlistCapacity.isEmpty() ? 0 : Integer.parseInt(waitlistCapacity));
        eventData.put("waitlistCountdown", waitlistCountdown.isEmpty() ? 0 : Integer.parseInt(waitlistCountdown));

        // Include the poster URL if it exists
        if (posterDownloadUrl != null) {
            eventData.put("posterUrl", posterDownloadUrl);
        }

        // Query the "facilities" collection for the document with the matching deviceId
        db.collection("facilities")
                .whereEqualTo("deviceId", DEVICE_ID)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String facilityId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("facilities")
                                .document(facilityId)
                                .collection("My Events")
                                .document(eventName)
                                .set(eventData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(CreateEventActivity.this, "Event created successfully", Toast.LENGTH_SHORT).show();
                                    // Generate and store QR code
                                    generateQRCode(eventName, facilityId);
                                    finish(); // Close the activity
                                })
                                .addOnFailureListener(e -> Toast.makeText(CreateEventActivity.this, "Error creating event", Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(CreateEventActivity.this, "Facility not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(CreateEventActivity.this, "Error retrieving facility", Toast.LENGTH_SHORT).show());

    }

    // Method to generate the QR code
    private void generateQRCode(String eventName, String facilityId) {
        // Link to the event's details (description and poster URL)
        String eventLink = "https://yourapp.com/event/" + facilityId + "/" + eventName;

        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(eventLink, BarcodeFormat.QR_CODE, 400, 400);

            // Display the QR code in the app (optional)
            //ImageView qrCodeImageView = findViewById(R.id.qrCodeImageView);
            //qrCodeImageView.setImageBitmap(bitmap);

            // Store the hash of the event URL
            String hash = generateHash(eventLink);
            storeHashInFirestore(eventName, facilityId, hash);

        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    // Method to generate a hash of the event link
    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Store the hash in Firestore
    private void storeHashInFirestore(String eventName, String facilityId, String hash) {
        Map<String, Object> qrCodeData = new HashMap<>();
        qrCodeData.put("hash", hash);

        db.collection("facilities")
                .document(facilityId)
                .collection("My Events")
                .document(eventName)
                .update(qrCodeData)
                .addOnSuccessListener(aVoid -> Toast.makeText(CreateEventActivity.this, "QR Code hash stored successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(CreateEventActivity.this, "Error storing QR Code hash", Toast.LENGTH_SHORT).show());
    }
}