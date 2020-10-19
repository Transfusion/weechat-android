package com.ubergeek42.WeechatAndroid.upload

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.utils.Constants.*


object Config {
    var uploadUri = PREF_UPLOADING_URI_D
    var uploadFormFieldName = PREF_UPLOADING_FORM_FIELD_NAME
    var httpUriGetter = HttpUriGetter.simple
    var requestModifiers = emptyList<RequestModifier>()
    var cacheMaxAge = PREF_UPLOADING_CACHE_MAX_AGE_D.hours_to_ms
}

fun initPreferences() {
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    for (key in listOf(PREF_UPLOADING_ACCEPT_SHARED,
                       PREF_UPLOADING_URI,
                       PREF_UPLOADING_FORM_FIELD_NAME,
                       PREF_UPLOADING_REGEX,
                       PREF_UPLOADING_ADDITIONAL_HEADERS,
                       PREF_UPLOADING_AUTHENTICATION,
                       PREF_UPLOADING_AUTHENTICATION_BASIC_USER,
                       PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD,
                       PREF_UPLOADING_CACHE_MAX_AGE,
    )) {
        onSharedPreferenceChanged(p, key)
    }
}

@Suppress("BooleanLiteralArgument")
fun onSharedPreferenceChanged(p: SharedPreferences, key: String) {
    when (key) {
        PREF_UPLOADING_ACCEPT_SHARED -> {
            val (text, media, everything) = when (p.getString(key, PREF_UPLOADING_ACCEPT_SHARED_D)) {
                PREF_UPLOADING_ACCEPT_SHARED_TEXT_ONLY ->      true to false to false
                PREF_UPLOADING_ACCEPT_SHARED_TEXT_AND_MEDIA -> false to true to false
                PREF_UPLOADING_ACCEPT_SHARED_EVERYTHING ->     false to false to true
                else -> true to false to false
            }
            enableDisableComponent(ShareActivityAliases.TEXT_ONLY.alias, text)
            enableDisableComponent(ShareActivityAliases.TEXT_AND_MEDIA.alias, media)
            enableDisableComponent(ShareActivityAliases.EVERYTHING.alias, everything)
        }

        PREF_UPLOADING_URI -> {
            Config.uploadUri = p.getString(key, PREF_UPLOADING_URI_D)!!
        }

        PREF_UPLOADING_FORM_FIELD_NAME -> {
            Config.uploadFormFieldName = p.getString(key, PREF_UPLOADING_FORM_FIELD_NAME_D)!!
        }

        PREF_UPLOADING_REGEX -> {
            val regex = p.getString(key, PREF_UPLOADING_REGEX_D)!!
            Config.httpUriGetter = if (regex.isEmpty()) {
                HttpUriGetter.simple
            } else {
                HttpUriGetter.fromRegex(regex)
            }
        }

        PREF_UPLOADING_ADDITIONAL_HEADERS,
        PREF_UPLOADING_AUTHENTICATION,
        PREF_UPLOADING_AUTHENTICATION_BASIC_USER,
        PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD -> {
            val requestModifiers = mutableListOf<RequestModifier>()

            val additionalHeaders = RequestModifier.additionalHeaders(
                    p.getString(PREF_UPLOADING_ADDITIONAL_HEADERS, PREF_UPLOADING_ADDITIONAL_HEADERS_D)!!)
            if (additionalHeaders != null)
                requestModifiers.add(additionalHeaders)

            if (p.getString(PREF_UPLOADING_AUTHENTICATION, PREF_UPLOADING_AUTHENTICATION_D) == PREF_UPLOADING_AUTHENTICATION_BASIC) {
                val user = p.getString(PREF_UPLOADING_AUTHENTICATION_BASIC_USER, PREF_UPLOADING_AUTHENTICATION_BASIC_USER_D)!!
                val password = p.getString(PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD, PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD_D)!!
                requestModifiers.add(RequestModifier.basicAuthentication(user, password))
            }

            Config.requestModifiers = requestModifiers
        }

        PREF_UPLOADING_CACHE_MAX_AGE -> {
            suppress<NumberFormatException> {
                Config.cacheMaxAge = p.getString(key, PREF_UPLOADING_CACHE_MAX_AGE_D)!!.hours_to_ms
            }
        }
    }
}

private enum class ShareActivityAliases(val alias: String) {
    TEXT_ONLY("ShareActivityText"),
    TEXT_AND_MEDIA("ShareActivityMedia"),
    EVERYTHING("ShareActivityEverything"),
}

private fun enableDisableComponent(name: String, enabled: Boolean) {
    val manager = applicationContext.packageManager
    val componentName = ComponentName(BuildConfig.APPLICATION_ID, "com.ubergeek42.WeechatAndroid.$name")
    val enabledDisabled = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    manager.setComponentEnabledSetting(componentName, enabledDisabled, PackageManager.DONT_KILL_APP)
}

private val String.hours_to_ms get() = (this.toFloat() * 60 * 60 * 1000).toInt()