package com.newtermux.features

import android.content.Context
import android.content.SharedPreferences

class NewTermuxSettings {

    companion object {
        private const val PREFS_NAME = "newtermux_settings"

        const val KEY_KEYBOARD_SUGGESTIONS = "keyboard_suggestions"
        const val KEY_SHOW_AC_BUTTON = "show_ac_button"
        const val KEY_SHOW_ROOT_BUTTON = "show_root_button"
        const val KEY_SHOW_STT_BUTTON = "show_stt_button"
        const val KEY_SHOW_PACKAGES_BUTTON = "show_packages_button"
        const val KEY_SHOW_CLEAR_BUTTON = "show_clear_button"
        const val KEY_ZSH_PLUGINS = "zsh_plugins"
        const val KEY_SESSION_TABS = "session_tabs"
        const val KEY_AUTOCORRECT = "autocorrect_enabled"
        const val KEY_SHOW_DRAWER_EXPORT_SCRIPT = "show_drawer_export_script"
        const val KEY_SHOW_DRAWER_PKG_UPDATE = "show_drawer_pkg_update"
        const val KEY_SHOW_DRAWER_CMD_BUTTONS = "show_drawer_cmd_buttons"
        const val KEY_STARTUP_SCRIPT_ENABLED = "startup_script_enabled"
        const val KEY_URL_DETECTION_ENABLED = "url_detection_enabled"
        const val KEY_SESSION_RENAME_ENABLED = "session_rename_enabled"
        const val KEY_TEXT_EXPANSION_ENABLED = "text_expansion_enabled"
        const val KEY_EXTRA_KEYS_VISIBLE = "extra_keys_visible"
        const val KEY_EXTRA_KEYS_IN_DRAWER = "extra_keys_in_drawer"

        private fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        @JvmStatic
        fun isKeyboardSuggestionsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_KEYBOARD_SUGGESTIONS, false)
        @JvmStatic
        fun setKeyboardSuggestions(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_KEYBOARD_SUGGESTIONS, v).apply() }

        @JvmStatic
        fun isShowAcButton(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_AC_BUTTON, true)
        @JvmStatic
        fun isShowRootButton(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_ROOT_BUTTON, true)
        @JvmStatic
        fun isShowSttButton(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_STT_BUTTON, true)
        @JvmStatic
        fun isShowPackagesButton(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_PACKAGES_BUTTON, true)
        @JvmStatic
        fun isShowClearButton(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_CLEAR_BUTTON, true)
        @JvmStatic
        fun isZshPluginsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ZSH_PLUGINS, false)
        @JvmStatic
        fun isSessionTabsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SESSION_TABS, true)
        @JvmStatic
        fun isAutocorrectEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTOCORRECT, true)
        @JvmStatic
        fun isShowDrawerExportScript(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_DRAWER_EXPORT_SCRIPT, true)
        @JvmStatic
        fun isShowDrawerPkgUpdate(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_DRAWER_PKG_UPDATE, true)
        @JvmStatic
        fun isShowDrawerCmdButtons(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_DRAWER_CMD_BUTTONS, true)
        @JvmStatic
        fun isStartupScriptEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_STARTUP_SCRIPT_ENABLED, false)
        @JvmStatic
        fun isUrlDetectionEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_URL_DETECTION_ENABLED, true)
        @JvmStatic
        fun isSessionRenameEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SESSION_RENAME_ENABLED, true)
        @JvmStatic
        fun isTextExpansionEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TEXT_EXPANSION_ENABLED, false)
        @JvmStatic
        fun isExtraKeysVisible(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_EXTRA_KEYS_VISIBLE, true)
        @JvmStatic
        fun isExtraKeysInDrawer(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_EXTRA_KEYS_IN_DRAWER, false)

        // Pending command
        @JvmStatic
        fun getPendingCommand(ctx: Context): String? = prefs(ctx).getString("pending_command", null)
        @JvmStatic
        fun setPendingCommand(ctx: Context, cmd: String) { prefs(ctx).edit().putString("pending_command", cmd).apply() }
        @JvmStatic
        fun clearPendingCommand(ctx: Context) { prefs(ctx).edit().remove("pending_command").apply() }

        // Generic setter for all boolean keys
        @JvmStatic
        fun set(ctx: Context, key: String, value: Boolean) { prefs(ctx).edit().putBoolean(key, value).apply() }

        // Generic getter with per-key defaults
        @JvmStatic
        fun get(ctx: Context, key: String): Boolean = when (key) {
            KEY_KEYBOARD_SUGGESTIONS -> isKeyboardSuggestionsEnabled(ctx)
            KEY_SHOW_AC_BUTTON -> isShowAcButton(ctx)
            KEY_SHOW_ROOT_BUTTON -> isShowRootButton(ctx)
            KEY_SHOW_STT_BUTTON -> isShowSttButton(ctx)
            KEY_SHOW_PACKAGES_BUTTON -> isShowPackagesButton(ctx)
            KEY_SHOW_CLEAR_BUTTON -> isShowClearButton(ctx)
            KEY_ZSH_PLUGINS -> isZshPluginsEnabled(ctx)
            KEY_SESSION_TABS -> isSessionTabsEnabled(ctx)
            KEY_AUTOCORRECT -> isAutocorrectEnabled(ctx)
            KEY_SHOW_DRAWER_EXPORT_SCRIPT -> isShowDrawerExportScript(ctx)
            KEY_SHOW_DRAWER_PKG_UPDATE -> isShowDrawerPkgUpdate(ctx)
            KEY_SHOW_DRAWER_CMD_BUTTONS -> isShowDrawerCmdButtons(ctx)
            KEY_STARTUP_SCRIPT_ENABLED -> isStartupScriptEnabled(ctx)
            KEY_URL_DETECTION_ENABLED -> isUrlDetectionEnabled(ctx)
            KEY_SESSION_RENAME_ENABLED -> isSessionRenameEnabled(ctx)
            KEY_TEXT_EXPANSION_ENABLED -> isTextExpansionEnabled(ctx)
            KEY_EXTRA_KEYS_VISIBLE -> isExtraKeysVisible(ctx)
            KEY_EXTRA_KEYS_IN_DRAWER -> isExtraKeysInDrawer(ctx)
            else -> prefs(ctx).getBoolean(key, false)
        }
    }
}
