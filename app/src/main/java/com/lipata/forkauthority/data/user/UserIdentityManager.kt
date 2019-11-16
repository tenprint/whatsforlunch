package com.lipata.forkauthority.data.user

import android.content.Context
import android.content.SharedPreferences
import com.lipata.forkauthority.poll.EmailDialog
import com.lipata.forkauthority.data.SharedPreferencesKeys.USER_EMAIL_KEY
import javax.inject.Inject


class UserIdentityManager @Inject constructor(val sharedPreferences: SharedPreferences,
                                              val emailDialog: EmailDialog) {

    val email: String?
        get() = sharedPreferences.getString(USER_EMAIL_KEY, null)


    fun hasIdentity(): Boolean {
        val userEmail = email
        return userEmail != null && userEmail.isNotBlank()
    }

    fun checkUserIdentity(context: Context) {
        if (!hasIdentity()) {
            promptUserForEmail(context)
        }
    }

    fun promptUserForEmail(context: Context) {
        // show UI
        emailDialog.showEmailPrompt(context,
            object : EmailDialog.Listener {
                override fun onSubmit(text: String) {
                    sharedPreferences.edit().putString(USER_EMAIL_KEY, text).apply()
                }
            }
        )
    }


}


