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
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.analytics.FirebaseAnalytics
import com.thesourceofcode.jadec.Constants
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.activities.BaseActivity
import com.thesourceofcode.jadec.activities.apps.adapters.getSystemBadge
import com.thesourceofcode.jadec.activities.explorer.navigator.NavigatorActivity
import com.thesourceofcode.jadec.data.PackageInfo
import com.thesourceofcode.jadec.data.SourceInfo
import com.thesourceofcode.jadec.databinding.ActivityDecompilerBinding
import com.thesourceofcode.jadec.databinding.LayoutPickDecompilerListItemBinding
import com.thesourceofcode.jadec.decompilers.BaseDecompiler
import com.thesourceofcode.jadec.decompilers.BaseDecompiler.Companion.isAvailable
import com.thesourceofcode.jadec.utils.ktx.sourceDir
import com.thesourceofcode.jadec.utils.ktx.toBundle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
//import kotlinx.android.synthetic.main.activity_decompiler.*
//import kotlinx.android.synthetic.main.layout_app_list_item.view.*
//import kotlinx.android.synthetic.main.layout_pick_decompiler_list_item.view.*
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.URI


class DecompilerActivity : BaseActivity() {

    private lateinit var packageInfo: PackageInfo

    private lateinit var binding: ActivityDecompilerBinding

    private var startedCompilation = false
    @SuppressLint("SetTextI18n")
    override fun init(savedInstanceState: Bundle?) {
        binding = ActivityDecompilerBinding.inflate(layoutInflater)
        val viewroot = binding.root
        setupLayout(viewroot)

        loadPackageInfoFromIntent()

        if (!::packageInfo.isInitialized) {
            Toast.makeText(context, R.string.cannotDecompileFile, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val apkSize = FileUtils.byteCountToDisplaySize(packageInfo.file.length())

        binding.itemLabel.text = if (packageInfo.isSystemPackage)
            SpannableString(
                TextUtils.concat(
                    packageInfo.label,
                    " ", " ",
                    getSystemBadge(context).toSpannable()
                )
            )
        else
            packageInfo.label

        binding.itemSecondaryLabel.text = "${packageInfo.version} - $apkSize"

        val decompilersValues = resources.getStringArray(R.array.decompilersValues)
        val decompilers = resources.getStringArray(R.array.decompilers)
        val decompilerDescriptions = resources.getStringArray(R.array.decompilerDescriptions)

        decompilersValues.forEachIndexed { index, decompiler ->
//            val view = LayoutInflater.from(binding.pickerList.context)
//                .inflate(R.layout.layout_pick_decompiler_list_item, binding.pickerList, false)
            val pickerbinding = LayoutPickDecompilerListItemBinding.inflate(layoutInflater)
            val view = pickerbinding.root
            pickerbinding.decompilerName.text = decompilers[index]
            pickerbinding.decompilerDescription.text = decompilerDescriptions[index]
            pickerbinding.decompilerItemCard.cardElevation = 1F
            pickerbinding.decompilerItemCard.setOnClickListener {
                startProcess(it, decompiler, index)
            }
            binding.pickerList.addView(view)
        }

        if (packageInfo.isSystemPackage) {
            binding.systemAppWarning.visibility = View.VISIBLE
            val warning = getString(R.string.systemAppWarning)
            val sb = SpannableStringBuilder(warning)
            val bss = StyleSpan(Typeface.BOLD)
            val iss = StyleSpan(Typeface.ITALIC)
            val nss = StyleSpan(Typeface.NORMAL)
            sb.setSpan(bss, 0, 8, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            sb.setSpan(nss, 8, warning.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            sb.setSpan(iss, 0, warning.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            binding.systemAppWarning.text = sb
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            binding.decompilersUnavailableNotification.visibility = View.VISIBLE
        }

        disposables.add(
            Observable.fromCallable {
                packageInfo.loadIcon(context)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorReturn {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        resources.getDrawable(R.drawable.ic_list_generic, null)
                    } else {
                        resources.getDrawable(R.drawable.ic_list_generic)
                    }
                }
                .subscribe { binding.itemIcon.setImageDrawable(it) }
        )

        assertSourceExistence(true)
    }

    private fun loadPackageInfoFromIntent() {
        if (intent.dataString.isNullOrEmpty()) {
            if (intent.hasExtra("packageInfo")) {
                packageInfo = intent.getParcelableExtra("packageInfo")!!
            } else {
                Toast.makeText(context, R.string.errorLoadingInputFile, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val info = PackageInfo.fromFile(
                context,
                File(URI.create(intent.dataString)).canonicalFile
            )
            if (info != null) {
                packageInfo = info
            }
        }
    }

    override fun onDestroy() {
        if (packageInfo.isExternalPackage && !startedCompilation){
            val tempFile = File(context.cacheDir, packageInfo.filePath.substring(packageInfo.filePath.lastIndexOf("/") + 1))
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        assertSourceExistence()
    }

    private fun assertSourceExistence(addListener: Boolean = false) {
        val sourceInfo = SourceInfo.from(sourceDir(packageInfo.name))
        if (addListener) {
            binding.historyCard.setOnClickListener {
                val intent = Intent(context, NavigatorActivity::class.java)
                intent.putExtra("selectedApp", sourceInfo)
                startActivity(intent)
            }
        }
        if (sourceInfo.exists()) {
            binding.historyCard.visibility = View.VISIBLE
            binding.historyInfo.text = FileUtils.byteCountToDisplaySize(sourceInfo.sourceSize)
        } else {
            binding.historyCard.visibility = View.GONE
        }
    }

    private fun startProcess(view: View, decompiler: String, decompilerIndex: Int) {

        if (!isAvailable(decompiler)) {
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.decompilerUnavailable))
                .setMessage(getString(R.string.decompilerUnavailableExplanation))
                .setIcon(R.drawable.ic_error_outline_black)
                .setNegativeButton(android.R.string.ok, null)
                .show()
            return
        }

        val inputMap = hashMapOf(
            "shouldIgnoreLibs" to userPreferences.ignoreLibraries,
            "maxAttempts" to userPreferences.maxAttempts,
            "chunkSize" to userPreferences.chunkSize,
            "memoryThreshold" to userPreferences.memoryThreshold,
            "keepIntermediateFiles" to userPreferences.keepIntermediateFiles,
            "decompiler" to decompiler,
            "name" to packageInfo.name,
            "label" to packageInfo.label,
            "inputPackageFile" to packageInfo.filePath,
            "type" to packageInfo.type.ordinal,
            "isExternalPackage" to packageInfo.isExternalPackage
        )

        BaseDecompiler.start(inputMap)

        startedCompilation = true

        firebaseAnalytics.logEvent(
            Constants.EVENTS.SELECT_DECOMPILER, hashMapOf(
                FirebaseAnalytics.Param.VALUE to decompiler
            ).toBundle()
        )

        firebaseAnalytics.logEvent(Constants.EVENTS.DECOMPILE_APP, inputMap.toBundle())

        val i = Intent(this, DecompilerProcessActivity::class.java)
        i.putExtra("packageInfo", packageInfo)
        i.putExtra("decompilerIndex", decompilerIndex)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val options = ActivityOptions
                .makeSceneTransitionAnimation(this, view, "decompilerItemCard")
            startActivity(i, options.toBundle())
        } else {
            startActivity(i)
        }
        finish()
    }
}