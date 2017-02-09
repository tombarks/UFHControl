package tombarks.UFHControl;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Binder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class EmonCMSFetchService extends Service {

    private final IBinder emonCMSBinder = new MyLocalBinder();
    int value = 0;
    SharedPreferences prefs;

    String targetValueString;
    String currentValueString;

    public int mId = 123;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotificationManager;
    BroadcastReceiver mReceiver;

    private boolean screenOff;
    private boolean isRunning = true;

    public EmonCMSFetchService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return emonCMSBinder;
    }

    public String getString(){
        return "Hello World!";
    }

    public int getValue(){
     return value;
    }

    public String getTemperature()
    {
        return currentValueString;
    }

    public String getTargetTemp(){
        return targetValueString;
    }

    public class MyLocalBinder extends Binder{
        EmonCMSFetchService getService(){
            return EmonCMSFetchService.this;
        }
    }

    public void onCreate()
    {
        final Context context = getApplicationContext();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        createNotification("Waiting for update.");

        // REGISTER RECEIVER THAT HANDLES SCREEN ON AND SCREEN OFF LOGIC
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mReceiver = new ScreenReceiver();
        registerReceiver(mReceiver, filter);

        // If you use API20 or more:
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() != Display.STATE_OFF) {
                screenOff = false;
                if(MyDebug.LOG)Log.i("tom", "Screen initial state on.");
            }
            else {
                screenOff = true;
                if(MyDebug.LOG)Log.i("tom", "Screen initial state off.");
            }
        }

        if(MyDebug.LOG)Log.i("tom", "Service created.");

        new Thread(new Runnable() {
            public void run() {

                // Moves the current Thread into the background
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                if(MyDebug.LOG)Log.i("tom", "Service thread started");

                //Loop in the thread as long as boolean value is set true
                while(isRunning)
                {
                    if(MyDebug.LOG)Log.i("tom", "Running thread..");

                    if(!screenOff)
                    {
                        ConnectivityManager cm =
                                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatus = context.registerReceiver(null, ifilter);

                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                        float batteryPct = level / (float) scale;

                        boolean isNotLowBattery = batteryPct >= 0.2;

                        if(MyDebug.LOG)Log.i("tom", "Battery level: " + batteryPct + " Is not low: " + isNotLowBattery);
                        if(MyDebug.LOG)Log.i("tom", "Connected to WAN: " + isConnected);

                        if (isConnected && isNotLowBattery && !screenOff) getTemp();
                        else {
                            String errorMessage = "";
                            if (!isConnected) errorMessage += "No internet connection. ";
                            if (!isNotLowBattery) errorMessage += "Battery is < 20%. ";
                            errorMessage += "Sync disabled.";

                            createNotification(errorMessage);
                        }
                    }
                    else if(MyDebug.LOG)Log.i("tom", "Screen off");

                    try {
                        Thread.sleep(300000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if(MyDebug.LOG)Log.i("tom", "thread closed");
            }
        }).start();

        if(MyDebug.LOG)Log.i("tom", "End of on create");
    }

    //Get the current temperature
    private void getTemp()
    {
        //Create the request que
        RequestQueue queue = Volley.newRequestQueue(this);

        //Get the feed ID's
        final String targetFeedID = prefs.getString("targetFeedID", "");
        String temperatureFeedID = prefs.getString("tempFeedID", "");

        //build the request string
        String targetFetchURL = getString(R.string.emonURL) + targetFeedID;
        String temperatureFetchURL = getString(R.string.emonURL) + temperatureFeedID;

        // Request a string response from the provided target URL.
        StringRequest targetRequest = new StringRequest(com.android.volley.Request.Method.GET, targetFetchURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        targetValueString = response.replace("\"", "")+"\u00B0C";

                        if(MyDebug.LOG)Log.i("tom", "Target value: " + targetValueString);

                        createNotification("Target Temperature: "+ targetValueString + " Current Temperature: " + currentValueString);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        // Add the request to the RequestQueue.
        queue.add(targetRequest);

        // Request a string response from the provided target URL.
        StringRequest tempRequest = new StringRequest(com.android.volley.Request.Method.GET, temperatureFetchURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        currentValueString = response.replace("\"", "")+"\u00B0C";

                        if(MyDebug.LOG)Log.i("tom", "Current value: " + currentValueString);

                        createNotification("Target Temperature: "+ targetValueString + " Current Temperature: " + currentValueString);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        // Add the request to the RequestQueue.
        queue.add(tempRequest);

    }

    private void createNotification(String text)
    {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Underfloor Heating Control")
                .setContentText(text).setOngoing(true)
                .setSmallIcon(R.drawable.ic_ac_unit_black_24dp)
                .setContentIntent(pendingIntent)
                .setTicker("ufhControl")
                .build();

        startForeground(mId, notification);
        if(MyDebug.LOG)Log.i("tom", "notification started: " + new Integer(mId).toString());
    }

    public class ScreenReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                screenOff = true;
                if(MyDebug.LOG)Log.i("tom", "Screen off");
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screenOff = false;
                if(MyDebug.LOG)Log.i("tom", "Screen on");

                createNotification("Updating values.");

                getTemp();
            }
        }

    }

    @Override
    public void onDestroy() {
        if(MyDebug.LOG)Log.i("tom", "Service destroyed");
        isRunning = false;
        //mNotificationManager.cancel(mId);

        try{
            unregisterReceiver(mReceiver);
            //if(MyDebug.LOG)Log.i("tom", "OnDestroy receiver registered");
        }catch(Exception ex){
            //if(MyDebug.LOG)Log.i("tom", "OnDestroy receiver not registered");
        }
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        if(MyDebug.LOG)Log.i("tom", "Service unbound");

        return super.onUnbind(intent);
    }

    /** The service is starting, due to a call to startService() */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(MyDebug.LOG)Log.i("tom", "service on StartCommand");
        return START_STICKY;
    }
}
