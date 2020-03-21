package com.lipata.forkauthority.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.lipata.forkauthority.R
import com.lipata.forkauthority.util.AddressParser
import dagger.Module
import dagger.Provides
import timber.log.Timber

@Module
class AppModule(private val application: Application) {
    @Provides
    @ApplicationScope
    fun provideContext(): Context {
        return application
    }

    @Provides
    @ApplicationScope
    fun provideSharedPrefs(): SharedPreferences {
        return application.getSharedPreferences(
            application.getString(R.string.shared_prefs_file),
            Context.MODE_PRIVATE
        )
    }

    @Provides
    @ApplicationScope
    fun providesApplication(): Application {
        return application
    }

    @Provides
    @ApplicationScope
    fun providesAddressParser(): AddressParser {
        return AddressParser()
    }

    @Provides
    @ApplicationScope
    @JustAteHerePref
    fun provideJustAteHerePref(context: Context, sharedPreferences: SharedPreferences): Int {
        val default = context.getString(R.string.preference_default_value_just_ate_here_expiration)

        val expirationPrefString = sharedPreferences.getString(
            context.getString(R.string.preference_key_just_ate_here_expiration),
            default) ?: default

        return try {
            expirationPrefString.toInt()
        } catch (e: Exception) {
            Timber.e(e)
            default.toInt()
        }
    }
}
