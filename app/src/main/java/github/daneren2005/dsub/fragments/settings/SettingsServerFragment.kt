package github.daneren2005.dsub.fragments.settings

import android.os.Bundle
import github.daneren2005.dsub.R

class SettingsServerFragment : SettingsBaseFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_servers, rootKey)
    }
}