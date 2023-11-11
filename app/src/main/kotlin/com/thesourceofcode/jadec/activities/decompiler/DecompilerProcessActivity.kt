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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.activities.BaseActivity
import com.thesourceofcode.jadec.data.PackageInfo
//import kotlinx.android.synthetic.main.activity_decompiler_process.*
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.thesourceofcode.jadec.BuildConfig
import com.thesourceofcode.jadec.Constants
import com.thesourceofcode.jadec.activities.explorer.navigator.NavigatorActivity
import com.thesourceofcode.jadec.data.SourceInfo
import com.thesourceofcode.jadec.databinding.ActivityDecompilerProcessBinding
import com.thesourceofcode.jadec.utils.ktx.sourceDir
import com.thesourceofcode.jadec.workers.DecompilerWorker
import timber.log.Timber


class DecompilerProcessActivity : BaseActivity() {

    private val statusesMap = mutableMapOf(
        "jar-extraction" to WorkInfo.State.ENQUEUED,
        "java-extraction" to WorkInfo.State.ENQUEUED,
        "resources-extraction" to WorkInfo.State.ENQUEUED
    )

    private lateinit var packageInfo: PackageInfo
    private lateinit var binding: ActivityDecompilerProcessBinding

    private var hasCompleted = false
    private var showMemoryUsage = false
    private var ranOutOfMemory = false

    override fun init(savedInstanceState: Bundle?) {
        binding = ActivityDecompilerProcessBinding.inflate(layoutInflater)
        val view = binding.root
        setupLayout(view)
        packageInfo = intent.getParcelableExtra("packageInfo")!!
        showMemoryUsage = userPreferences.showMemoryUsage

        binding.memoryUsage.visibility = if (showMemoryUsage) View.VISIBLE else View.GONE
        binding.memoryStatus.visibility = if (showMemoryUsage) View.VISIBLE else View.GONE

        val decompilerIndex = intent.getIntExtra("decompilerIndex", 0)

        binding.inputPackageLabel.text = packageInfo.label

        val decompilers = resources.getStringArray(R.array.decompilers)
        val decompilerValues = resources.getStringArray(R.array.decompilersValues)
        val decompilerDescriptions = resources.getStringArray(R.array.decompilerDescriptions)

        binding.decompilerItemCard.decompilerName.text = decompilers[decompilerIndex]
        binding.decompilerItemCard.decompilerDescription.text = decompilerDescriptions[decompilerIndex]


        setupGears()

        val statusIntentFilter = IntentFilter(Constants.WORKER.ACTION.BROADCAST + packageInfo.name)
        registerReceiver(progressReceiver, statusIntentFilter)

        binding.cancelButton.setOnClickListener {
            DecompilerWorker.cancel(context, packageInfo.name)
            finish()
        }

        WorkManager.getInstance()
            .getWorkInfosForUniqueWorkLiveData(packageInfo.name)
            .observe(this, Observer<List<WorkInfo>> { statuses ->
                statuses.forEach {
                    statusesMap.keys.forEach { tag ->
                        if (it.tags.contains(tag)) {
                            statusesMap[tag] = it.state
                        }
                    }

                    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        it.outputData.keyValueMap.forEach { t, u ->
                            Timber.d("[status][data] $t : $u")
                        }
                        statusesMap.forEach { t, u ->
                            Timber.d("[status][statuses] $t : $u")
                        }
                    }

                    if (it.outputData.getBoolean("ranOutOfMemory", false)) {
                        val intent = Intent(context, LowMemoryActivity::class.java)
                        intent.putExtra("packageInfo", packageInfo)
                        intent.putExtra("decompiler", decompilerValues[decompilerIndex])
                        startActivity(intent)
                        hasCompleted = true
                        finish()
                    } else {
                        reconcileDecompilerStatus()
                    }
                }
            })
    }

    private fun reconcileDecompilerStatus() {
        synchronized(hasCompleted) {
            if (hasCompleted) {
                return
            }

            val hasFailed = statusesMap.values.any { it == WorkInfo.State.FAILED }
            val isWaiting = statusesMap.values.any { it == WorkInfo.State.ENQUEUED }
            val hasPassed = statusesMap.values.all { it == WorkInfo.State.SUCCEEDED }
            val isCancelled = statusesMap.values.any { it == WorkInfo.State.CANCELLED }

            if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                statusesMap.forEach { t, u ->
                    Timber.d("[status] Worker: $t State: ${u.name}")
                }
            }

            Timber.d("[status] [${packageInfo.name}] hasPassed: $hasPassed | hasFailed: $hasFailed")

            when {
                isCancelled -> {
                    hasCompleted = true
                    finish()
                }
                hasFailed -> {
                    Toast.makeText(
                        context,
                        getString(R.string.errorDecompilingApp, packageInfo.label),
                        Toast.LENGTH_LONG
                    ).show()
                    hasCompleted = true
                    finish()
                }
                hasPassed -> {
                    val intent = Intent(context, NavigatorActivity::class.java)
                    intent.putExtra("selectedApp", SourceInfo.from(
                        sourceDir(
                            packageInfo.name
                        )
                    ))
                    startActivity(intent)
                    hasCompleted = true
                    finish()
                }
                isWaiting -> binding.statusText.text = getString(R.string.waitingToStart)
            }
        }
    }

    private fun getGearAnimation(duration: Int = 1, isClockwise: Boolean = true): RotateAnimation {
        val animation = RotateAnimation(
            if (isClockwise) 0.0f else 360.0f,
            if (isClockwise) 360.0f else 0.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        animation.repeatCount = Animation.INFINITE
        animation.duration = duration.toLong() * 1500
        animation.interpolator = LinearInterpolator()
        return animation
    }

    private fun setupGears() {
        binding.leftProgressGear.post { binding.leftProgressGear.animation = getGearAnimation(2, true) }
        binding.rightProgressGear.post { binding.rightProgressGear.animation = getGearAnimation(1, false) }
    }

    private val progressReceiver = object : BroadcastReceiver() {

        @SuppressLint("SetTextI18n") // For memory status
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra(Constants.WORKER.STATUS_MESSAGE)
            if (intent.getStringExtra(Constants.WORKER.STATUS_TYPE) == "memory") {
                if (!showMemoryUsage) {
                    return
                }
                try {
                    val percentage = message?.toDouble()
                    binding.memoryStatus.text = "$message%"
                    val textColor = ContextCompat.getColor(
                        context,
                        when {
                            percentage!! < 40 -> R.color.green_500
                            percentage < 60 -> R.color.amber_500
                            percentage < 80 -> R.color.orange_500
                            else -> R.color.red_500
                        }
                    )
                    binding.memoryStatus.setTextColor(textColor)
                    binding.memoryUsage.setTextColor(textColor)
                } catch (ignored: Exception) { }
                return
            }

            intent.getStringExtra(Constants.WORKER.STATUS_TITLE)?.let {
                if (it.trim().isNotEmpty()) {
                    binding.statusTitle.text = it
                }
            }
            message?.let {
                binding.statusText.text = it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(progressReceiver)
    }
}