package github.daneren2005.dsub.activity

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import github.daneren2005.dsub.MainApplication
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

abstract class BaseActivity :
    AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    KoinComponent  {

    /* Preferences */
    protected val prefsApp: Preferences.App by inject()

    protected open val disableAppTheme = false

    protected val app
        get() = application as MainApplication

    override fun onCreate(savedInstanceState: Bundle?) {
       /* if(!disableAppTheme)
            applyAppTheme(this)*/
        prefsApp.registerOnSharedPreferenceChangeListener(this)
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        prefsApp.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(/*key == getString(R.string.key_appearance_pure_black) ||*/
            key == getString(R.string.key_theme)) {
            if(!disableAppTheme)
                ActivityCompat.recreate(this)
        }
    }
}
