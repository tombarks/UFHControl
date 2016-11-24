package tombarks.UFHControl;


import android.os.Bundle;
import android.preference.PreferenceActivity;

import tombarks.UFHControl.R;

public class preferences_activity extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
