package edu.berkeley.eecs.emission.cordova.shakedetect;

/**
 * Created by Felipe Arias
 */

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



import de.appplant.cordova.plugin.localnotification.TriggerReceiver;
import de.appplant.cordova.plugin.notification.Manager;

/*
 * Importing dependencies from the logger plugin
 */
import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.SimpleLocation;


import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.location.Location;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderApi;

public class ShakeDetector extends Service implements SensorEventListener{
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private float acceleration;
    private float currAcceleration;
    private float lastAcceleration;
    private long lastUpdated;

    private static final String CONFIG_LIST_KEY = "config_list";
    private static final String MUTED_LIST_KEY = "muted_list";
    private static final String POTENTIAL_INCIDENT = "potential_incident";
    private static final String ID = "id";

    @Override
    public IBinder onBind(Intent intent){
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
        {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, new Handler());
        }
        else if(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)
        {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL, new Handler());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public  void onAccuracyChanged(Sensor sensor, int accuracy){
    }

    @Override
    public void onSensorChanged(SensorEvent event){

        long currTime = System.currentTimeMillis();
        long deltaTime = currTime - lastUpdated;


        //Only get readings if an "accident" has not occured within the past 2 seconds
        if(deltaTime > 2000) { 

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                float xAcceleration = event.values[0];
                float yAcceleration = event.values[1];
                float zAcceleration = event.values[2];

                //Used to diminish the effect of the acceleration in the y-direction
                float yAccelerationConst = 0.4f;

                lastAcceleration = currAcceleration;
                currAcceleration = (float) Math.sqrt((double) (xAcceleration * xAcceleration + yAcceleration * yAcceleration * yAccelerationConst * yAccelerationConst));

                acceleration = acceleration * 0.90f + (currAcceleration - lastAcceleration);

                //String AccTag1 = "2Shake0.6A0.95-13-10";
                //Log.i(this, AccTag1, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration) + ",");

                if ((acceleration > 12f) && (Math.abs(zAcceleration) < 9)) {
                    lastUpdated = currTime;

                    try {
                        storeDataAndNotify(this, event.values, currTime, acceleration);
                    } catch(JSONException e) {
                        Log.e(this, "storeDataAndNotify", e.getMessage());
                        Log.e(this, "storeDataAndNotify", e.toString());
                    }

                }
            }
            else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
                float xRotation = event.values[0];
                float yRotation = event.values[1];
                float zRotation = event.values[2];
                String GyroTag = "2Gyro5";

                //Log.i(this, GyroTag, ","+String.valueOf(currTime)+"," + Float.toString(xRotation) + "," );

                if ((xRotation > 5f) && (yRotation < 5f) && (zRotation) < 5f) {
                    lastUpdated = currTime;
                    Log.i(this, GyroTag, "," + Float.toString(xRotation) + "," );
                    notifyEvent(this, POTENTIAL_INCIDENT, null);
                }
            }
        }
    }

    public void notifyEvent(Context context, String eventName, JSONObject autogenData) {
        String TAG = "ShakeDetector";
        Log.d(context, TAG, "Generating all notifications for generic "+eventName);
        try {
            JSONObject notifyConfigWrapper = UserCacheFactory.getUserCache(context).getLocalStorage(eventName, false);

            if (notifyConfigWrapper == null) {
                Log.d(context, TAG, "no configuration found for event "+eventName+", skipping notification");
                return;
            }
            JSONArray notifyConfigs = notifyConfigWrapper.getJSONArray(CONFIG_LIST_KEY);
            JSONArray mutedConfigs = notifyConfigWrapper.optJSONArray(MUTED_LIST_KEY);

            for(int i = 0; i < notifyConfigs.length(); i++) {
                try {
                    JSONObject currNotifyConfig = notifyConfigs.getJSONObject(i);
                    int mutedIndex = findEntryWithId(mutedConfigs, currNotifyConfig.getLong(ID));
                    if(mutedIndex == -1) {
                        if (autogenData != null) { // we need to merge in the autogenerated data with any user data
                            JSONObject currData = currNotifyConfig.optJSONObject("data");
                            if (currData == null) {
                                currData = new JSONObject();
                                currNotifyConfig.put("data", currData);
                            }
                            mergeObjects(currData, autogenData);
                        }
                        Log.d(context, TAG, "generating notification for event "+eventName
                                + " and id = " + currNotifyConfig.getLong(ID));
                        Manager.getInstance(context).schedule(currNotifyConfig, TriggerReceiver.class);
                    } else {
                        Log.d(context, TAG, "notification for event "+eventName+" and id = "+currNotifyConfig.getLong(ID)
                                +" muted, skip");
                    }
                } catch (Exception e) {
                    Log.e(context, TAG, "Got error "+e.getMessage()+" while processing object "
                            + notifyConfigs.getJSONObject(i) + " at index "+i);
                }
            }
        } catch(JSONException e) {
            Log.e(context, TAG, e.getMessage());
            Log.e(context, TAG, e.toString());
        }
    }

    private void mergeObjects(JSONObject existing, JSONObject autogen) throws JSONException {
        JSONArray toBeCopiedKeys = autogen.names();
        for(int j = 0; j < toBeCopiedKeys.length(); j++) {
            String currKey = toBeCopiedKeys.getString(j);
            existing.put(currKey, autogen.get(currKey));
        }
    }

    private int findEntryWithId(JSONArray array, long id) throws JSONException {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject currCheckedObject = array.getJSONObject(i);
            if (currCheckedObject.getLong(ID) == id) {
                return i;
            }
        }
        return -1;
    }

    private void storeDataAndNotify(Context context, float[] accelerations, long currTime, float combinedAccel) throws JSONException{

        JSONObject incidentData = new JSONObject();
        UserCache uc = UserCacheFactory.getUserCache(this);
        SimpleLocation loc[] = uc.getLastSensorData(R.string.key_usercache_filtered_location, 1, SimpleLocation.class);

        if (loc.length == 0) {
            incidentData.put("inc_ts", ((double) currTime) / 1000);
        } else {
            SimpleLocation mostRecentLocation = loc[0];
            incidentData.put("inc_ts", ((double) currTime) / 1000);
            incidentData.put("inc_latitude", mostRecentLocation.getLatitude());
            incidentData.put("inc_longitude", mostRecentLocation.getLongitude());
            incidentData.put("inc_altitude", mostRecentLocation.getAltitude());
            incidentData.put("inc_speed", mostRecentLocation.getSensed_speed());
            incidentData.put("inc_accuracy", mostRecentLocation.getAccuracy());
            incidentData.put("inc_bearing", mostRecentLocation.getBearing());
        }

        //int modeOfTransportation = -1;

        //Storage of mode of transportation (not implemented on iOS)
        /*
        ActivityRecognitionResult currActivity[] = uc.getLastSensorData(R.string.key_usercache_activity, 1, ActivityRecognitionResult.class);

        if ((currActivity.length != 0) && (currActivity[0] != null)){
            DetectedActivity mostProbableActivity = currActivity[0].getMostProbableActivity();
            modeOfTransportation = mostProbableActivity.getType();
        }

        incidentData.put("inc_mode", modeOfTransportation);
        */

        incidentData.put("inc_xAccel", accelerations[0]);
        incidentData.put("inc_yAccel", accelerations[1]);
        incidentData.put("inc_zAccel", accelerations[2]);
        incidentData.put("inc_combAccel", combinedAccel);

        notifyEvent(context, POTENTIAL_INCIDENT, incidentData);
    }
}