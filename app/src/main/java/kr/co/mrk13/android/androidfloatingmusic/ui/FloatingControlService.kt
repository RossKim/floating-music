package kr.co.mrk13.android.androidfloatingmusic.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import com.bumptech.glide.Glide
import kr.co.mrk13.android.androidfloatingmusic.R
import kr.co.mrk13.android.androidfloatingmusic.constant.Constant
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaCommand
import kr.co.mrk13.android.androidfloatingmusic.databinding.FloatingControlBinding
import kr.co.mrk13.android.androidfloatingmusic.model.MusicData
import kr.co.mrk13.android.androidfloatingmusic.receiver.ActiveSessionsChangedListener
import kr.co.mrk13.android.androidfloatingmusic.util.Log
import kr.co.mrk13.android.androidfloatingmusic.util.convertDp2Px
import kotlin.math.abs
import kotlin.math.max

var notificationId = 1

enum class ResizeMode {
    BottomLeft, BottomRight
}

interface MusicDataViewModel {
    val musicData: LiveData<MusicData?>
    fun musicDataChange(data: MusicData?)
}

class FloatingControlServiceViewModel : ViewModel(), MusicDataViewModel {
    private val mutableMusicData = MutableLiveData<MusicData?>()
    override val musicData: LiveData<MusicData?> get() = mutableMusicData

    override fun musicDataChange(data: MusicData?) {
        mutableMusicData.value = data
    }
}

/**
 * @author ross.
 * @see https://android.googlesource.com/platform/packages/apps/Music/+/278aaed1eb37763f7dcb2364523db591014a9210/src/com/android/music/MediaPlaybackService.java
 *
 * @see https://stackoverflow.com/questions/59052978/is-there-any-way-to-fetch-the-info-of-the-current-playing-song
 */
class FloatingControlService : NotificationListenerService(), LifecycleOwner {

    private var floatView: ViewGroup? = null
    private var LAYOUT_TYPE = 0
    private var floatWindowLayoutParam: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    private var mBinding: FloatingControlBinding? = null
    private val binding get() = mBinding!!

    private val viewModel = FloatingControlServiceViewModel()
    private val sessionsChangeListener = ActiveSessionsChangedListener(this, viewModel)
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var isForeground = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        if (!isForeground) {
            isForeground = true
            handler.postDelayed({
                startForeground()
            }, 1000)
        }

        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this

        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this
        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels
//        val height = metrics.heightPixels

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml
        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // inflate a new view hierarchy from the floating_layout xml

        // inflate a new view hierarchy from the floating_layout xml
        mBinding = FloatingControlBinding.inflate(inflater)
        floatView = binding.root


        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.

        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.
        LAYOUT_TYPE =
                // If API Level is more than 26, we need TYPE_APPLICATION_OVERLAY
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 55% of the phone width.
        // 2) The Height of the window will be 58% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 30% of the phone width.
        // 2) The Height of the window will be 30% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT
        val windowWidth = max(
            convertDp2Px(Constant.controlWindowMinimumSize, applicationContext),
            width * 0.3f
        ).toInt()
        floatWindowLayoutParam = WindowManager.LayoutParams(
            windowWidth,
            windowWidth,
            LAYOUT_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen
        floatWindowLayoutParam?.apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        windowManager?.addView(floatView, floatWindowLayoutParam)

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        initUI()

        observePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForeground) {
            isForeground = true
            handler.postDelayed({
                startForeground()
            }, 1000)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // It is called when stopService()
    // method is called in MainActivity
    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        mBinding = null
        sessionsChangeListener.deregisterObserver()
        isForeground = false
        super.onDestroy()
        stopSelf()
        // Window is removed from the screen
        windowManager?.removeView(floatView)
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    private fun startForeground() {
        val channelId = Constant.notificationChannelId
        val channel = NotificationChannel(
            channelId,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("")
            .setContentText("")
            .setSilent(true)
            .build()
        startForeground(notificationId, notification)
        notificationId += 1
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initUI() {
        binding.closeButton.setOnClickListener {
            // stopSelf() method is used to stop the service if
            // it was previously started
            stopForeground(true)
            stopSelf()

            // The window is removed from the screen
            windowManager?.removeView(floatView)
        }
        binding.playButton.setOnClickListener {
            if (binding.playButton.isSelected) {
                sendCommand(MediaCommand.PAUSE)
            } else {
                sendCommand(MediaCommand.PLAY)
            }
        }
        binding.nextButton.setOnClickListener {
            sendCommand(MediaCommand.NEXT)
        }
        binding.prevButton.setOnClickListener {
            sendCommand(MediaCommand.PREVIOUS)
        }

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.
        floatView?.setOnTouchListener(windowTouch)
    }

    private fun observePlayer() {
        val listener = ComponentName(this, FloatingControlService::class.java)
        sessionsChangeListener.registerObserver(listener)

        viewModel.musicData.observe(this) {
            setMusicDataToUI(it)
        }
    }

    private fun sendCommand(command: MediaCommand) {
        viewModel.musicData.value?.controller?.get()?.let {
            when (command) {
                MediaCommand.PLAY -> it.transportControls.play()
                MediaCommand.PAUSE -> it.transportControls.pause()
                MediaCommand.PREVIOUS -> it.transportControls.skipToPrevious()
                MediaCommand.NEXT -> it.transportControls.skipToNext()
            }
        }
    }

    private fun setMusicDataToUI(musicData: MusicData?) {
        musicData?.let { data ->
            binding.artistText?.text = data.artistName
            binding.songText?.text = data.trackTitle
            binding.playButton.isSelected = data.playing
            binding.timePositionText.text = positionToText(data.playingPosition)
            binding.timeDurationText.text = positionToText(data.duration)
            data.albumUrl?.let { pair ->
                pair.second?.let {
                    Glide.with(this).clear(binding.backgroundImage)
                    binding.backgroundImage.tag = null
                    binding.backgroundImage.setImageBitmap(it)
                } ?: pair.first?.let {
                    if (binding.backgroundImage.tag != it) {
                        Glide.with(this).clear(binding.backgroundImage)
                        binding.backgroundImage.tag = it
                        Glide.with(this)
                            .load(it)
                            .centerCrop()
                            .into(binding.backgroundImage)
                    }
                } ?: run {
                    binding.backgroundImage.tag = null
                    Glide.with(this).clear(binding.backgroundImage)
                }
            } ?: {
                binding.backgroundImage.tag = null
                Glide.with(this).clear(binding.backgroundImage)
            }
        } ?: run {
            binding.artistText?.text = getString(R.string.msg_need_to_play)
            binding.songText?.text = null
            binding.playButton.isSelected = false
            binding.timePositionText.text = ""
            binding.timeDurationText.text = ""
            binding.backgroundImage.tag = null
            Glide.with(this).clear(binding.backgroundImage)
        }
    }

    private fun positionToText(time: Long): String {
        if (time < 0) {
            return ""
        }
        var text = ""
        var position = time / 1000
        if (position >= 3600) {
            text += "${(position / 3600).toString(10).padStart(2, '0')}:"
            position %= 3600
        }
        if (position >= 60) {
            text += "${(position / 60).toString(10).padStart(2, '0')}:"
        } else {
            text += "00:"
        }
        position %= 60
        return "${text}${position.toString(10).padStart(2, '0')}"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.v("onListenerConnected()")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.v("onListenerDisconnected()")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.packageName?.run {
            Log.v("onNotificationPosted() --> packageName: $this")
        }
    }

    private val windowTouch: View.OnTouchListener by lazy {
        object : View.OnTouchListener {
            private var moveX = 0.0f
            private var moveY = 0.0f
            private var moveWidth = 0.0f
            private var moveHeight = 0.0f
            private var movePx = 0.0f
            private var movePy = 0.0f
            private var resizeMode: ResizeMode? = null
            private val resizeThreshold =
                convertDp2Px(Constant.resizeThreshold, this@FloatingControlService)
            private val minimumWidth =
                convertDp2Px(Constant.controlWindowMinimumSize, this@FloatingControlService)

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                floatWindowLayoutParam?.let { it ->
                    event?.let { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                moveX = it.x.toFloat()
                                moveY = it.y.toFloat()
                                moveWidth = it.width.toFloat()
                                moveHeight = it.height.toFloat()

                                // returns the original raw X
                                // coordinate of this event
                                movePx = event.rawX

                                // returns the original raw Y
                                // coordinate of this event
                                movePy = event.rawY

                                if (abs(event.x - it.width) <= resizeThreshold && abs(event.y - it.height) <= resizeThreshold) {
                                    resizeMode = ResizeMode.BottomRight
                                } else if (abs(event.x) <= resizeThreshold && abs(event.y - it.height) <= resizeThreshold) {
                                    resizeMode = ResizeMode.BottomLeft
                                } else {
                                    resizeMode = null
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                when (resizeMode) {
                                    ResizeMode.BottomRight -> {
                                        it.width =
                                            max(
                                                minimumWidth,
                                                (moveWidth + event.rawX - movePx)
                                            ).toInt()
                                        it.height = it.width
                                    }
                                    ResizeMode.BottomLeft -> {
                                        it.width =
                                            max(
                                                minimumWidth,
                                                (moveWidth + movePx - event.rawX)
                                            ).toInt()
                                        it.height = it.width
                                    }
                                    null -> {
                                        it.x = (moveX + event.rawX - movePx).toInt()
                                        it.y = (moveY + event.rawY - movePy).toInt()
                                    }
                                }
                                // updated parameter is applied to the WindowManager
                                floatView?.let { view ->
                                    windowManager?.updateViewLayout(view, it)
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                resizeMode = null
                            }
                            else -> {

                            }
                        }
                    }
                }
                return false
            }
        }
    }
}