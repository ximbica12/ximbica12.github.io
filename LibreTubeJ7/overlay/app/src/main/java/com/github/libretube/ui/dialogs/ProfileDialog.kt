package com.github.libretube.ui.dialogs

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
        val profiles = ProfileManager.getProfiles()
        val activeId = ProfileManager.getActiveProfileId()

        val labels = profiles.map { profile ->
            buildString {
                if (profile.id == activeId) append("✓ ")
                append(profile.name)
                profile.googleEmail?.let { append("\n").append(it) }
            }
        } + getString(R.string.new_profile)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profiles)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == profiles.size) {
                    showCreateProfileDialog()
                } else {
                    switchTo(profiles[which])
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun showCreateProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.profile_name)
            setSingleLine(true)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_profile)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                val profile = ProfileManager.createProfile(input.text?.toString().orEmpty())
                switchTo(profile)
            }
            .show()
    }

    private fun switchTo(profile: ProfileManager.Profile) {
        if (profile.id == ProfileManager.getActiveProfileId()) return

        DatabaseHolder.switchProfile(profile.id)
        PreferenceHelper.reloadAuthenticationPreferences(requireContext())

        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }
}
