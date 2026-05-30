package com.newtermux.features

import android.content.Intent
import android.view.View
import com.termux.app.TermuxActivity
import com.termux.app.activities.PackageManagerActivity

/**
 * Handles the toolbar packages button — launches the full Package Manager activity.
 */
class PackageManagerMenu(private val mActivity: TermuxActivity) {

    fun show(anchor: View) {
        val intent = Intent(mActivity, PackageManagerActivity::class.java)
        mActivity.startActivity(intent)
    }
}
