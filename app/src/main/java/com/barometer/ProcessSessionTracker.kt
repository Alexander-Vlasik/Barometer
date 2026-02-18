package com.barometer

import java.util.concurrent.atomic.AtomicBoolean

object ProcessSessionTracker {
    private val uiSeen = AtomicBoolean(false)
    private val firstWorkerSeen = AtomicBoolean(false)

    fun markUiStarted(): Boolean {
        return uiSeen.compareAndSet(false, true)
    }

    fun markWorkerStartAndIsColdStartByWorker(): Boolean {
        val firstWorkerInProcess = firstWorkerSeen.compareAndSet(false, true)
        return firstWorkerInProcess && !uiSeen.get()
    }
}
