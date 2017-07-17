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
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import java.util.Iterator;

public class ShakeDetector extends Service implements SensorEventListener{
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor linearAccelerometer;
    private float acceleration;
    private float acceleration2;
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
        else if(sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null) {
            linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            sensorManager.registerListener(this, linearAccelerometer, SensorManager.SENSOR_DELAY_NORMAL, new Handler());
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
        long deltaTime = currTime; // - lastUpdated;


        //Only get readings if an "accident" has not occured within the past 0.5 seconds
        if(deltaTime > 500) { //Make the 500 a const

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float xAcceleration = event.values[0];
                float yAcceleration = event.values[1];
                float zAcceleration = event.values[2];

                //Used to diminish the effect of the acceleration in the y-direction
                float yAccelerationConst = 0.6f;

                lastAcceleration = currAcceleration;
                currAcceleration = (float) Math.sqrt((double) (zAcceleration * zAcceleration + yAcceleration * yAcceleration * yAccelerationConst * yAccelerationConst));

                acceleration = acceleration * 0.95f + (currAcceleration - lastAcceleration);
                String AccTag1 = "2Shake0.6A0.95-13-10";

                //Log.i(this, AccTag1, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration) + ",");

                if ((acceleration > 13f) && (Math.abs(xAcceleration) < 10)) {
                    lastUpdated = currTime;
                    //NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, AccTag1);
                    Log.i(this, AccTag1, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration) + ",");
                    notifyEvent(this, POTENTIAL_INCIDENT, new JSONObject());
                }

                acceleration2 = acceleration2 * 0.85f + (currAcceleration - lastAcceleration);
                String AccTag2 = "2Shake0.6A0.85-13-10";

                //Log.i(this, AccTag2, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration2) + ",");


                if ((acceleration > 13f) && (Math.abs(xAcceleration) < 10)) {
                    lastUpdated = currTime;
                    //NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, AccTag2);
                    Log.i(this, AccTag2, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration2) + ",");
                    notifyEvent(this, POTENTIAL_INCIDENT, new JSONObject());
                }
            }
            else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
                float xAcceleration = event.values[0];
                float yAcceleration = event.values[1];
                float zAcceleration = event.values[2];

                //Used to diminish the effect of the acceleration in the y-direction
                float yAccelerationConst = 0.6f;

                lastAcceleration = currAcceleration;
                currAcceleration = (float) Math.sqrt((double) (zAcceleration * zAcceleration + yAcceleration * yAcceleration * yAccelerationConst * yAccelerationConst));

                acceleration = acceleration * 0.95f + (currAcceleration - lastAcceleration);
                String LAccTag = "2Shake0.6LA0.95";
                //Log.i(this, LAccTag, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration) + ",");

                if ((acceleration > 13f) && (Math.abs(xAcceleration) < 10)) {
                    lastUpdated = currTime;
                    //NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, LAccTag);
                    Log.i(this, LAccTag, "," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration) + ",");
                    notifyEvent(this, POTENTIAL_INCIDENT, new JSONObject());
                }

                acceleration2 = acceleration2 * 0.85f + (currAcceleration - lastAcceleration);
                String LAccTag2 = "2Shake0.6LA0.85";

                //Log.i(this, LAccTag2, ","+String.valueOf(currTime)+"," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration2) + ",");

                if ((acceleration > 13f) && (Math.abs(xAcceleration) < 10)) {
                    lastUpdated = currTime;
                    //NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, LAccTag2);
                    Log.i(this, LAccTag2, "," + Float.toString(xAcceleration) + "," + Float.toString(yAcceleration) + "," + Float.toString(zAcceleration) + "," + Float.toString(acceleration2) + ",");
                    notifyEvent(this, POTENTIAL_INCIDENT, new JSONObject());
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
                    //NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, GyroTag);
                    Log.i(this, GyroTag, "," + Float.toString(xRotation) + "," );
                    notifyEvent(this, POTENTIAL_INCIDENT, new JSONObject());
                }
            }
        }
    }

    public void notifyEvent(Context context, String eventName, JSONObject autogenData) {
        String TAG = "ShakeDetector";
        Intent shakeDetectionIntent = new Intent();
        shakeDetectionIntent.setAction(eventName);
        shakeDetectionIntent.putExtras(jsonToBundle(autogenData));
        context.sendBroadcast(shakeDetectionIntent);
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

        private Bundle jsonToBundle(JSONObject toConvert) {
            Bundle bundle = new Bundle();

            for (Iterator<String> it = toConvert.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray arr = toConvert.optJSONArray(key);
                Double num = toConvert.optDouble(key);
                String str = toConvert.optString(key);

                if (arr != null && arr.length() <= 0)
                    bundle.putStringArray(key, new String[]{});

                else if (arr != null && !Double.isNaN(arr.optDouble(0))) {
                    double[] newarr = new double[arr.length()];
                    for (int i=0; i<arr.length(); i++)
                        newarr[i] = arr.optDouble(i);
                    bundle.putDoubleArray(key, newarr);
                }

                else if (arr != null && arr.optString(0) != null) {
                    String[] newarr = new String[arr.length()];
                    for (int i=0; i<arr.length(); i++)
                        newarr[i] = arr.optString(i);
                    bundle.putStringArray(key, newarr);
                }

                else if (!num.isNaN())
                    bundle.putDouble(key, num);

                else if (str != null)
                    bundle.putString(key, str);

                else
                    System.err.println("unable to transform json to bundle " + key);
            }
            return bundle;
        }

}