package edu.berkeley.eecs.emission.cordova.shakedetect;

        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;

        import java.util.Arrays;
        import java.util.HashSet;
        import java.util.Set;



        import edu.berkeley.eecs.emission.R;
        import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;


public class ShakeNotificationReceiver extends BroadcastReceiver {

    public static final String USERDATA = "userdata";
    private static String TAG =  ShakeNotificationReceiver.class.getSimpleName();

    public static final String EVENTNAME_ERROR = "event name null or empty.";

    private static final String TRIP_STARTED = "trip_started";
    private static final String TRIP_ENDED = "trip_ended";
    private static final String TRACKING_STARTED = "tracking_started";
    private static final String TRACKING_STOPPED = "tracking_stopped";
    private static final String POTENTIAL_INCIDENT = "potential_incident";

    private static final String CONFIG_LIST_KEY = "config_list";
    private static final String MUTED_LIST_KEY = "muted_list";
    private static final String ID = "id";

    public ShakeNotificationReceiver() {
        // The automatically created receiver needs a default constructor
        android.util.Log.i(TAG, "noarg constructor called");
    }

    public ShakeNotificationReceiver(Context context) {
        android.util.Log.i(TAG, "constructor called with arg "+context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(context, TAG, "shakeNotificationReciever onReceive(" + context + ", " + intent + ") called");

        Set<String> validTransitions = new HashSet<String>(Arrays.asList(new String[]{
                context.getString(R.string.transition_initialize),
                context.getString(R.string.transition_exited_geofence),
                context.getString(R.string.transition_stopped_moving),
                context.getString(R.string.transition_stop_tracking),
                context.getString(R.string.transition_start_tracking),
                context.getString(R.string.transition_tracking_error)
        }));

        if (!validTransitions.contains(intent.getAction())) {
            Log.e(context, TAG, "Received unknown action "+intent.getAction()+" ignoring");
            return;
        }
        manageShakeDetection(context, intent.getAction());
    }

    /**
     * @param context
     * @param eventName
     */

    protected void manageShakeDetection( final Context context, final String eventName){

        if (eventName.equals(context.getString(R.string.transition_exited_geofence))) {
            //Start listening for shakes
            Intent intent = new Intent(context, ShakeDetector.class);
            context.startService(intent);
        }
        else if (eventName.equals(context.getString(R.string.transition_stopped_moving)) || eventName.equals("local.transition.stopped_moving") || eventName.equals("local.transition.tracking_error")) {
            //Stop listening for shakes
            Intent intent = new Intent(context, ShakeDetector.class);
            context.stopService(intent);
        }
    }
}
