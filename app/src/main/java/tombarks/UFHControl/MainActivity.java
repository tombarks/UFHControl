package tombarks.UFHControl;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.VolleyError;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView targetText;
    TextView tempText;
    TextView userFeedListText;
    EditText sendText;
    SharedPreferences prefs;
    Button button_send;
    Button button_fetch;

    Handler autoFetchHandler = new Handler();
    int delay = 1000; //milliseconds
    long fetchExecutionTimer = 0;

    LineChart chart;

    boolean appIsRunning = true;

    String chartFeedName;

    public int mId;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotificationManager;

    String targetValueString = "Unknown";
    String currentValueString = "Unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chartFeedName =  getString(R.string.chartFeedNameDefault);

        //Get handles to GUI objects
        targetText = (TextView) findViewById(R.id.fetchText);
        tempText = (TextView) findViewById(R.id.ufhTemp);
        sendText = (EditText) findViewById(R.id.sendText);
        userFeedListText = (TextView) findViewById(R.id.userFeedList);
        button_send = (Button)findViewById(R.id.buttonSend);
        button_fetch = (Button)findViewById(R.id.buttonFetch);
        chart = (LineChart) findViewById(R.id.chart);

        //Get shared prefereces
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //Create persistent notification
        if(prefs.getBoolean("showNotification", true))createNotification();

        //Remove edit text focus
        findViewById(R.id.mainLayout).requestFocus();

        //do a fetch if the preference to do so has been set
        if(prefs.getBoolean("startupSync", true))fetchWebData();

        // Run the above code block on the main thread after 2 seconds
        autoFetchHandler.postDelayed(runnableCode, 1000);
    }

    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            if(appIsRunning && prefs.getBoolean("autoSync", false) && (System.currentTimeMillis() - fetchExecutionTimer) >= 30000)fetchWebData();
            autoFetchHandler.postDelayed(runnableCode, 1000);
        }
    };

    //chart
    public void drawChart()
    {
        //Generate start and end time in linux mills from poch
        //Start is 6 hours in the past
        String now = Long.toString(System.currentTimeMillis());
        String then = Long.toString(System.currentTimeMillis()-21600000);
        String chartFeedIDstring = prefs.getString("chartFeedID", "");

        String bulkGetUrl = "https://emoncms.org/feed/data.json?id="+chartFeedIDstring+"&start="+then+"&end="+now+"&interval=600";

        //Create the request que
        RequestQueue queue = Volley.newRequestQueue(this);

        // Request a string response from the provided target URL.
        StringRequest chartDataRequest = new StringRequest(com.android.volley.Request.Method.GET, bulkGetUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        List<Entry> entries = new ArrayList<Entry>();
                        try
                        {
                            JSONArray jsonArray = new JSONArray(response);

                            for(int i=0;i<jsonArray.length();i++) {
                                JSONArray jsonArray2 = new JSONArray(jsonArray.getJSONArray(i).toString());
                                entries.add(new Entry(-(jsonArray.length()* 10) + (i*10), (float)jsonArray2.getDouble(1)));
                            }

                            LineDataSet dataSet = new LineDataSet(entries, chartFeedName);

                            LineData lineData = new LineData(dataSet);
                            lineData.setDrawValues(false);
                            chart.setData(lineData);
                            Description desc = new Description();
                            desc.setText("");
                            chart.setDescription(desc);
                            chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
                            chart.invalidate(); // refresh
                        }

                        catch (Exception e)
                        {
                            Context context = getApplicationContext();
                            CharSequence text = e.toString();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(context, text, duration);
                            toast.show();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Context context = getApplicationContext();
                CharSequence text = "chart render failed";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });

        queue.add(chartDataRequest);
    }

    /** Called when the user clicks the Send button */
    public void fetch(View view) {
        fetchWebData();
    }

    //Getch Method
    public void fetchWebData()
    {
        //log execution time
        fetchExecutionTimer = System.currentTimeMillis();

        //disable buttons
        button_fetch.setEnabled(false);
        button_send.setEnabled(false);

        //Get the current target temperature
        getTemp();

        //Fetch users public channels
        getUserPublicChannels();

        //Get chart data
        drawChart();

        //callback to enable buttons
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Enable buttons
                button_fetch.setEnabled(true);
                button_send.setEnabled(true);
            }
        }, 1000);
    }

    //Send the temp
    public void send(View view) {
        setTemp(sendText.getText().toString());
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

                        // Display the first 500 characters of the response string.
                        targetText.setText(getString(R.string.ufhTarget) + targetValueString);

                        if(mBuilder != null && mNotificationManager != null && prefs.getBoolean("showNotification", true)) {
                            mBuilder.setContentText("UFH Target: " + targetValueString + ", UFH Temperature: " + currentValueString);
                            // Because the ID remains unchanged, the existing notification is
                            // updated.
                            mNotificationManager.notify(
                                    mId,
                                    mBuilder.build());
                        }

                        Context context = getApplicationContext();
                        CharSequence text = getString(R.string.temperatureFetched);
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Context context = getApplicationContext();
                CharSequence text = getString(R.string.temperatureFetchedFail);
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
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

                        // Display the first 500 characters of the response string.
                        tempText.setText(getString(R.string.ufhTemp) + currentValueString);

                        if(mBuilder != null && mNotificationManager != null && prefs.getBoolean("showNotification", true)) {
                            mBuilder.setContentText("UFH Target: " + targetValueString + ", UFH Temperature: " + currentValueString);
                            // Because the ID remains unchanged, the existing notification is
                            // updated.
                            mNotificationManager.notify(
                                    mId,
                                    mBuilder.build());
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Context context = getApplicationContext();
                CharSequence text = getString(R.string.temperatureFetchedFail);
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });

        // Add the request to the RequestQueue.
        queue.add(tempRequest);

    }

    //Get the current temperature
    private void setTemp(String temperature)
    {
        //check if the value for the temperature is valid
        try {
            int myNum = Integer.parseInt(temperature);
            if(myNum < 0 || myNum > 30)
            {
                Context context = getApplicationContext();
                CharSequence text = getString(R.string.invalidTargetTemp);
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                return;
            }
        } catch(NumberFormatException nfe) {
            Context context = getApplicationContext();
            CharSequence text = getString(R.string.invalidTargetTemp);
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            button_fetch.setEnabled(true);
            button_send.setEnabled(true);

            return;
        }

        //disable buttons
        button_fetch.setEnabled(false);
        button_send.setEnabled(false);
        
        // Instantiate the RequestQueue.
        String apiKey = prefs.getString("apiKey", "");
        String feedName = prefs.getString("targetInputName", "");
        RequestQueue queue = Volley.newRequestQueue(this);

        //build the url string
        String url = getString(R.string.emonPostURL).replace("[apiKey]", apiKey).replace("[feedName]", feedName).replace("[temperature]",temperature);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(com.android.volley.Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        String responseString = getString(R.string.sendingTemp) + response;
                        Context context = getApplicationContext();
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, responseString, duration);
                        toast.show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Context context = getApplicationContext();
                CharSequence text = getString(R.string.failedSendingTemp);
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        //callback to enable buttons
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Enable buttons
                button_fetch.setEnabled(true);
                button_send.setEnabled(true);
            }
        }, 1000);
    }

    //Get list of channels
    private void getUserPublicChannels()
    {
        //Create the request que
        RequestQueue queue = Volley.newRequestQueue(this);

        //get user id
        String userIDString = prefs.getString("userID", "");

        //build the users request string
        String userListURL = getString(R.string.emonUserListURL) + userIDString;

        // Request a string response from the provided target URL.
        StringRequest userListRequest = new StringRequest(com.android.volley.Request.Method.GET, userListURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        parseUserFeedJSON(response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                userFeedListText.setText("Error fetching user feed list");
            }
        });

        // Add the request to the RequestQueue.
        queue.add(userListRequest);
    }

    //parse user feedJson
    public void parseUserFeedJSON(String JSONString)
    {
        try
        {
            JSONArray jsonArray = new JSONArray(JSONString);
            String text = "";
            for(int i=0;i<jsonArray.length();i++) {
                String name = jsonArray.getJSONObject(i).getString("name");
                String value = jsonArray.getJSONObject(i).getString("value");
                String id = jsonArray.getJSONObject(i).getString("id");

                if(id.equals(prefs.getString("chartFeedID", "")))
                {
                    chartFeedName = name;
                }
                text += name + " [" + id + "]: " + value + "\n";
            }

            userFeedListText.setText(text);
        }
        catch(JSONException e)
        {
            userFeedListText.setText("Error parsing user feed list JSON: "+ e.toString());
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                Intent i = new Intent(MainActivity.this, preferences_activity.class);
                startActivity(i);
                return true;
            case R.id.about:
                Context context = getApplicationContext();
                CharSequence text = getString(R.string.aboutText);
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();  // Always call the superclass method first

        //get shared prefereces and clear notification
        if(!prefs.getBoolean("showNotification", true) && mNotificationManager != null)mNotificationManager.cancel(mId);
        if(prefs.getBoolean("showNotification", true) && mNotificationManager == null)createNotification();

        // Activity being restarted from stopped state
        //do a fetch if the preference to do so has been set
        boolean pref = prefs.getBoolean("startupSync", true);
        if(pref)
        {
            fetchWebData();
        }

        appIsRunning = true;

        //Remove edit text focus
        findViewById(R.id.mainLayout).requestFocus();
    }

    private void createNotification()
    {
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_ac_unit_black_24dp)
                        .setContentTitle("Underfloor Heating Control")
                        .setContentText("Waiting for update.").setOngoing(true);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this,  MainActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(mId, mBuilder.build());
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        //end
        if(!prefs.getBoolean("showNotification", true) && mNotificationManager != null)mNotificationManager.cancel(mId);
        if(prefs.getBoolean("showNotification", true) && mNotificationManager == null)createNotification();

        appIsRunning = false;
    }

    @Override
    public void onDestroy() {
        if(mNotificationManager !=null)mNotificationManager.cancel(mId);
        super.onDestroy();

    }
}
