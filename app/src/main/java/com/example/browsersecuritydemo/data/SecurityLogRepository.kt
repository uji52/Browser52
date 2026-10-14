package com.example.browsersecuritydemo.data

import android.os.Handler
import android.os.Looper
import com.example.browsersecuritydemo.model.InterceptedData
import java.util.concurrent.CopyOnWriteArrayList

object SecurityLogRepository {
    private val logs = CopyOnWriteArrayList<InterceptedData>()
    private val listeners = CopyOnWriteArrayList<(InterceptedData) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addLog(data: InterceptedData) {
        logs.add(0, data) // newest first
        mainHandler.post {
            for (listener in listeners) {
                listener.invoke(data)
            }
        }
    }

    fun getLogs(): List<InterceptedData> {
        return logs.toList()
    }

    fun clear() {
        logs.clear()
    }

    fun addListener(listener: (InterceptedData) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (InterceptedData) -> Unit) {
        listeners.remove(listener)
    }
}
