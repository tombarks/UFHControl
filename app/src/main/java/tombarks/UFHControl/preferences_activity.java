package tombarks.UFHControl;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import tombarks.UFHControl.R;

public class preferences_activity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // handle the preference change here
        if(MyDebug.LOG)Log.i("tom", "Preference " + key + " changed");

        switch(key)
        {
            case "showNotification":
                if(MyDebug.LOG)Log.i("tom", "Changing notification state");
                break;

            default:
                break;
        }
    }
}
