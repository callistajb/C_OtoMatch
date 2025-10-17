package com.example.c_otomatch.utils

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val PREFS_NAME = "otomatch_prefs"

    // Key untuk data user
    private const val KEY_IS_LOGGED = "is_logged"
    private const val KEY_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_PHONE = "user_phone"
    private const val KEY_LOCATION = "user_location"
    private const val KEY_PROFILE_URI = "user_profile_uri"
    private const val KEY_PASSWORD = "user_password"
    private const val KEY_RATING = "user_rating"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** --- LOGIN STATUS --- */
    fun setLoggedIn(ctx: Context, name: String, email: String? = null) {
        prefs(ctx).edit()
            .putBoolean(KEY_IS_LOGGED, true)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun logout(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun isLoggedIn(ctx: Context) = prefs(ctx).getBoolean(KEY_IS_LOGGED, false)

    /** --- BASIC USER INFO --- */
    fun getName(ctx: Context) = prefs(ctx).getString(KEY_NAME, "") ?: ""
    fun setName(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_NAME, value).apply()

    fun getEmail(ctx: Context) = prefs(ctx).getString(KEY_EMAIL, "") ?: ""
    fun setEmail(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_EMAIL, value).apply()

    fun getPhone(ctx: Context) = prefs(ctx).getString(KEY_PHONE, "") ?: ""
    fun setPhone(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_PHONE, value).apply()

    fun getLocation(ctx: Context) = prefs(ctx).getString(KEY_LOCATION, "") ?: ""
    fun setLocation(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_LOCATION, value).apply()

    fun getProfileImageUri(ctx: Context): String? {
        val uri = prefs(ctx).getString(KEY_PROFILE_URI, null)
        return if (uri.isNullOrBlank()) null else uri
    }

    fun setProfileImageUri(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_PROFILE_URI, value).apply()

    /** --- PASSWORD --- */
    fun getPassword(ctx: Context) = prefs(ctx).getString(KEY_PASSWORD, "") ?: ""
    fun setPassword(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_PASSWORD, value).apply()

    /** --- RATING --- */
    fun getRating(ctx: Context): Float = prefs(ctx).getFloat(KEY_RATING, 4.7f) // default rating
    fun setRating(ctx: Context, value: Float) = prefs(ctx).edit().putFloat(KEY_RATING, value).apply()
}
