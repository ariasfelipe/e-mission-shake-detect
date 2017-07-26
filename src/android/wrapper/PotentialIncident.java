package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import android.location.Location;

import java.text.SimpleDateFormat;

/**
 * Created by Felipe Arias
 */

public class PotentialIncident {
    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getTs() {
        return ts;
    }

    public float getXVal() {return xVal;}
    public float getYVal() {return yVal;}
    public float getZVal() {return zVal;}

    private double latitude;
    private double longitude;
    private double altitude;

    private double ts;
    private String fmt_time;
    private long elapsedRealtimeNanos;
    private float accuracy;
    private float bearing;

    private float xVal;
    private float yVal;
    private float zVal;
    private float sensed_speed;


    private String provider;
    private final String filter = "time";
    private int modeOfTransportation;
    private int typeOfIncident;

    /*
     * No-arg constructor to use with gson.
     * If this works, consider switching to a custom serializer instead.
     */
    public PotentialIncident() {}

    public PotentialIncident(Location loc, float accelVals[], int activityType) {
        latitude = loc.getLatitude();
        longitude = loc.getLongitude();
        altitude = loc.getAltitude();
        xVal = accelVals[0];
        yVal = accelVals[1];
        zVal = accelVals[2];


        modeOfTransportation = activityType;

        ts = ((double)loc.getTime())/1000;
        // NOTE: There is no ISO format datetime shortcut on java.
        // This will probably return values that are not in the ISO format.
        // but that's OK because we will fix it on the server
        fmt_time = SimpleDateFormat.getDateTimeInstance().format(loc.getTime());
        elapsedRealtimeNanos = loc.getElapsedRealtimeNanos();

        sensed_speed = loc.getSpeed();
        accuracy = loc.getAccuracy();
        bearing = loc.getBearing();

        sensed_speed = loc.getSpeed();

    }
}
