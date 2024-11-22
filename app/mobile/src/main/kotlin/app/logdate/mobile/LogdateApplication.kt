package app.logdate.mobile

import android.app.Application
import androidx.fragment.app.FragmentActivity
import app.logdate.mobile.ui.BiometricActivityProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LogdateApplication : Application(), BiometricActivityProvider {
//    val appComponent = DaggerApplicationComponent.create()

    override fun provideBiometricActivity(): FragmentActivity {
        TODO()
    }

    override fun onCreate() {
        super.onCreate()
    }
}
