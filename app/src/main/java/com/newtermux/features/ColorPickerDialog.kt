package com.newtermux.features

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.termux.R

/**
 * Builder-style reusable color picker dialog.
 * Supports HSV color wheel and RGB sliders.
 * Remembers last picker style in SharedPreferences ("newtermux_theme" / "color_picker_style").
 */
class ColorPickerDialog(private val mContext: Context) {

    interface OnColorSelectedListener {
        fun onColorSelected(color: Int)
    }

    companion object {
        private const val PREF_STYLE_KEY = "color_picker_style"
        private const val STYLE_HSV = "hsv"
        private const val STYLE_RGB = "rgb"

        private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
    }

    private var mInitialColor: Int = Color.WHITE
    private var mListener: OnColorSelectedListener? = null

    fun setInitialColor(color: Int): ColorPickerDialog {
        mInitialColor = color
        return this
    }

    fun setOnColorSelectedListener(listener: OnColorSelectedListener?): ColorPickerDialog {
        mListener = listener
        return this
    }

    fun show() {
        val lastStyle = mContext.getSharedPreferences("newtermux_theme", Context.MODE_PRIVATE)
            .getString(PREF_STYLE_KEY, STYLE_HSV)
        val items = arrayOf("Color Wheel (HSV)", "RGB Sliders")
        val defaultItem = if (STYLE_HSV == lastStyle) 0 else 1

        val chooser = AlertDialog.Builder(mContext)
            .setTitle("Choose Picker Style")
            .setSingleChoiceItems(items, defaultItem, null)
            .setPositiveButton("Next", null)
            .setNegativeButton("Cancel", null)
            .create()

        chooser.setOnShowListener {
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var selected = chooser.listView.checkedItemPosition
                if (selected < 0) selected = defaultItem
                val style = if (selected == 0) STYLE_HSV else STYLE_RGB
                mContext.getSharedPreferences("newtermux_theme", Context.MODE_PRIVATE)
                    .edit().putString(PREF_STYLE_KEY, style).apply()
                chooser.dismiss()
                if (STYLE_HSV == style) {
                    showHsvPicker()
                } else {
                    showRgbPicker()
                }
            }
        }
        chooser.show()
    }

    private fun showHsvPicker() {
        val view = LayoutInflater.from(mContext).inflate(R.layout.dialog_color_picker_hsv, null)
        val wheel = view.findViewById<HsvColorWheelView>(R.id.color_wheel)
        val brightnessBar = view.findViewById<SeekBar>(R.id.seek_brightness)
        val preview = view.findViewById<View>(R.id.color_preview)
        val hexInput = view.findViewById<android.widget.EditText>(R.id.edit_hex)

        var syncing = false

        wheel.setColor(mInitialColor)
        brightnessBar.max = 255
        brightnessBar.progress = (wheel.getBrightness() * 255).toInt()
        preview.setBackgroundColor(mInitialColor)
        hexInput.setText(colorToHex(mInitialColor))

        wheel.setOnColorChangedListener(object : HsvColorWheelView.OnColorChangedListener {
            override fun onColorChanged(color: Int) {
                if (syncing) return
                syncing = true
                preview.setBackgroundColor(color)
                hexInput.setText(colorToHex(color))
                syncing = false
            }
        })

        brightnessBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                syncing = true
                wheel.setBrightness(progress / 255f)
                val color = wheel.getColor()
                preview.setBackgroundColor(color)
                hexInput.setText(colorToHex(color))
                syncing = false
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (syncing) return
                var hex = s.toString()
                if (!hex.startsWith("#")) hex = "#$hex"
                if (hex.length != 7) return
                try {
                    val color = Color.parseColor(hex)
                    syncing = true
                    wheel.setColor(color)
                    brightnessBar.progress = (wheel.getBrightness() * 255).toInt()
                    preview.setBackgroundColor(color)
                    syncing = false
                } catch (_: IllegalArgumentException) {}
            }
        })

        AlertDialog.Builder(mContext)
            .setTitle("Custom Color (HSV)")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                mListener?.onColorSelected(wheel.getColor())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRgbPicker() {
        val view = LayoutInflater.from(mContext).inflate(R.layout.dialog_color_picker_rgb, null)
        val preview = view.findViewById<View>(R.id.color_preview)
        val seekR = view.findViewById<SeekBar>(R.id.seek_r)
        val seekG = view.findViewById<SeekBar>(R.id.seek_g)
        val seekB = view.findViewById<SeekBar>(R.id.seek_b)
        val valR = view.findViewById<android.widget.TextView>(R.id.val_r)
        val valG = view.findViewById<android.widget.TextView>(R.id.val_g)
        val valB = view.findViewById<android.widget.TextView>(R.id.val_b)
        val hexInput = view.findViewById<android.widget.EditText>(R.id.edit_hex)

        var syncing = false
        val rgb = intArrayOf(
            Color.red(mInitialColor),
            Color.green(mInitialColor),
            Color.blue(mInitialColor)
        )

        seekR.max = 255; seekR.progress = rgb[0]
        seekG.max = 255; seekG.progress = rgb[1]
        seekB.max = 255; seekB.progress = rgb[2]
        valR.text = rgb[0].toString()
        valG.text = rgb[1].toString()
        valB.text = rgb[2].toString()
        preview.setBackgroundColor(mInitialColor)
        hexInput.setText(colorToHex(mInitialColor))

        val sbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                when (seekBar.id) {
                    R.id.seek_r -> { rgb[0] = progress; valR.text = progress.toString() }
                    R.id.seek_g -> { rgb[1] = progress; valG.text = progress.toString() }
                    R.id.seek_b -> { rgb[2] = progress; valB.text = progress.toString() }
                }
                val color = Color.rgb(rgb[0], rgb[1], rgb[2])
                syncing = true
                preview.setBackgroundColor(color)
                hexInput.setText(colorToHex(color))
                syncing = false
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }

        seekR.setOnSeekBarChangeListener(sbListener)
        seekG.setOnSeekBarChangeListener(sbListener)
        seekB.setOnSeekBarChangeListener(sbListener)

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (syncing) return
                var hex = s.toString()
                if (!hex.startsWith("#")) hex = "#$hex"
                if (hex.length != 7) return
                try {
                    val color = Color.parseColor(hex)
                    syncing = true
                    rgb[0] = Color.red(color)
                    rgb[1] = Color.green(color)
                    rgb[2] = Color.blue(color)
                    seekR.progress = rgb[0]; valR.text = rgb[0].toString()
                    seekG.progress = rgb[1]; valG.text = rgb[1].toString()
                    seekB.progress = rgb[2]; valB.text = rgb[2].toString()
                    preview.setBackgroundColor(color)
                    syncing = false
                } catch (_: IllegalArgumentException) {}
            }
        })

        AlertDialog.Builder(mContext)
            .setTitle("Custom Color (RGB)")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                mListener?.onColorSelected(Color.rgb(rgb[0], rgb[1], rgb[2]))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
