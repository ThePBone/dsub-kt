package github.daneren2005.dsub

import android.app.Application
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import github.daneren2005.dsub.model.ThemeMode
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class MainApplication : Application(), SharedPreferences.OnSharedPreferenceChangeListener, KoinComponent {
    private val prefs: Preferences.App by inject()

    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        Timber.i("====> Application starting up")


        val appModule = module {
            single { Preferences(androidContext()).App() }
        }

        startKoin {
            androidLogger()
            androidContext(this@MainApplication)
            modules(appModule)
        }


        onSharedPreferenceChanged(prefs.preferences, getString(R.string.key_theme))
        prefs.registerOnSharedPreferenceChangeListener(this)
        super.onCreate()
    }

    override fun onTerminate() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onTerminate()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key == getString(R.string.key_theme)) {
            AppCompatDelegate.setDefaultNightMode(
                when (ThemeMode.fromInt(prefs.get<String>(R.string.key_theme).toIntOrNull() ?: 0)) {
                    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

}