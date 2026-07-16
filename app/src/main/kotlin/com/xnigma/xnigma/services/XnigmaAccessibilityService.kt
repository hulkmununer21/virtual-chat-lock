package com.xnigma.xnigma.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView

class XnigmaAccessibilityService : AccessibilityService() {

    private val XNIGMA_PREFIX = "[XG]"
    private val XNIGMA_SUFFIX = "[/XG]"

    private lateinit var windowManager: WindowManager
    private var masterLensIcon: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    companion object {
        var currentTargetInputField: AccessibilityNodeInfo? = null

        // STABLE INJECTION: Clipboard + 300ms delay to allow host app to wake up
        fun injectCipherText(context: Context, cipherText: String) {
            if (currentTargetInputField != null) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Xnigma Cipher", cipherText))

                    Handler(Looper.getMainLooper()).postDelayed({
                        currentTargetInputField?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }, 300)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createMasterLensIcon()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createMasterLensIcon() {
        masterLensIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_secure) 
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(30, 30, 30, 30)
            elevation = 15f
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 500
        }

        addLensPhysics(masterLensIcon!!, layoutParams!!)
        windowManager.addView(masterLensIcon, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addLensPhysics(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX > 15 || diffY > 15) {
                        isDragging = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val intent = Intent(this, com.xnigma.xnigma.ui.OverlayActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                    } else {
                        // STABLE SCAN: Just scan the whole screen, don't worry about X/Y coordinates
                        performStableScan()
                        params.x = 0
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun performStableScan() {
        val rootNode = rootInActiveWindow ?: return
        val payload = extractCipherFromNode(rootNode)
        
        if (payload != null) {
            val intent = Intent(this, com.xnigma.xnigma.ui.DecryptionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("EXTRA_PAYLOAD", payload)
            }
            startActivity(intent)
        }
    }

    // Brute-force recursive search that guarantees we find the payload if it's on screen
    private fun extractCipherFromNode(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        if (node.text != null) {
            val text = node.text.toString()
            if (text.contains(XNIGMA_PREFIX) && text.contains(XNIGMA_SUFFIX)) {
                val start = text.indexOf(XNIGMA_PREFIX)
                val end = text.indexOf(XNIGMA_SUFFIX) + XNIGMA_SUFFIX.length
                return text.substring(start, end)
            }
        }
        
        for (i in 0 until node.childCount) {
            val res = extractCipherFromNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source
                if (node != null && node.isEditable) {
                    val packageName = node.packageName?.toString() ?: ""
                    if (packageName != "com.xnigma.xnigma") {
                        currentTargetInputField = node
                        masterLensIcon?.post { masterLensIcon?.visibility = View.VISIBLE }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        masterLensIcon?.let { windowManager.removeView(it) }
    }
}