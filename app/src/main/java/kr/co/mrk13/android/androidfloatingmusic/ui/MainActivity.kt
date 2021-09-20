package kr.co.mrk13.android.androidfloatingmusic.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.co.mrk13.android.androidfloatingmusic.R
import kr.co.mrk13.android.androidfloatingmusic.databinding.ActivityMainBinding
import kr.co.mrk13.android.androidfloatingmusic.util.isServiceRunning


/**
 * @author ross.
 * @see https://www.geeksforgeeks.org/how-to-make-a-floating-window-application-in-android
 */
class MainActivity : AppCompatActivity() {

    private var mBinding: ActivityMainBinding? = null
    private val binding get() = mBinding!!

    private lateinit var permissionLauncher: ActivityResultLauncher<Intent>
    private var dialogDelayMs: Long = 0

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If the app is started again while the
        // floating window service is running
        // then the floating window service will stop
        if (isServiceRunning(this)) {
            // onDestroy() method in FloatingControlService
            // class will be called here
            stopService(Intent(this@MainActivity, FloatingControlService::class.java))
        }

//        val weakActivity = WeakReference(this)
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
//            if (result.resultCode == RESULT_OK) {
//                weakActivity.get()?.startServiceWithPermission()
//            } else {
//                weakActivity.get()?.finish()
//            }
        }
    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        startServiceWithPermission()
    }

    private fun startServiceWithPermission() {
        // First it confirms whether the
        // 'Display over other apps' permission in given
        if (checkOverlayDisplayPermission()) {
            if (checkNotificationPermissionAllowed()) {
                startServiceIfNotExist()
            } else {
                handler.postDelayed({
                    dialogDelayMs = 0
                    requestNotificationPermission()
                }, dialogDelayMs)
            }
        } else {
            // If permission is not given,
            // it shows the AlertDialog box and
            // redirects to the Settings
            handler.postDelayed({
                dialogDelayMs = 0
                requestOverlayDisplayPermission()
            }, dialogDelayMs)
        }
    }

    private fun startServiceIfNotExist() {
        if (!isServiceRunning(this)) {
            // FloatingControlService service is started
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this@MainActivity,
                        FloatingControlService::class.java
                    )
                )
            } catch (e: Throwable) {

            }
        }
        // The MainActivity closes here
        finish()
    }

    private fun requestOverlayDisplayPermission() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(getString(R.string.alert_overlay_permission_title))
        builder.setMessage(getString(R.string.alert_overlay_permission_message))
        builder.setPositiveButton(getString(R.string.alert_overlay_permission_btn_positive)) { _, _ -> // The app will redirect to the 'Display over other apps' in Settings.
            // This is an Implicit Intent. This is needed when any Action is needed
            // to perform, here it is
            // redirecting to an other app(Settings).
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            permissionLauncher.launch(intent)
        }
        builder.setNegativeButton(getString(R.string.alert_overlay_permission_btn_negative)) { _, _ ->
            finish()
        }
        val dialog = builder.create()
        // The Dialog will
        // show in the screen
        dialog.show()
        dialogDelayMs = 500L
    }

    private fun checkOverlayDisplayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestNotificationPermission() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(getString(R.string.alert_notification_permission_title))
        builder.setMessage(getString(R.string.alert_notification_permission_message))
        builder.setPositiveButton(getString(R.string.alert_notification_permission_btn_positive)) { _, _ ->
            // This is an Implicit Intent. This is needed when any Action is needed
            // to perform, here it is
            // redirecting to an other app(Settings).
            val intent = Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )

            permissionLauncher.launch(intent)
        }
        builder.setNegativeButton(getString(R.string.alert_notification_permission_btn_negative)) { _, _ ->
            finish()
        }
        val dialog = builder.create()
        // The Dialog will
        // show in the screen
        dialog.show()
    }

    private fun checkNotificationPermissionAllowed(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(applicationContext)
            .any { enabledPackageName ->
                enabledPackageName == packageName
            }
    }
}