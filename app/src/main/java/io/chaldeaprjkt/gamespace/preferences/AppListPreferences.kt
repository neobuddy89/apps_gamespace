/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.preferences

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import androidx.activity.result.ActivityResult
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.UserGame
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorActivity
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf


class AppListPreferences @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    PreferenceCategory(context, attrs), Preference.OnPreferenceClickListener {

    private val apps = mutableListOf<UserGame>()
    private val systemSettings by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().systemSettings()
    }

    init {
        isOrderingAsAdded = false
    }

    private val makeAddPref by lazy {
        Preference(context).apply {
            title = "Add"
            key = KEY_ADD_GAME
            setIcon(R.drawable.ic_add)
            isPersistent = false
            onPreferenceClickListener = this@AppListPreferences
        }
    }

    private fun getAppInfo(packageName: String): ApplicationInfo? = try {
        context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun updateAppList() {
        apps.clear()
        if (!systemSettings.userGames.isNullOrEmpty()) {
            apps.addAll(systemSettings.userGames)
        }
        removeAll()
        addPreference(makeAddPref)
        apps.filter { getAppInfo(it.packageName) != null }
            .map {
                val info = getAppInfo(it.packageName)
                Preference(context).apply {
                    key = it.packageName
                    title = info?.loadLabel(context.packageManager)
                    summary = describeMode(it.mode)
                    icon = info?.loadIcon(context.packageManager)
                    isPersistent = false
                    onPreferenceClickListener = this@AppListPreferences
                }
            }
            .sortedBy { it.title.toString().lowercase() }
            .forEach(::addPreference)
    }

    private fun describeMode(mode: Int): String {
        val title = context.getString(R.string.game_mode_title)
        val desc = GameModeUtils.describeMode(context, mode)
        return "$title: $desc"
    }

    private fun registerApp(packageName: String) {
        if (!apps.any { it.packageName == packageName }) {
            apps.add(UserGame(packageName))
        }
        systemSettings.userGames = apps
        GameModeUtils.setIntervention(packageName)
        updateAppList()
    }

    private fun unregisterApp(preference: Preference) {
        apps.removeIf { it.packageName == preference.key }
        systemSettings.userGames = apps
        GameModeUtils.setIntervention(preference.key, null)
        updateAppList()
    }

    override fun onAttached() {
        super.onAttached()
        updateAppList()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        if (preference != makeAddPref) {
            val message = context.getString(R.string.game_remove_message, preference.title)
            AlertDialog.Builder(context).setTitle(R.string.game_list_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    unregisterApp(preference)
                }
                .show()
        } else {
            parentActivity?.startActivity(Intent(context, AppSelectorActivity::class.java))
        }
        return true
    }

    fun useSelectorResult(result: ActivityResult?) {
        result?.takeIf { it.resultCode == Activity.RESULT_OK }
            ?.data?.getStringExtra(EXTRA_APP)
            ?.let { registerApp(it) }
    }

    private val parentActivity: Activity?
        get() {
            if (context is Activity)
                return context as Activity

            if (context is ContextThemeWrapper && (context as ContextThemeWrapper).baseContext is Activity)
                return (context as ContextThemeWrapper).baseContext as Activity
            return null
        }


    companion object {
        const val KEY_ADD_GAME = "add_game"
        const val EXTRA_APP = "selected_app"
    }
}
