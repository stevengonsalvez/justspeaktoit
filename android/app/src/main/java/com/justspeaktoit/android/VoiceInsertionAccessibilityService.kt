package com.justspeaktoit.android

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.lang.ref.WeakReference

class VoiceInsertionAccessibilityService : AccessibilityService() {
    private var focusedNode: AccessibilityNodeInfo? = null
    private var focusedPackage: String? = null

    override fun onServiceConnected() {
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val source = event.source ?: return
        if (source.isEditableTextField()) {
            focusedNode?.recycle()
            focusedNode = AccessibilityNodeInfo.obtain(source)
            focusedPackage = event.packageName?.toString()
        } else {
            source.recycle()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        focusedNode?.recycle()
        focusedNode = null
        if (activeService?.get() == this) activeService = null
        super.onDestroy()
    }

    fun insertText(text: String): VoiceInsertionResult {
        val target = focusedNode?.takeIf { it.isEditableTextField() }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return VoiceInsertionResult(VoiceInsertionMethod.Failed, "No editable field is focused", text)
        if (!target.isEditableTextField()) {
            return VoiceInsertionResult(VoiceInsertionMethod.Failed, "Focused field does not accept text", text)
        }
        val existing = target.text?.toString().orEmpty()
        val start = target.textSelectionStart.takeIf { it >= 0 } ?: existing.length
        val end = target.textSelectionEnd.takeIf { it >= 0 } ?: start
        val planned = TextInsertionPlanner.insertAtSelection(existing, start, end, text)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, planned.text)
        }
        val changed = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        val packageName = focusedPackage ?: target.packageName?.toString().orEmpty()
        return if (changed) {
            target.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, planned.cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, planned.cursor)
                }
            )
            VoiceInsertionResult(
                method = VoiceInsertionMethod.Accessibility,
                message = "Inserted into ${packageName.ifBlank { "focused app" }}",
                insertedText = text
            )
        } else {
            VoiceInsertionResult(VoiceInsertionMethod.Failed, "Focused app rejected accessibility insertion", text)
        }
    }

    private fun AccessibilityNodeInfo.isEditableTextField(): Boolean {
        return isEditable || className?.contains("EditText", ignoreCase = true) == true
    }

    companion object {
        @Volatile
        private var activeService: WeakReference<VoiceInsertionAccessibilityService>? = null

        fun active(): VoiceInsertionAccessibilityService? = activeService?.get()
    }
}

class VoiceInsertionCoordinator(private val context: Context) {
    fun insertOrFallback(text: String, clipboardFallbackEnabled: Boolean): VoiceInsertionResult {
        if (text.isBlank()) {
            return VoiceInsertionResult(VoiceInsertionMethod.Failed, "No dictated text to insert", text)
        }
        val direct = VoiceInsertionAccessibilityService.active()?.insertText(text)
        if (direct?.success == true) return direct
        if (!clipboardFallbackEnabled) {
            return direct ?: VoiceInsertionResult(VoiceInsertionMethod.Failed, "Accessibility insertion is unavailable", text)
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Just Speak to It dictation", text))
        Toast.makeText(context, "Copied dictation. Paste into the focused field.", Toast.LENGTH_LONG).show()
        return VoiceInsertionResult(
            method = VoiceInsertionMethod.ClipboardFallback,
            message = "Accessibility unavailable; copied for paste recovery",
            insertedText = text
        )
    }
}
