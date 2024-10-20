package com.example.eventmaster;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ProfilePicture {

    /**
     * Creates a URI of the bitmap generated from auto generating a profile picture
     *
     * @param context used to save the profile picture in the app’s external files directory
     * @param bitmap the bitmap of the profile picture photo
     * @param name the user's name
     *
     * @return the URI of the auto generated profile picture
     */
    public static Uri saveProfilePicture(Context context, Bitmap bitmap, String name) {
        // Create a directory for saving the profile pictures
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ProfilePictures");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Create a file with the username as the filename
        File imageFile = new File(directory, name + "_profile.png");

        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            // Compress the bitmap into PNG format and save it to the file
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Return the URI of the saved image
        return Uri.fromFile(imageFile);
    }
}
