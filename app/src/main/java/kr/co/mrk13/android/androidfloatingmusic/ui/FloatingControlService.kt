package kr.co.mrk13.android.androidfloatingmusic.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import kr.co.mrk13.android.androidfloatingmusic.R
import kr.co.mrk13.android.androidfloatingmusic.constant.Constant
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaApp
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaCommand
import kr.co.mrk13.android.androidfloatingmusic.databinding.FloatingControlBinding
import kr.co.mrk13.android.androidfloatingmusic.model.MusicData
import kr.co.mrk13.android.androidfloatingmusic.receiver.ActiveSessionsChangedListener
import kr.co.mrk13.android.androidfloatingmusic.util.Log
import kr.co.mrk13.android.androidfloatingmusic.util.convertDp2Px
import kr.co.mrk13.android.androidfloatingmusic.util.convertPx2Dp
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


enum class ResizeMode {
    BottomLeft, BottomRight
}

interface MusicDataViewModelObserver {
    fun onMusicDataChange(data: MusicData?)
    fun onPlayingStateChange(data: MusicData?)
}

interface MusicDataViewModel {
    val musicData: MusicData?
    fun musicDataChange(data: MusicData?)

    val musicPlayingStateData: MusicData?
    fun playingStateChange(data: MusicData?)

    fun addObserver(observer: MusicDataViewModelObserver)
    fun removeObserver(observer: MusicDataViewModelObserver)
}

class FloatingControlServiceViewModel(private val app: Context) : MusicDataViewModel {
    private val lock = Any()
    private val observers = ArrayList<MusicDataViewModelObserver>()

    private val pref: SharedPreferences
        get() {
            return app.getSharedPreferences("floating-music-pref", Activity.MODE_PRIVATE)
        }

    private var mutableMusicData: MusicData? = null
        set(value) {
            field = value
            observers.forEach { it.onMusicDataChange(value) }
        }
    override val musicData: MusicData? get() = mutableMusicData

    private var mutableMusicPlayingStateData: MusicData? = null
        set(value) {
            field = value
            observers.forEach { it.onPlayingStateChange(value) }
        }
    override val musicPlayingStateData: MusicData? get() = mutableMusicPlayingStateData

    init {
        pref.getString(Constant.prefPlayedPackageName, null)?.takeIf { it.isNotEmpty() }
            ?.let { packageName ->
                val app = MediaApp.entries.firstOrNull { it.packageName == packageName }
                val data = MusicData(
                    app, packageName, null, null, null, 0L, null, 0L, false,
                    initData = true
                )
                mutableMusicData = data
                mutableMusicPlayingStateData = data
            }
    }

    override fun musicDataChange(data: MusicData?) {
        synchronized(lock) {
            if (data == null && mutableMusicData?.initData == true) {
                return@synchronized
            }
            mutableMusicData = data
        }
    }

    override fun playingStateChange(data: MusicData?) {
        synchronized(lock) {
            mutableMusicPlayingStateData = data
        }

        data?.takeIf { it.playing }?.packageName?.let {
            this.pref.edit {
                putString(Constant.prefPlayedPackageName, it)
            }
        }
    }

    override fun addObserver(observer: MusicDataViewModelObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    override fun removeObserver(observer: MusicDataViewModelObserver) {
        if (observers.contains(observer)) {
            observers.remove(observer)
        }
    }
}

/**
 * @author ross.
 * @see https://android.googlesource.com/platform/packages/apps/Music/+/278aaed1eb37763f7dcb2364523db591014a9210/src/com/android/music/MediaPlaybackService.java
 *
 * @see https://stackoverflow.com/questions/59052978/is-there-any-way-to-fetch-the-info-of-the-current-playing-song
 */
class FloatingControlService : NotificationListenerService(), LifecycleOwner,
    MusicDataViewModelObserver {

    private var floatView: ViewGroup? = null
    private var LAYOUT_TYPE = 0
    private var floatWindowLayoutParam: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    private var mBinding: FloatingControlBinding? = null
    private val binding get() = mBinding!!

    private lateinit var viewModel: FloatingControlServiceViewModel
    private lateinit var sessionsChangeListener: ActiveSessionsChangedListener
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var isForeground = false
    private var initCount = 0
    private val handler = Handler(Looper.getMainLooper())

    private val pref: SharedPreferences
        get() {
            return getSharedPreferences("floating-music-pref", Activity.MODE_PRIVATE)
        }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        viewModel = FloatingControlServiceViewModel(applicationContext)
        sessionsChangeListener =
            ActiveSessionsChangedListener(this, viewModel, sessionComponentName)

        Log.d("onCreate: $isForeground")
        if (!isForeground) {
            isForeground = true
            startForeground()
        }

        initWindow()
        observeSetting()

        viewModel.musicData?.let {
            onMusicDataChange(it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("onStartCommand: $isForeground")
        if (!isForeground) {
            isForeground = true
            startForeground()
            initWindow()
        } else {
            sessionsChangeListener.runObserver()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sessionsChangeListener.runObserver()
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)

        sessionsChangeListener.runObserver()
    }

    // It is called when stopService()
    // method is called in MainActivity
    override fun onDestroy() {
        super.onDestroy()
        stopSelf()
        isForeground = false
        clear()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private fun initWindow() {
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
        LAYOUT_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

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
        var windowWidth = max(
            min(
                convertDp2Px(Constant.controlWindowDefaultSize, applicationContext),
                width * 0.3f
            ),
            convertDp2Px(Constant.controlWindowMinimumWidth, applicationContext)
        ).toInt()
        var windowHeight = windowWidth
        var posX = 0
        var posY = 0
        var gravity = Gravity.CENTER

        pref.getString(Constant.prefWindowPosition, null)?.let {
            Rect.unflattenFromString(it)
        }?.let {
            posX = it.left
            posY = it.top
            windowWidth = it.width()
            windowHeight = it.height()
            gravity = Gravity.NO_GRAVITY
        }
        floatWindowLayoutParam = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            LAYOUT_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL),
            PixelFormat.TRANSLUCENT
        )

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen
        floatWindowLayoutParam?.apply {
            this.gravity = gravity
            x = posX
            y = posY
        }

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        try {
            windowManager?.addView(floatView, floatWindowLayoutParam)
        } catch (e: Throwable) {
            Log.e("error", e)
            if (e is WindowManager.BadTokenException || (e as? RuntimeException)?.cause is WindowManager.BadTokenException) {
                val weak = WeakReference(this@FloatingControlService)
                handler.postDelayed({
                    weak.get()?.let {
                        if (it.isForeground) {
                            it.initCount += 1
                            if (it.initCount <= 3) {
                                it.initWindow()
                            } else {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                                clear()
                            }
                        }
                    }
                }, 1000L)
            }
        }
        initCount = 0
        setUIVisibility(windowWidth, windowHeight)

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        initUI()

        observePlayer()

        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, intentFilter)
    }

    private fun clear() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        mBinding = null

        try {
            unobservePlayer()
        } catch (e: Throwable) {
            Log.d(e.message)
        }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Throwable) {
            Log.d(e.message)
        }
        try {
            floatView?.removeOnLayoutChangeListener(windowLayoutChange)
            // Window is removed from the screen
            windowManager?.removeView(floatView)
        } catch (e: Throwable) {
            Log.d(e.message)
        }
        isForeground = false
    }

    private fun startForeground() {
        val channelId = Constant.notificationChannelId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("")
            .setContentText("")
            .setSilent(true)
            .build()
        startForeground(920, notification)
    }

    private var settingListener = object : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            try {
                @Suppress("SENSELESS_COMPARISON")
                if (binding == null) {
                    return
                }
                sharedPreferences?.let { prefs ->
                    when (key) {
                        "title_fontsize", "button_size", "show_album", "time_fontsize", "view_opacity", "top_button_opacity", "show_clock", "clock_fontsize" -> {
                            setPreferencesToUI(prefs)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("onSharedPreferenceChanged", e)
            }
        }

    }

    private fun observeSetting() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(settingListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initUI() {
        binding.artistText.setHorizontallyScrolling(true)
        binding.artistText.isSelected = true
        binding.songText.setHorizontallyScrolling(true)
        binding.songText.isSelected = true
        binding.albumTitle.setHorizontallyScrolling(true)
        binding.albumTitle.isSelected = true

        binding.closeButton.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(
                this@FloatingControlService,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()

                        clear()

                        val weak = WeakReference(this@FloatingControlService)
                        handler.postDelayed({
                            weak.get()?.let {
                                if (!it.isForeground) {
                                    it.isForeground = true
                                    it.startForeground()
                                    it.initWindow()
                                }
                            }
                        }, 1000L)
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        // stopSelf() method is used to stop the service if
                        // it was previously started
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()

                        clear()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        binding.root.visibility = View.GONE

                        val weak = WeakReference(this@FloatingControlService)
                        handler.postDelayed({
                            weak.get()?.let {
                                try {
                                    if (it.isForeground) {
                                        it.binding.root.visibility = View.VISIBLE
                                    }
                                } catch (ignore: Throwable) {
                                }
                            }
                        }, 60 * 1000L)
                    }
                })

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event?.let {
                    gestureDetector.onTouchEvent(it)
                }
                return true
            }
        })
        binding.settingButton.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        binding.launchButton.setOnClickListener {
            try {
                viewModel.musicData?.packageName?.let { packageName ->
                    var intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent == null) {
                        val mainIntent = Intent()
                        mainIntent.setPackage(packageName)
                        val appList = packageManager.queryIntentActivities(
                            mainIntent,
                            PackageManager.MATCH_ALL
                        )
                        appList.firstOrNull()?.let {
                            val activity = it.activityInfo
                            val name = ComponentName(
                                activity.applicationInfo.packageName,
                                activity.name
                            )
                            val i = Intent(Intent.ACTION_MAIN)
                            i.component = name
                            intent = i
                        }
                    }
                    intent?.let {
                        it.flags
                        startActivity(it)
                    }
                }
            } catch (e: Throwable) {
                Log.e("launch error", e)
            }
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
        binding.clockView.format12Hour = null
        binding.clockView.format24Hour = "kk:mm"

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.
        floatView?.setOnTouchListener(windowTouch)

        floatView?.addOnLayoutChangeListener(windowLayoutChange)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setPreferencesToUI(prefs)
    }

    private fun setPreferencesToUI(prefs: SharedPreferences) {
        prefs.getString("title_fontsize", "12")?.toIntOrNull()?.let { size ->
            binding.artistText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            binding.songText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            binding.albumTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        }
        prefs.getString("button_size", "30")?.toIntOrNull()?.let { size ->
            val buttonSize = convertDp2Px(size, this).toInt()
            binding.buttonLayout.layoutParams =
                (binding.buttonLayout.layoutParams as? LinearLayout.LayoutParams)?.let {
                    it.height = buttonSize
                    it
                }
            binding.playButton.layoutParams =
                (binding.playButton.layoutParams as? RelativeLayout.LayoutParams)?.let {
                    it.width = buttonSize
                    it.height = buttonSize
                    it
                }
            binding.prevButton.layoutParams =
                (binding.prevButton.layoutParams as? RelativeLayout.LayoutParams)?.let {
                    it.width = buttonSize
                    it.height = buttonSize
                    it
                }
            binding.nextButton.layoutParams =
                (binding.nextButton.layoutParams as? RelativeLayout.LayoutParams)?.let {
                    it.width = buttonSize
                    it.height = buttonSize
                    it
                }
        }
        prefs.getString("time_fontsize", "12")?.toIntOrNull()?.let { size ->
            binding.timePositionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            binding.timeDurationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        }
        prefs.getString("view_opacity", "0.7")?.toFloatOrNull()?.let { size ->
            binding.backgroundImage.alpha = min(1.0f, max(0.0f, size))
        }
        prefs.getString("top_button_opacity", "1.0")?.toFloatOrNull()?.let { size ->
            binding.launchButton.alpha = min(1.0f, max(0.0f, size))
            binding.settingButton.alpha = min(1.0f, max(0.0f, size))
            binding.closeButton.alpha = min(1.0f, max(0.0f, size))
        }
        prefs.getString("show_album", "1")?.toIntOrNull()?.let { flag ->
            binding.albumTitle.tag = flag == 1
            if (binding.albumTitle.isVisible && flag != 1) {
                binding.albumTitle.visibility = View.GONE
            } else if (binding.albumTitle.isGone && flag == 1 && !binding.albumTitle.text.isNullOrEmpty()) {
                binding.albumTitle.visibility = View.VISIBLE
            }
        }
        prefs.getString("show_clock", "0")?.toIntOrNull()?.let { flag ->
            binding.clockView.visibility = if (flag == 1) View.VISIBLE else View.GONE
        }
        prefs.getString("clock_fontsize", "20")?.toIntOrNull()?.let { size ->
            binding.clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            val weakSelf = WeakReference(this@FloatingControlService)
            handler.postDelayed({
                weakSelf.get()?.let {
                    try {
                        val height =
                            if (it.binding.clockView.isVisible) max(
                                0,
                                it.binding.clockView.height - convertDp2Px(15, it).toInt()
                            ) else 0
                        it.binding.centerLayout.setPadding(
                            0,
                            height,
                            0,
                            height
                        )
                    } catch (ignore: Throwable) {
                    }
                }
            }, 500L)
        }
    }

    private fun setUIVisibility(width: Int, height: Int) {
        val widthDP = convertPx2Dp(width, this)
//        val heightDP = convertPx2Dp(height, this)
        binding.timePositionText.visibility = View.VISIBLE
        binding.timeDurationText.visibility = View.VISIBLE
        binding.prevButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.VISIBLE
    }

    private val sessionComponentName: ComponentName
        get() {
            return ComponentName(this, FloatingControlService::class.java)
        }

    private fun observePlayer() {
        viewModel.addObserver(this)

        sessionsChangeListener.registerObserver()
    }

    private fun unobservePlayer() {
        viewModel.removeObserver(this)

        sessionsChangeListener.deregisterObserver()
    }

    private fun sendCommand(command: MediaCommand) {
        viewModel.musicData?.getController(this, sessionComponentName)?.let {
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
            if (data.initData) {
                binding.artistText.text = getString(R.string.msg_need_to_play)
                binding.songText.text = null
                binding.albumTitle.text = null
                binding.albumTitle.visibility = View.GONE
            } else {
                binding.artistText.text = data.artistName
                binding.songText.text = data.trackTitle
                binding.albumTitle.text = data.albumTitle
                binding.albumTitle.visibility =
                    if (data.albumTitle.isNullOrEmpty() || binding.albumTitle.tag as? Boolean == false) View.GONE else View.VISIBLE
            }
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
            } ?: run {
                binding.backgroundImage.tag = null
                Glide.with(this).clear(binding.backgroundImage)
            }
            binding.launchButton.visibility = data.packageName?.takeIf { name ->
                MediaApp.entries.firstOrNull { it.packageName == name } != null
            }?.let {
                View.VISIBLE
            } ?: View.GONE
        } ?: run {
            binding.artistText.text = getString(R.string.msg_need_to_play)
            binding.songText.text = null
            binding.timeDurationText.text = ""
            binding.backgroundImage.tag = null
            Glide.with(this).clear(binding.backgroundImage)
            binding.launchButton.visibility = View.GONE
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
        text += if (position >= 60) {
            "${(position / 60).toString(10).padStart(2, '0')}:"
        } else {
            "00:"
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

    override fun onMusicDataChange(data: MusicData?) {
        setMusicDataToUI(data)
    }

    override fun onPlayingStateChange(data: MusicData?) {
        binding.playButton.isSelected = data?.playing ?: false
        binding.timePositionText.text = positionToText(data?.playingPosition ?: -1)
        data?.playingPosition?.let { pos ->
            if (pos >= 0 && data.duration > 0) {
                val progress = (pos.toDouble() / data.duration).toFloat()
                (binding.timeSlider.layoutParams as? LinearLayout.LayoutParams)?.weight =
                    progress
                (binding.timeSliderHolder.layoutParams as? LinearLayout.LayoutParams)?.weight =
                    1 - progress
            }
        } ?: run {
            (binding.timeSlider.layoutParams as? LinearLayout.LayoutParams)?.weight = 0f
            (binding.timeSliderHolder.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f
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
                convertDp2Px(Constant.controlWindowMinimumWidth, this@FloatingControlService)
            private val minimumHeight =
                convertDp2Px(Constant.controlWindowMinimumHeight, this@FloatingControlService)

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
                                        it.height =
                                            max(
                                                minimumHeight,
                                                (moveHeight + event.rawY - movePy)
                                            ).toInt()
                                    }

                                    ResizeMode.BottomLeft -> {
                                        it.width =
                                            max(
                                                minimumWidth,
                                                (moveWidth + movePx - event.rawX)
                                            ).toInt()
                                        it.height =
                                            max(
                                                minimumHeight,
                                                (moveHeight + movePy - event.rawY)
                                            ).toInt()
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

                                val rect = Rect(it.x, it.y, it.x + it.width, it.y + it.height)
                                val pref = this@FloatingControlService.pref.edit()
                                pref.putString(Constant.prefWindowPosition, rect.flattenToString())
                                pref.apply()
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

    private val windowLayoutChange =
        View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom - top != oldBottom - oldTop || right - left != oldRight - oldLeft) {
                setUIVisibility(right - left, bottom - top)
            }
        }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let {
                if (Intent.ACTION_SCREEN_ON == it) {
                    observePlayer()
                } else if (Intent.ACTION_SCREEN_OFF == it) {
                    unobservePlayer()
                }
            }
        }

    }
}