package edu.berkeley.eecs.emission.cordova.transitionnotify;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.webkit.ValueCallback;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.emission.BuildConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/**
 * This class echoes a string called from JavaScript.
 */
public class TransitionNotifier extends CordovaPlugin {

    public static final String USERDATA = "userdata";
    private static String TAG =  TransitionNotifier.class.getSimpleName();

    public static final String EVENTNAME_ERROR = "event name null or empty.";
    public static final String CONFIG_ERROR = "config name null or empty.";

    private static final String CONFIG_LIST_KEY = "config_list";
    private static final String MUTED_LIST_KEY = "muted_list";
    private static final String ID = "id";

    java.util.Map<String,BroadcastReceiver> receiverMap =
                    new java.util.HashMap<String,BroadcastReceiver>(10);

    @Override
    public Object onMessage(String id, Object data) {
        Log.d(cordova.getActivity(), TAG, "Received onMessage with id = "+id+" and data = "+data);
        return Boolean.TRUE;
    }

    private int findEntryWithId(JSONArray array, long id) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
           JSONObject currCheckedObject = array.getJSONObject(i);
            if (currCheckedObject.getLong(ID) == id) {
                return i;
            }
        }
        return -1;
    }

    private JSONArray filterNulls(Context context, JSONArray unfilteredArray) {
        JSONArray filteredArray = new JSONArray();
        for (int i = 0; i < unfilteredArray.length(); i++) {
            if (!unfilteredArray.isNull(i)) {
                try {
                    filteredArray.put(unfilteredArray.get(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(context, TAG, "Skipped entry at index " + i+ " because of "+e.getMessage());
                }
            }
        }
        return filteredArray;
    }

    private void addOrReplaceEntry(Context ctxt, String eventName,
                                   JSONObject localNotifyConfig, String listName) throws JSONException {
            JSONObject configWrapper = UserCacheFactory.getUserCache(ctxt).getLocalStorage(eventName, false);
            JSONArray currList;

            if (configWrapper == null) {
                configWrapper = new JSONObject();
                currList = new JSONArray();
            configWrapper.put(listName, currList);
                            } else {
            currList = configWrapper.optJSONArray(listName);
            if (currList == null) {
                currList = new JSONArray();
                configWrapper.put(listName, currList);
            }
                            }

            if(BuildConfig.DEBUG) {
            if (configWrapper == null || currList == null &&
                    configWrapper.getJSONArray(listName) != currList) {
                throw new RuntimeException("configWrapper = "+configWrapper+" currList = "+currList);
            }
                        }

            int existingIndex = findEntryWithId(currList, localNotifyConfig.getLong(ID));

            boolean modified = true;
            if (existingIndex == -1) {
                Log.d(ctxt, TAG, "new configuration, adding object with id "+localNotifyConfig.getLong(ID));
                currList.put(localNotifyConfig);
            } else {
                if (localNotifyConfig.equals(currList.getJSONObject(existingIndex))) {
                    Log.d(ctxt, TAG, "configuration unchanged, skipping list modify");
                    modified = false;
                } else {
                    Log.d(ctxt, TAG, "configuration changed, changing object at index "+existingIndex);
                    currList.put(existingIndex, localNotifyConfig);
                }
            }

            if (modified) {
                UserCacheFactory.getUserCache(ctxt).putLocalStorage(eventName, configWrapper);
            }
    }

    private void removeEntry(Context ctxt, String eventName,
                                   JSONObject localNotifyConfig, String listName) throws JSONException {
        JSONObject configWrapper = UserCacheFactory.getUserCache(ctxt).getLocalStorage(eventName, false);

        if (configWrapper != null) { // There is an existing entry for this event
            JSONArray currList = configWrapper.getJSONArray(listName);
            int existingIndex = findEntryWithId(currList, localNotifyConfig.getLong(ID));
            if (existingIndex != -1) { // There is an existing entry for this ID
                Log.d(ctxt, TAG, "removed obsolete notification at " + existingIndex);
                // Should be replaced by remove once we move our minApi version up
                currList.put(existingIndex, null);
                currList = filterNulls(ctxt, currList);
                if (currList.length() == 0) { // list size is now zero, can remove the entry
                    // Let us think about what we want to happen here. One thought might be that we want to
                    // remove the entry iff both lists are empty. But then you could run into situations in which
                    // there was a notification, it was muted, and then it was removed. Because the muted list still
                    // had an entry, we would keep the (zombie) entry around.
                    // So it seems like we should actually look at the list and treat the config list and the muted list
                    // differently. Alternatively, we could document that you always need to unmute a list before deleting it
                    // but that places additional (unnecessary) burden on the user.
                    // So let's treat them separately for now and fix later if it is a problem
                    if (listName.equals(CONFIG_LIST_KEY)) {
                    Log.d(ctxt, TAG, "list size is now, zero, removing entry for event "+eventName);
                    UserCacheFactory.getUserCache(ctxt).removeLocalStorage(eventName);
                } else {
                        if (BuildConfig.DEBUG) {
                            if (!listName.equals(MUTED_LIST_KEY)) {
                                throw new RuntimeException("listName = "+listName+" expected "+MUTED_LIST_KEY);
                            }
                            Log.d(ctxt, TAG, "muted list size is now 0, removing list "+MUTED_LIST_KEY
                                    +" for event "+eventName);
                            configWrapper.remove(listName);
                            UserCacheFactory.getUserCache(ctxt).putLocalStorage(eventName, configWrapper);
                        }
                    }
                } else {
                    Log.d(ctxt, TAG, "saving list with size "+currList.length());
                    UserCacheFactory.getUserCache(ctxt).putLocalStorage(eventName, configWrapper);
                }
            }
        }
    }

    /**
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Context ctxt = cordova.getActivity();
        if (action.equals("addEventListener")) {

            final String eventName = args.getString(0);
            if( eventName==null || eventName.isEmpty() ) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
        }

            final JSONObject localNotifyConfig = args.getJSONObject(1);
            if (localNotifyConfig == null || localNotifyConfig.length() == 0) {
                callbackContext.error(CONFIG_ERROR);
                return false;
            }

            addOrReplaceEntry(ctxt, eventName, localNotifyConfig, CONFIG_LIST_KEY);
            callbackContext.success();

            return true;
        } else if (action.equals("removeEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            final JSONObject localNotifyConfig = args.getJSONObject(1);
            if (localNotifyConfig == null || localNotifyConfig.length() == 0) {
                callbackContext.error(CONFIG_ERROR);
                return false;
            }

            removeEntry(ctxt, eventName, localNotifyConfig, CONFIG_LIST_KEY);
            callbackContext.success();
            return true;
        } else if (action.equals("enableEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            final JSONObject localNotifyConfig = args.getJSONObject(1);
            if (localNotifyConfig == null || localNotifyConfig.length() == 0) {
                callbackContext.error(CONFIG_ERROR);
                return false;
                    }

            removeEntry(ctxt, eventName, localNotifyConfig, MUTED_LIST_KEY);
            callbackContext.success();
            return true;
        } else if (action.equals("disableEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            final JSONObject localNotifyConfig = args.getJSONObject(1);
            if (localNotifyConfig == null || localNotifyConfig.length() == 0) {
                callbackContext.error(CONFIG_ERROR);
                return false;
            }

            addOrReplaceEntry(ctxt, eventName, localNotifyConfig, MUTED_LIST_KEY);
            callbackContext.success();
            return true;
        }
        return false;
    }
}
