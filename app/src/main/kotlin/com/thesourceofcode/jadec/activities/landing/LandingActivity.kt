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

package com.thesourceofcode.jadec.activities.landing

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.angads25.filepicker.model.DialogConfigs
import com.github.angads25.filepicker.model.DialogProperties
import com.github.angads25.filepicker.view.FilePickerDialog
import com.google.ads.consent.ConsentStatus
import com.google.android.gms.ads.AdView
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.activities.BaseActivity
import com.thesourceofcode.jadec.activities.apps.AppsActivity
import com.thesourceofcode.jadec.activities.decompiler.DecompilerActivity
import com.thesourceofcode.jadec.activities.explorer.navigator.NavigatorActivity
import com.thesourceofcode.jadec.activities.landing.adapters.HistoryListAdapter
import com.thesourceofcode.jadec.data.PackageInfo
import com.thesourceofcode.jadec.data.SourceInfo
import com.thesourceofcode.jadec.databinding.ActivityLandingBinding
import com.thesourceofcode.jadec.utils.Ads
import com.thesourceofcode.jadec.utils.secure.PurchaseUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
//import kotlinx.android.synthetic.main.activity_landing.*
import timber.log.Timber
import java.io.File


class LandingActivity : BaseActivity() {

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var landingHandler: LandingHandler
    private lateinit var filePickerDialog: FilePickerDialog
    private lateinit var purchaseUtils: PurchaseUtils

    private lateinit var binding: ActivityLandingBinding

    private var historyListAdapter: HistoryListAdapter? = null
    private var historyItems = ArrayList<SourceInfo>()

    private var shouldLoadHistory = true

    override fun init(savedInstanceState: Bundle?) {
        binding = ActivityLandingBinding.inflate(layoutInflater)
        val view = binding.root
        setupLayout(view)
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            R.string.drawerOpen,
            R.string.drawerClose
        )
        binding.navigationView.setNavigationItemSelectedListener {
            onOptionsItemSelected(it)
        }

        if (!isPro()) {
            binding.navigationView.menu.findItem(R.id.get_pro_option).isVisible = true
        }

        binding.drawerLayout.addDrawerListener(drawerToggle)
        landingHandler = LandingHandler(context)
        setupFab()

        if (savedInstanceState != null) {
             savedInstanceState.getParcelableArrayList<SourceInfo>("historyItems")?.let {
                 this.historyItems = it
                 shouldLoadHistory = false
                 setupList()
             }
        }

        val properties = DialogProperties()
        properties.selection_mode = DialogConfigs.SINGLE_MODE
        properties.selection_type = DialogConfigs.FILE_SELECT
        properties.root = Environment.getExternalStorageDirectory()
        properties.error_dir = properties.root
        properties.offset = properties.root
        properties.extensions = arrayOf("apk", "jar", "dex", "odex")

        filePickerDialog = FilePickerDialog(this, properties)
        filePickerDialog.setTitle(getString(R.string.selectFile))

        filePickerDialog.setDialogSelectionListener { files ->
            if (files.isNotEmpty()) {
                val selectedFile = File(files.first())
                if (selectedFile.exists() && selectedFile.isFile) {
                    PackageInfo.fromFile(context, selectedFile) ?. let {
                        val i = Intent(context, DecompilerActivity::class.java)
                        i.putExtra("packageInfo", it)
                        startActivity(i)
                    }
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            populateHistory(true)
        }

        purchaseUtils = PurchaseUtils(this, secureUtils)
        purchaseUtils.doOnComplete {
            if (isPro()) {
                supportActionBar?.title = "${getString(R.string.appName)} Pro"
                findViewById<AdView>(R.id.adView)?.visibility = View.GONE
                binding.navigationView.menu.findItem(R.id.get_pro_option)?.isVisible = false
            }
        }
        purchaseUtils.initializeCheckout(false, true)
        if (inEea && userPreferences.consentStatus == ConsentStatus.UNKNOWN.ordinal) {
            Ads(context).loadConsentScreen()
        }
    }

    public override fun onResume() {
        super.onResume()
       // if (hasValidPermissions()) {
            populateHistory(true)
      //  }
        if (isPro()) {
            supportActionBar?.title = "${getString(R.string.appName)} Pro"
            findViewById<AdView>(R.id.adView)?.visibility = View.GONE
            binding.navigationView.menu.findItem(R.id.get_pro_option)?.isVisible = false
        }
    }

    private fun setupFab() {
        binding.selectionFab.addOnMenuItemClickListener { _, _, itemId ->
            when (itemId) {
                R.id.action_pick_installed -> {
                    startActivity(
                        Intent(context, AppsActivity::class.java)
                    )
                }
                R.id.action_pick_sdcard -> {
                    pickFile()
                }
            }
        }
    }

    private fun pickFile() {
        filePickerDialog.show()
    }

    override fun postPermissionsGrant() {
        if (shouldLoadHistory) {
            populateHistory()
        }
    }

    private fun populateHistory(resume: Boolean = false) {
        binding.swipeRefresh.isRefreshing = true
        disposables.add(landingHandler.loadHistory()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorReturn {
                Timber.e(it)
                ArrayList()
            }
            .subscribe {
                historyItems = it
                binding.swipeRefresh.isRefreshing = false
                if (resume && historyListAdapter != null) {
                    historyListAdapter?.updateData(historyItems)
                    setListVisibility(!historyItems.isEmpty())
                } else {
                    setupList()
                }
            }
        )
    }

    private fun setListVisibility(isListVisible: Boolean = true) {
        val listGroupVisibility = if (isListVisible) View.VISIBLE else View.GONE
        val defaultGroupVisibility = if (isListVisible) View.GONE else View.VISIBLE
        binding.historyListView.visibility = listGroupVisibility
        binding.swipeRefresh.visibility = listGroupVisibility
        binding.pickAppText.visibility = listGroupVisibility
        binding.welcomeLayout.visibility = defaultGroupVisibility
    }


    private fun setupList() {
        if (historyItems.isEmpty()) {
            setListVisibility(false)
        } else {
            setListVisibility(true)
            binding.historyListView.setHasFixedSize(true)
            binding.historyListView.layoutManager = LinearLayoutManager(context)
            historyListAdapter = HistoryListAdapter(historyItems) { selectedHistoryItem ->
                val intent = Intent(context, NavigatorActivity::class.java)
                intent.putExtra("selectedApp", selectedHistoryItem)
                startActivity(intent)
            }
            binding.historyListView.adapter = historyListAdapter
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putParcelableArrayList("historyItems", historyItems)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        purchaseUtils.onDestroy()
    }
}
