package github.daneren2005.dsub.fragments.settings

import android.os.Bundle
import github.daneren2005.dsub.R

class SettingsCastFragment : SettingsBaseFragment() {

    /*private val themeMode by lazy { findPreference<ListPreference>(getString(R.string.key_appearance_theme_mode)) }
    private val amoledMode by lazy { findPreference<Preference>(getString(R.string.key_appearance_pure_black)) }
    private val appTheme by lazy { findPreference<ThemesPreference>(getString(R.string.key_appearance_app_theme)) }
*/
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_cast, rootKey)
    }
}