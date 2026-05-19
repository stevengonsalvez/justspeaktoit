package com.justspeaktoit.android

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class VoiceBubbleOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val repository by lazy { SpeakRepository(this) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                hideBubble()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH, ACTION_SHOW, null -> showBubbleIfAllowed()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideBubble()
        super.onDestroy()
    }

    private fun showBubbleIfAllowed() {
        val settings = repository.loadSettings()
        if (!settings.flowBubbleEnabled || settings.flowBubbleSnoozed) {
            hideBubble()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            hideBubble()
            return
        }
        if (bubbleView != null) {
            renderBubble(settings)
            return
        }
        val size = (64 * settings.flowBubbleSizePercent.coerceIn(0.75f, 1.35f)).roundToInt()
        val view = buildBubbleView(settings)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 220
        }
        bubbleView = view
        layoutParams = params
        windowManager.addView(view, params)
    }

    private fun buildBubbleView(settings: SpeakSettings): View {
        val label = TextView(this).apply {
            text = "J"
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            background = bubbleBackground(settings.flowBubbleOpacity)
        }
        renderBubble(settings, label)
        return FrameLayout(this).apply {
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnClickListener { toggleRecordingFromBubble() }
            setOnLongClickListener {
                Toast.makeText(context, "Push-to-talk started from Flow Bubble", Toast.LENGTH_SHORT).show()
                toggleRecordingFromBubble()
                true
            }
            setOnTouchListener(DragTouchListener())
        }
    }

    private fun renderBubble(settings: SpeakSettings, label: TextView? = null) {
        val textView = label ?: ((bubbleView as? FrameLayout)?.getChildAt(0) as? TextView) ?: return
        textView.text = when {
            !settings.flowBubbleClipboardFallbackEnabled -> "!"
            settings.flowBubblePhraseStartEnabled -> "S"
            else -> "J"
        }
        textView.alpha = settings.flowBubbleOpacity.coerceIn(0.6f, 1.0f)
    }

    private fun bubbleBackground(opacity: Float): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0D7C81.toInt(), 0xFF6A4C84.toInt())).apply {
            shape = GradientDrawable.OVAL
            alpha = (opacity.coerceIn(0.6f, 1.0f) * 255).roundToInt()
            setStroke(4, 0xFFFFFFFF.toInt())
        }
    }

    private fun toggleRecordingFromBubble() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_TOGGLE_RECORDING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun hideBubble() {
        val view = bubbleView ?: return
        windowManager.removeView(view)
        bubbleView = null
        layoutParams = null
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX - (event.rawX - touchX)).roundToInt()
                    params.y = (startY - (event.rawY - touchY)).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    params.x = if (params.x > 160) 24 else params.x.coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    false
                }
                else -> false
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.justspeaktoit.android.action.SHOW_FLOW_BUBBLE"
        const val ACTION_HIDE = "com.justspeaktoit.android.action.HIDE_FLOW_BUBBLE"
        const val ACTION_REFRESH = "com.justspeaktoit.android.action.REFRESH_FLOW_BUBBLE"

        fun overlaySettingsIntent(packageName: String): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
