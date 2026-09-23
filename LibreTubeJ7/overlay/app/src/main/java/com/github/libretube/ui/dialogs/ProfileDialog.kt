package com.github.libretube.ui.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.github.libretube.R
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.ProfileManager
import com.github.libretube.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Capture the host Activity while the DialogFragment is definitely attached.
        // Item dialogs dismiss themselves before the click callback chain is fully done,
        // so calling requireContext()/requireActivity() later can crash on Oreo.
        val host = requireActivity()
        val profiles = ProfileManager.getProfiles()
        val activeId = ProfileManager.getActiveProfileId()

        val labels = profiles.map { profile ->
            buildString {
                if (profile.id == activeId) append("✓ ")
                append(profile.name)
                profile.googleEmail?.let { append("\n").append(it) }
            }
        } + getString(R.string.new_profile)

        return MaterialAlertDialogBuilder(host)
            .setTitle(R.string.profiles)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == profiles.size) {
                    showCreateProfileDialog(host)
                } else {
                    switchTo(profiles[which], host)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun showCreateProfileDialog(host: Activity) {
        if (host.isFinishing || host.isDestroyed) return

        val input = EditText(host).apply {
            hint = host.getString(R.string.profile_name)
            setSingleLine(true)
        }

        MaterialAlertDialogBuilder(host)
            .setTitle(R.string.new_profile)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.profile_create) { _, _ ->
                if (host.isFinishing || host.isDestroyed) return@setPositiveButton

                val profile = ProfileManager.createProfile(input.text?.toString().orEmpty())
                switchTo(profile, host)
            }
            .show()
    }

    private fun switchTo(profile: ProfileManager.Profile, host: Activity) {
        if (profile.id == ProfileManager.getActiveProfileId()) return
        if (host.isFinishing || host.isDestroyed) return

        // None of the operations below depends on this DialogFragment remaining attached.
        DatabaseHolder.switchProfile(profile.id)
        PreferenceHelper.reloadAuthenticationPreferences(host.applicationContext)

        dismissAllowingStateLoss()

        val intent = Intent(host, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        host.startActivity(intent)
        host.finish()
    }
}
