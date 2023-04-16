/*
 * Show Java - A java/apk decompiler for android
 * Copyright (c) 2018 Niranjan Rajendran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.thesourceofcode.jadec.activities.decompiler

import android.os.Bundle
import android.widget.Toast
import com.thesourceofcode.jadec.Constants
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.activities.BaseActivity
import com.thesourceofcode.jadec.data.PackageInfo
import com.thesourceofcode.jadec.databinding.ActivityLowMemoryBinding
import com.thesourceofcode.jadec.utils.ktx.toBundle
//import kotlinx.android.synthetic.main.activity_low_memory.*

/**
 * If an app's decompilation was stopped due to low memory, explain what happened to the user
 * And also provide user a way to report the app that failed to decompile. This can then
 * be investigated later on to see what can be done to reduce the memory usage.
 */
class LowMemoryActivity : BaseActivity() {
    private lateinit var binding: ActivityLowMemoryBinding
    override fun init(savedInstanceState: Bundle?) {
        binding = ActivityLowMemoryBinding.inflate(layoutInflater)
        val view = binding.root
        setupLayout(view)
        val packageInfo = intent.getParcelableExtra<PackageInfo>("packageInfo")
        val decompiler = intent.getStringExtra("decompiler")

        binding.reportButton.setOnClickListener {

            firebaseAnalytics.logEvent(Constants.EVENTS.REPORT_APP_LOW_MEMORY, mapOf(
                "shouldIgnoreLibs" to userPreferences.ignoreLibraries,
                "maxAttempts" to userPreferences.maxAttempts,
                "chunkSize" to userPreferences.chunkSize,
                "memoryThreshold" to userPreferences.memoryThreshold,
                "label" to packageInfo!!.label,
                "name" to packageInfo.name,
                "type" to packageInfo.type.name,
                "decompiler" to decompiler
            ).toBundle())

            Toast.makeText(context, R.string.appReportThanks, Toast.LENGTH_LONG).show()
        }
    }

}