package com.github.libretube.helpers

import com.github.libretube.LibreTubeApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ProfileManager {
    const val DEFAULT_PROFILE_ID = "default"
    private const val PREFS = "tubelite_profiles"
    private const val KEY_ACTIVE = "active_profile"
    private const val KEY_PROFILES = "profiles"

    data class Profile(
        val id: String,
        val name: String,
        val googleEmail: String? = null
    )

    private val prefs
        get() = LibreTubeApp.instance.getSharedPreferences(PREFS, 0)

    fun getProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) {
            val initial = listOf(Profile(DEFAULT_PROFILE_ID, "Principal"))
            saveProfiles(initial)
            return initial
        }

        val parsed = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Profile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            googleEmail = item.optString("googleEmail")
                                .takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        if (parsed.isEmpty()) {
            val initial = listOf(Profile(DEFAULT_PROFILE_ID, "Principal"))
            saveProfiles(initial)
            return initial
        }
        return parsed
    }

    fun getActiveProfileId(): String =
        prefs.getString(KEY_ACTIVE, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID

    fun getActiveProfile(): Profile =
        getProfiles().firstOrNull { it.id == getActiveProfileId() }
            ?: getProfiles().first()

    fun setActiveProfile(profileId: String) {
        require(getProfiles().any { it.id == profileId })
        prefs.edit().putString(KEY_ACTIVE, profileId).apply()
    }

    fun createProfile(name: String, googleEmail: String? = null): Profile {
        val cleanName = name.trim().ifBlank { "Perfil" }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            googleEmail = googleEmail
        )
        saveProfiles(getProfiles() + profile)
        return profile
    }

    fun ensureProfileForGoogleAccount(email: String): Profile {
        val normalized = email.trim()
        getProfiles().firstOrNull {
            it.googleEmail.equals(normalized, ignoreCase = true)
        }?.let { return it }

        val active = getActiveProfile()
        if (active.googleEmail.isNullOrBlank()) {
            val updated = active.copy(googleEmail = normalized)
            replace(updated)
            return updated
        }

        return createProfile(
            name = normalized.substringBefore("@").ifBlank { "YouTube" },
            googleEmail = normalized
        )
    }

    fun renameProfile(profileId: String, name: String) {
        val current = getProfiles().firstOrNull { it.id == profileId } ?: return
        replace(current.copy(name = name.trim().ifBlank { current.name }))
    }

    private fun replace(profile: Profile) {
        saveProfiles(getProfiles().map { if (it.id == profile.id) profile else it })
    }

    private fun saveProfiles(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("googleEmail", profile.googleEmail ?: "")
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }
}
