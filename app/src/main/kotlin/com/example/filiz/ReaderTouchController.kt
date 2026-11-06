package com.example.filiz

import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

internal const val NOTE_LONG_PRESS_MS = 820L

internal class ReaderTouchController(
    private val handler: Handler
) {
    private var touchActive: Boolean = false
    private var touchRawX: Float = 0f
    private var touchRawY: Float = 0f
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchDownOffset: Int = -1
    private var longPressRunnable: Runnable? = null
    private var lastTapAt: Long = 0L
    private var lastTapX: Float = 0f
    private var lastTapY: Float = 0f

    fun hasPendingLongPress(): Boolean = touchActive

    fun onDocumentDown(
        event: MotionEvent,
        offset: Int,
        noteSelectionActive: Boolean,
        noteSelectionPendingCommit: Boolean,
        canStartNoteSelection: (Int) -> Boolean,
        documentView: View,
        onLongPress: (Int) -> Unit
    ) {
        cancelLongPress()
        touchActive = false
        touchRawX = event.rawX
        touchRawY = event.rawY
        touchDownX = event.x
        touchDownY = event.y
        touchDownOffset = offset
        if (noteSelectionActive || noteSelectionPendingCommit || !canStartNoteSelection(touchDownOffset)) {
            touchActive = false
            return
        }
        touchActive = true
        longPressRunnable = Runnable {
            if (!touchActive) return@Runnable
            if (!canStartNoteSelection(touchDownOffset)) return@Runnable
            documentView.parent?.requestDisallowInterceptTouchEvent(true)
            documentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress(touchDownOffset)
        }.also {
            handler.postDelayed(it, NOTE_LONG_PRESS_MS)
        }
    }

    fun onDocumentMove(event: MotionEvent, noteSelectionActive: Boolean, cancelDistancePx: Int) {
        if (noteSelectionActive) return
        val movedDistance = hypot(
            (event.x - touchDownX).toDouble(),
            (event.y - touchDownY).toDouble()
        )
        if (movedDistance > cancelDistancePx.toDouble()) {
            touchActive = false
            cancelLongPress()
        }
    }

    fun onDocumentUpOrCancel() {
        touchActive = false
        cancelLongPress()
    }

    fun trackPendingLongPress(event: MotionEvent, cancelDistancePx: Int, noteSelectionActive: Boolean) {
        if (!touchActive || noteSelectionActive) return
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val moved = hypot(
                    (event.rawX - touchRawX).toDouble(),
                    (event.rawY - touchRawY).toDouble()
                )
                if (moved > cancelDistancePx.toDouble()) {
                    touchActive = false
                    cancelLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onDocumentUpOrCancel()
        }
    }

    fun cancelPendingLongPress() {
        touchActive = false
        cancelLongPress()
    }

    fun isDoubleTap(event: MotionEvent, distancePx: Int): Boolean {
        val now = System.currentTimeMillis()
        val isDoubleTap = now - lastTapAt in 40..320 &&
            kotlin.math.abs(event.x - lastTapX) < distancePx &&
            kotlin.math.abs(event.y - lastTapY) < distancePx
        lastTapAt = now
        lastTapX = event.x
        lastTapY = event.y
        return isDoubleTap
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }
}
