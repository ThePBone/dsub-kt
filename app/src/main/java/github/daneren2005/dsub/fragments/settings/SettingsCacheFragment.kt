package github.daneren2005.dsub.fragments.settings

import android.os.Bundle
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.FileUtil
import github.daneren2005.dsub.util.Util
import github.daneren2005.dsub.util.Util.getPreferences
import github.daneren2005.dsub.view.CacheLocationPreference
import org.koin.android.ext.android.inject
import java.io.File

class SettingsCacheFragment : SettingsBaseFragment() {
    private val prefs: Preferences.App by inject()

    private val cacheLocation by lazy { findPreference<CacheLocationPreference>("cacheLocation") }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_cache, rootKey)

        cacheLocation?.setOnPreferenceChangeListener { preference, any ->
            val path = any as String
            val dir: File = File(path)
            if (!FileUtil.verifyCanWrite(dir)) {
                context?.let { Util.toast(it, R.string.settings_cache_location_error, false) }

            /*  TODO  // Reset it to the default.
                val defaultPath = FileUtil.getDefaultMusicDirectory(context).path
                if (defaultPath != path) {
                    prefs.set(Constants.PREFERENCES_KEY_CACHE_LOCATION, defaultPath)
                    cacheLocation?.summary = defaultPath
                    cacheLocation?.text = defaultPath
                }*/

                // Clear download queue.
                DownloadService.getInstance().clear()
                false
            }
            else true
        }
    }
}