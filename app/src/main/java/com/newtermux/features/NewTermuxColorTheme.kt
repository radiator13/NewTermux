package com.newtermux.features

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.FileWriter
import java.io.IOException

/**
 * Manages terminal color themes for NewTermux.
 * Themes are written to ~/.termux/colors.properties and reloaded by the terminal emulator.
 */
class NewTermuxColorTheme {

    companion object {
        private const val PREFS_NAME = "newtermux_color_theme"
        private const val KEY_THEME = "selected_theme"
        const val DEFAULT_THEME = "default_dark"

        const val THEME_KEY_CUSTOM = "custom"

        @JvmField
        val THEME_KEYS = arrayOf(
            "default_dark", "oled_black", "dracula", "nord", "monokai",
            "gruvbox_dark", "one_dark", "catppuccin_mocha", "tokyo_night",
            "solarized_dark", "rgui", "amber", "low_contrast", "custom"
        )

        @JvmField
        val THEME_NAMES = arrayOf(
            "Default Dark", "OLED Black", "Dracula", "Nord", "Monokai",
            "Gruvbox Dark", "One Dark", "Catppuccin", "Tokyo Night",
            "Solarized Dark", "RGUI", "Amber", "Low Contrast"
        )

        private const val THEME_DEFAULT_DARK =
            "background=#000000\n" +
            "foreground=#ffffff\n" +
            "cursor=#ffffff\n" +
            "color0=#000000\n" +
            "color1=#cc0000\n" +
            "color2=#4e9a06\n" +
            "color3=#c4a000\n" +
            "color4=#3465a4\n" +
            "color5=#75507b\n" +
            "color6=#06989a\n" +
            "color7=#d3d7cf\n" +
            "color8=#555753\n" +
            "color9=#ef2929\n" +
            "color10=#8ae234\n" +
            "color11=#fce94f\n" +
            "color12=#729fcf\n" +
            "color13=#ad7fa8\n" +
            "color14=#34e2e2\n" +
            "color15=#eeeeec\n"

        private const val THEME_OLED_BLACK =
            "background=#000000\n" +
            "foreground=#ffffff\n" +
            "cursor=#ffffff\n" +
            "color0=#000000\n" +
            "color1=#ff5555\n" +
            "color2=#55ff55\n" +
            "color3=#ffff55\n" +
            "color4=#5555ff\n" +
            "color5=#ff55ff\n" +
            "color6=#55ffff\n" +
            "color7=#cccccc\n" +
            "color8=#555555\n" +
            "color9=#ff7777\n" +
            "color10=#77ff77\n" +
            "color11=#ffff77\n" +
            "color12=#7777ff\n" +
            "color13=#ff77ff\n" +
            "color14=#77ffff\n" +
            "color15=#ffffff\n"

        private const val THEME_DRACULA =
            "background=#282a36\n" +
            "foreground=#f8f8f2\n" +
            "cursor=#f8f8f2\n" +
            "color0=#21222c\n" +
            "color1=#ff5555\n" +
            "color2=#50fa7b\n" +
            "color3=#f1fa8c\n" +
            "color4=#bd93f9\n" +
            "color5=#ff79c6\n" +
            "color6=#8be9fd\n" +
            "color7=#f8f8f2\n" +
            "color8=#6272a4\n" +
            "color9=#ff6e6e\n" +
            "color10=#69ff94\n" +
            "color11=#ffffa5\n" +
            "color12=#d6acff\n" +
            "color13=#ff92df\n" +
            "color14=#a4ffff\n" +
            "color15=#ffffff\n"

        private const val THEME_NORD =
            "background=#2e3440\n" +
            "foreground=#d8dee9\n" +
            "cursor=#d8dee9\n" +
            "color0=#3b4252\n" +
            "color1=#bf616a\n" +
            "color2=#a3be8c\n" +
            "color3=#ebcb8b\n" +
            "color4=#81a1c1\n" +
            "color5=#b48ead\n" +
            "color6=#88c0d0\n" +
            "color7=#e5e9f0\n" +
            "color8=#4c566a\n" +
            "color9=#bf616a\n" +
            "color10=#a3be8c\n" +
            "color11=#ebcb8b\n" +
            "color12=#81a1c1\n" +
            "color13=#b48ead\n" +
            "color14=#8fbcbb\n" +
            "color15=#eceff4\n"

        private const val THEME_MONOKAI =
            "background=#272822\n" +
            "foreground=#f8f8f2\n" +
            "cursor=#f8f8f0\n" +
            "color0=#272822\n" +
            "color1=#f92672\n" +
            "color2=#a6e22e\n" +
            "color3=#f4bf75\n" +
            "color4=#66d9e8\n" +
            "color5=#ae81ff\n" +
            "color6=#a1efe4\n" +
            "color7=#f8f8f2\n" +
            "color8=#75715e\n" +
            "color9=#f92672\n" +
            "color10=#a6e22e\n" +
            "color11=#f4bf75\n" +
            "color12=#66d9e8\n" +
            "color13=#ae81ff\n" +
            "color14=#a1efe4\n" +
            "color15=#f9f8f5\n"

        private const val THEME_GRUVBOX_DARK =
            "background=#282828\n" +
            "foreground=#ebdbb2\n" +
            "cursor=#ebdbb2\n" +
            "color0=#282828\n" +
            "color1=#cc241d\n" +
            "color2=#98971a\n" +
            "color3=#d79921\n" +
            "color4=#458588\n" +
            "color5=#b16286\n" +
            "color6=#689d6a\n" +
            "color7=#a89984\n" +
            "color8=#928374\n" +
            "color9=#fb4934\n" +
            "color10=#b8bb26\n" +
            "color11=#fabd2f\n" +
            "color12=#83a598\n" +
            "color13=#d3869b\n" +
            "color14=#8ec07c\n" +
            "color15=#ebdbb2\n"

        private const val THEME_ONE_DARK =
            "background=#282c34\n" +
            "foreground=#abb2bf\n" +
            "cursor=#528bff\n" +
            "color0=#282c34\n" +
            "color1=#e06c75\n" +
            "color2=#98c379\n" +
            "color3=#e5c07b\n" +
            "color4=#61afef\n" +
            "color5=#c678dd\n" +
            "color6=#56b6c2\n" +
            "color7=#abb2bf\n" +
            "color8=#545862\n" +
            "color9=#e06c75\n" +
            "color10=#98c379\n" +
            "color11=#e5c07b\n" +
            "color12=#61afef\n" +
            "color13=#c678dd\n" +
            "color14=#56b6c2\n" +
            "color15=#c8cdd4\n"

        private const val THEME_CATPPUCCIN_MOCHA =
            "background=#1e1e2e\n" +
            "foreground=#cdd6f4\n" +
            "cursor=#f5e0dc\n" +
            "color0=#45475a\n" +
            "color1=#f38ba8\n" +
            "color2=#a6e3a1\n" +
            "color3=#f9e2af\n" +
            "color4=#89b4fa\n" +
            "color5=#f5c2e7\n" +
            "color6=#94e2d5\n" +
            "color7=#bac2de\n" +
            "color8=#585b70\n" +
            "color9=#f38ba8\n" +
            "color10=#a6e3a1\n" +
            "color11=#f9e2af\n" +
            "color12=#89b4fa\n" +
            "color13=#f5c2e7\n" +
            "color14=#94e2d5\n" +
            "color15=#a6adc8\n"

        private const val THEME_TOKYO_NIGHT =
            "background=#1a1b26\n" +
            "foreground=#c0caf5\n" +
            "cursor=#c0caf5\n" +
            "color0=#15161e\n" +
            "color1=#f7768e\n" +
            "color2=#9ece6a\n" +
            "color3=#e0af68\n" +
            "color4=#7aa2f7\n" +
            "color5=#bb9af7\n" +
            "color6=#7dcfff\n" +
            "color7=#a9b1d6\n" +
            "color8=#414868\n" +
            "color9=#f7768e\n" +
            "color10=#9ece6a\n" +
            "color11=#e0af68\n" +
            "color12=#7aa2f7\n" +
            "color13=#bb9af7\n" +
            "color14=#7dcfff\n" +
            "color15=#c0caf5\n"

        private const val THEME_SOLARIZED_DARK =
            "background=#002b36\n" +
            "foreground=#839496\n" +
            "cursor=#2aa198\n" +
            "color0=#073642\n" +
            "color1=#dc322f\n" +
            "color2=#859900\n" +
            "color3=#b58900\n" +
            "color4=#268bd2\n" +
            "color5=#d33682\n" +
            "color6=#2aa198\n" +
            "color7=#eee8d5\n" +
            "color8=#002b36\n" +
            "color9=#cb4b16\n" +
            "color10=#586e75\n" +
            "color11=#657b83\n" +
            "color12=#839496\n" +
            "color13=#6c71c4\n" +
            "color14=#93a1a1\n" +
            "color15=#fdf6e3\n"

        private const val THEME_RGUI =
            "background=#1a1422\n" +
            "foreground=#c8c8c8\n" +
            "cursor=#c0a0ff\n" +
            "color0=#1a1422\n" +
            "color1=#d44942\n" +
            "color2=#5a9e4e\n" +
            "color3=#c99a2e\n" +
            "color4=#4e82b4\n" +
            "color5=#9264b4\n" +
            "color6=#4e9e9e\n" +
            "color7=#c8c8c8\n" +
            "color8=#3a3050\n" +
            "color9=#e06060\n" +
            "color10=#70c060\n" +
            "color11=#e0c060\n" +
            "color12=#70a0d0\n" +
            "color13=#c080e0\n" +
            "color14=#70c0c0\n" +
            "color15=#e8e8e8\n"

        private const val THEME_AMBER =
            "background=#1a0f00\n" +
            "foreground=#ffb347\n" +
            "cursor=#ff8c00\n" +
            "color0=#1a0f00\n" +
            "color1=#cc4400\n" +
            "color2=#887722\n" +
            "color3=#cc8800\n" +
            "color4=#885500\n" +
            "color5=#aa6622\n" +
            "color6=#bb7722\n" +
            "color7=#ffb347\n" +
            "color8=#3a2000\n" +
            "color9=#ff6600\n" +
            "color10=#bbaa00\n" +
            "color11=#ffcc00\n" +
            "color12=#cc8800\n" +
            "color13=#dd9944\n" +
            "color14=#ddbb00\n" +
            "color15=#ffcc88\n"

        private const val THEME_LOW_CONTRAST =
            "background=#1e2030\n" +
            "foreground=#808898\n" +
            "cursor=#4a5568\n" +
            "color0=#1e2030\n" +
            "color1=#805060\n" +
            "color2=#507060\n" +
            "color3=#706040\n" +
            "color4=#4060a0\n" +
            "color5=#705080\n" +
            "color6=#407080\n" +
            "color7=#808898\n" +
            "color8=#2a3040\n" +
            "color9=#906070\n" +
            "color10=#608070\n" +
            "color11=#807050\n" +
            "color12=#5070b0\n" +
            "color13=#806090\n" +
            "color14=#508090\n" +
            "color15=#a0a8b8\n"

        @JvmStatic
        fun getCurrentTheme(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, DEFAULT_THEME)!!
        }

        @JvmStatic
        fun applyTheme(context: Context, themeKey: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, themeKey).apply()
            writeColorsFile(themeKey)
        }

        @JvmStatic
        fun getThemeName(key: String): String {
            for (i in THEME_KEYS.indices) {
                if (THEME_KEYS[i] == key) return THEME_NAMES[i]
            }
            return key
        }

        /** Returns {bg, fg, toolbar, green, cursor} for drawing a preview card. */
        @JvmStatic
        fun getPreviewColors(key: String): IntArray = when (key) {
            "oled_black"       -> intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF55FF55.toInt(), 0xFFFFFFFF.toInt())
            "dracula"          -> intArrayOf(0xFF282a36.toInt(), 0xFFf8f8f2.toInt(), 0xFF21222c.toInt(), 0xFF50fa7b.toInt(), 0xFFf8f8f2.toInt())
            "nord"             -> intArrayOf(0xFF2e3440.toInt(), 0xFFd8dee9.toInt(), 0xFF242930.toInt(), 0xFFa3be8c.toInt(), 0xFFd8dee9.toInt())
            "monokai"          -> intArrayOf(0xFF272822.toInt(), 0xFFf8f8f2.toInt(), 0xFF1e1f1c.toInt(), 0xFFa6e22e.toInt(), 0xFFf8f8f0.toInt())
            "gruvbox_dark"     -> intArrayOf(0xFF282828.toInt(), 0xFFebdbb2.toInt(), 0xFF1d2021.toInt(), 0xFFb8bb26.toInt(), 0xFFebdbb2.toInt())
            "one_dark"         -> intArrayOf(0xFF282c34.toInt(), 0xFFabb2bf.toInt(), 0xFF21252d.toInt(), 0xFF98c379.toInt(), 0xFF528bff.toInt())
            "catppuccin_mocha" -> intArrayOf(0xFF1e1e2e.toInt(), 0xFFcdd6f4.toInt(), 0xFF181825.toInt(), 0xFFa6e3a1.toInt(), 0xFFf5e0dc.toInt())
            "tokyo_night"      -> intArrayOf(0xFF1a1b26.toInt(), 0xFFc0caf5.toInt(), 0xFF13141f.toInt(), 0xFF9ece6a.toInt(), 0xFFc0caf5.toInt())
            "solarized_dark"   -> intArrayOf(0xFF002b36.toInt(), 0xFF839496.toInt(), 0xFF00212b.toInt(), 0xFF859900.toInt(), 0xFF2aa198.toInt())
            "rgui"             -> intArrayOf(0xFF1a1422.toInt(), 0xFFc8c8c8.toInt(), 0xFF0e0c16.toInt(), 0xFF5a9e4e.toInt(), 0xFFc0a0ff.toInt())
            "amber"            -> intArrayOf(0xFF1a0f00.toInt(), 0xFFffb347.toInt(), 0xFF0f0800.toInt(), 0xFFbbaa00.toInt(), 0xFFff8c00.toInt())
            "low_contrast"     -> intArrayOf(0xFF1e2030.toInt(), 0xFF808898.toInt(), 0xFF161825.toInt(), 0xFF507060.toInt(), 0xFF4a5568.toInt())
            else               -> intArrayOf(0xFF000000.toInt(), 0xFFffffff.toInt(), 0xFF111111.toInt(), 0xFF4e9a06.toInt(), 0xFFffffff.toInt())
        }

        private fun writeColorsFile(themeKey: String) {
            val content = getThemeContent(themeKey)
            val dir = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            try {
                FileWriter(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE).use { it.write(content) }
            } catch (_: IOException) {}
        }

        private fun getThemeContent(key: String): String = when (key) {
            "oled_black"       -> THEME_OLED_BLACK
            "dracula"          -> THEME_DRACULA
            "nord"             -> THEME_NORD
            "monokai"          -> THEME_MONOKAI
            "gruvbox_dark"     -> THEME_GRUVBOX_DARK
            "one_dark"         -> THEME_ONE_DARK
            "catppuccin_mocha" -> THEME_CATPPUCCIN_MOCHA
            "tokyo_night"      -> THEME_TOKYO_NIGHT
            "rgui"             -> THEME_RGUI
            "amber"            -> THEME_AMBER
            "low_contrast"     -> THEME_LOW_CONTRAST
            "solarized_dark"   -> THEME_SOLARIZED_DARK
            else               -> THEME_DEFAULT_DARK
        }

        @JvmStatic
        fun applyCustomTheme(ctx: Context, content: String) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("custom_theme_content", content)
                .putString(KEY_THEME, THEME_KEY_CUSTOM)
                .apply()
            writeColorsFileRaw(content)
        }

        @JvmStatic
        fun getCustomThemeContent(ctx: Context): String {
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("custom_theme_content", THEME_DEFAULT_DARK)!!
        }

        @JvmStatic
        internal fun writeColorsFileRaw(content: String) {
            val dir = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            try {
                FileWriter(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE).use { it.write(content) }
            } catch (_: IOException) {}
        }
    }
}
