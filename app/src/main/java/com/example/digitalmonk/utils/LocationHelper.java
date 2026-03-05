package com.example.digitalmonk.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;

public class LocationHelper {
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;

    public interface LocationResultListener {
        void onLocationFound(Location location);
    }

    public LocationHelper(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void fetchCurrentLocation(LocationResultListener listener) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("G4", "Location permission missing in Helper");
            return;
        }

        // Request a single high-accuracy update
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000)
                .setMaxUpdates(1)
                .setDurationMillis(10000)
                .build();

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult res) {
                if (res != null && res.getLastLocation() != null) {
                    listener.onLocationFound(res.getLastLocation());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(req, callback, Looper.getMainLooper());
    }
}