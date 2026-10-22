package com.asd.player

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * ASD è sullo schermo? In background Android può togliere la rete all'app: dopo il login Google
 * nel browser si aspetta che l'utente torni qui prima di parlare con Drive.
 */
object AppForeground : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun init(app: Application) = app.registerActivityLifecycleCallbacks(this)

    suspend fun await() {
        visible.first { it }
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        _visible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        _visible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
