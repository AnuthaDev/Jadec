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

package com.thesourceofcode.jadec.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.google.ads.consent.ConsentInformation
import com.google.ads.consent.ConsentStatus
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.thesourceofcode.jadec.Constants
import com.thesourceofcode.jadec.MainApplication
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.activities.about.AboutActivity
import com.thesourceofcode.jadec.activities.purchase.PurchaseActivity
import com.thesourceofcode.jadec.activities.settings.SettingsActivity
import com.thesourceofcode.jadec.utils.secure.SecureUtils
import com.thesourceofcode.jadec.utils.UserPreferences
import com.thesourceofcode.jadec.utils.ktx.checkDataConnection
import io.reactivex.disposables.CompositeDisposable
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions


abstract class BaseActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    protected lateinit var toolbar: Toolbar
    protected lateinit var context: AppCompatActivity
    protected lateinit var userPreferences: UserPreferences
    protected lateinit var secureUtils: SecureUtils
    protected lateinit var mainApplication: MainApplication

    lateinit var firebaseAnalytics: FirebaseAnalytics

    protected val disposables = CompositeDisposable()
    protected var inEea = false

    abstract fun init(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        inEea = ConsentInformation.getInstance(this).isRequestLocationInEeaOrUnknown
        firebaseAnalytics = Firebase.analytics
        mainApplication = application as MainApplication
        firebaseAnalytics.setUserProperty("instance_id", mainApplication.instanceId)

        userPreferences = UserPreferences(getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
        secureUtils = SecureUtils.getInstance(applicationContext)

        if (userPreferences.customFont) {
            context.theme.applyStyle(R.style.LatoFontStyle, true)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.storagePermissionRationale),
                    Constants.STORAGE_PERMISSION_REQUEST,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                init(savedInstanceState)
            } else {
                init(savedInstanceState)
                postPermissionsGrant()
            }
        } else {
            init(savedInstanceState)
            postPermissionsGrant()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!EasyPermissions.hasPermissions(this,Manifest.permission.POST_NOTIFICATIONS)) {

                EasyPermissions.requestPermissions(
                    this,
                    "Please allow notification permission :-)",
                    1010,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    fun setupLayout(layoutRef: Int) {
        setContentView(layoutRef)
        setupToolbar(null)
        setupGoogleAds()
    }
    fun setupLayout(layoutRef: View) {
        setContentView(layoutRef)
        setupToolbar(null)
        setupGoogleAds()
    }

    fun setupLayout(layoutRef: View, title: String) {
        setContentView(layoutRef)
        setupToolbar(title)
        setupGoogleAds()
    }
    fun setupLayout(layoutRef: Int, title: String) {
        setContentView(layoutRef)
        setupToolbar(title)
        setupGoogleAds()
    }

    fun setupLayoutNoActionBar(layoutRef: Int) {
        setContentView(layoutRef)
    }

    fun setSubtitle(subtitle: String?) {
        // Workaround for a weird bug caused by Calligraphy
        // https://github.com/chrisjenx/Calligraphy/issues/280#issuecomment-256444828
        toolbar.post {
            toolbar.subtitle = subtitle
        }

    }

    private fun setupToolbar(title: String?) {
        toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        if (title != null) {
            supportActionBar?.title = title
        } else {
            if (isPro()) {
                val activityInfo: ActivityInfo
                try {
                    activityInfo = packageManager.getActivityInfo(
                        componentName,
                        PackageManager.GET_META_DATA
                    )
                    val currentTitle = activityInfo.loadLabel(packageManager).toString()
                    if (currentTitle.trim() == getString(R.string.appName)) {
                        supportActionBar?.title = "${getString(R.string.appName)} Pro"
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {

                }
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupGoogleAds() {
        findViewById<AdView>(R.id.adView)?.let {it ->
            it.visibility = View.GONE
            if (!isPro()) {
                val extras = Bundle()
                val consentStatus = ConsentStatus.values()[userPreferences.consentStatus]
                if (consentStatus == ConsentStatus.NON_PERSONALIZED) {
                    extras.putString("npa", "1")
                }

                val adRequest = AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                    //.addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                   // .addTestDevice(getString(R.string.adUnitId))
                    .build()

                it.adListener = object : AdListener() {
                    override fun onAdFailedToLoad(errorCode: LoadAdError) {
                        super.onAdFailedToLoad(errorCode)
                        it.visibility = View.GONE
                    }

                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        it.visibility = View.VISIBLE
                    }
                }
                it.loadAd(adRequest)
                if (!checkDataConnection(context)) {
                    it.visibility = View.GONE
                }
            }
        }
    }

    fun isPro(): Boolean {
        return secureUtils.hasPurchasedPro()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.about_option -> {
                startActivity(Intent(baseContext, AboutActivity::class.java))
                return true
            }
            R.id.bug_report_option -> {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:") // Only email apps handle this.
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("thesourceofcode@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Bug in Jadec")
                    putExtra(Intent.EXTRA_TEXT, "Hello! I found a bug in your app \n <Describe your problem>")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }else{
                    Toast.makeText(this, "You don't have an email app :-)", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            R.id.settings_option -> {
                startActivity(Intent(baseContext, SettingsActivity::class.java))
                return true
            }
            R.id.get_pro_option -> {
                startActivity(Intent(baseContext, PurchaseActivity::class.java))
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    open fun postPermissionsGrant() {}

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        postPermissionsGrant()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (perms.isNotEmpty() || EasyPermissions.somePermissionPermanentlyDenied(
                    this,
                    perms
                )
            ) {
                AppSettingsDialog.Builder(this)
                    .build()
                    .show()
            }
        }
    }

    fun hasValidPermissions(): Boolean {
        return EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    @Deprecated("Deprecated in Java")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
                if (!EasyPermissions.hasPermissions(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    Toast.makeText(
                        this,
                        R.string.storagePermissionRationale,
                        Toast.LENGTH_LONG
                    ).show()
                    //finish()
                } else {
                    postPermissionsGrant()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }
}