package app.logdate.mobile.di

import app.logdate.mobile.ui.AndroidBiometricGatekeeper
import app.logdate.mobile.ui.BiometricGatekeeper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@InstallIn(ViewModelComponent::class)
@Module
abstract class BiometricAuthModule {
    @Binds
    abstract fun provideBiometricGatekeeper(gatekeeper: AndroidBiometricGatekeeper): BiometricGatekeeper
}