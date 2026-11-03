package com.example.taptix.automation.safety

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Formal safety states for the centralized Automation Safety Controller.
 */
enum class AutomationSafetyState {
    /** Automation disabled. No booking detection, clicks, or gestures permitted. */
    OFF,

    /** Automation enabled and eligible to process supported offers. */
    ON,

    /** Immediate emergency cancellation in progress. All actions blocked. */
    STOPPING,

    /** Automation cannot safely continue (e.g. service disconnected or latched). */
    BLOCKED
}

/**
 * Centralized Automation Safety Controller & Emergency Kill Switch.
 *
 * Guarantees:
 * 1. Single source of truth for automation readiness.
 * 2. Idempotent, thread-safe Emergency STOP mechanism.
 * 3. Monotonic generation counter to invalidate in-flight / queued callbacks.
 * 4. Persistent STOP latch preventing silent auto-resume across service rebinds or restarts.
 * 5. Fail-safe: if state is uncertain, automation remains strictly OFF/BLOCKED.
 */
object AutomationSafetyController {

    private const val TAG = "SafetyController"
    private const val PREFS_NAME = "taptix_safety_prefs"
    private const val KEY_STOP_LATCHED = "key_emergency_stop_latched"

    private val currentState = AtomicReference(AutomationSafetyState.OFF)
    private val generationCounter = AtomicLong(1L)
    private val stopLatched = AtomicBoolean(false)
    private var appContext: Context? = null

    // Callbacks registered to cancel active operations when emergency STOP fires
    private val stopListeners = CopyOnWriteArrayList<() -> Unit>()
    private val stateChangeListeners = CopyOnWriteArrayList<(AutomationSafetyState) -> Unit>()

    val state: AutomationSafetyState
        get() = currentState.get()

    val currentGeneration: Long
        get() = generationCounter.get()

    val isStopLatched: Boolean
        get() = stopLatched.get()

    /**
     * Initializes the controller with application context for latch persistence.
     */
    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = getPrefs()
        val latched = prefs?.getBoolean(KEY_STOP_LATCHED, false) ?: false
        stopLatched.set(latched)

        if (latched) {
            currentState.set(AutomationSafetyState.OFF)
            logI("Initialized in latched STOP state. Automation remains OFF.")
        } else {
            currentState.set(AutomationSafetyState.OFF)
        }
        notifyStateChanged(currentState.get())
    }

    /**
     * Evaluates whether screen traversal and booking candidate inspection are permitted.
     */
    fun canProcessScreen(): Boolean {
        return currentState.get() == AutomationSafetyState.ON
    }

    /**
     * Evaluates whether an automated click or gesture is permitted.
     * Enforces BOTH that the controller is in the [AutomationSafetyState.ON] state
     * AND that the caller's [actionGeneration] matches [currentGeneration].
     */
    fun canExecuteAction(actionGeneration: Long): Boolean {
        if (currentState.get() != AutomationSafetyState.ON) {
            return false
        }
        return actionGeneration == generationCounter.get()
    }

    /**
     * Executes an immediate, idempotent emergency STOP.
     *
     * 1. Transitions to STOPPING.
     * 2. Advances generation counter (invalidates all queued and executing tokens).
     * 3. Triggers registered cancellation handlers (aborts gestures, clears queues, resets state machines).
     * 4. Latches the STOP state to disk.
     * 5. Transitions to OFF.
     */
    @Synchronized
    fun emergencyStop(reason: String = "USER_EMERGENCY_STOP"): Boolean {
        // Idempotency: If already OFF and stop is latched, one logical STOP is already established.
        // Redundant / cascading stop calls are suppressed without side-effects.
        if (currentState.get() == AutomationSafetyState.OFF && stopLatched.get()) {
            logD("Emergency STOP already active and latched. Redundant request suppressed: reason=$reason")
            return true
        }

        logW("EMERGENCY STOP TRIGGERED: reason=$reason, gen=${generationCounter.get()}")

        // 1. Enter STOPPING immediately
        currentState.set(AutomationSafetyState.STOPPING)
        notifyStateChanged(AutomationSafetyState.STOPPING)

        // 2. Increment generation counter to atomically invalidate all in-flight tokens
        generationCounter.incrementAndGet()

        // 3. Latch the STOP state persistently
        stopLatched.set(true)
        persistLatch(true)

        // 4. Invalidate all active tasks via registered listeners
        for (listener in stopListeners) {
            try {
                listener.invoke()
            } catch (t: Throwable) {
                logE("Error in emergency stop listener: ${t.message}", t)
            }
        }

        // 5. Complete transition to OFF
        currentState.set(AutomationSafetyState.OFF)
        notifyStateChanged(AutomationSafetyState.OFF)

        logI("Emergency STOP completed safely. State=OFF, Latched=true, NextGen=${generationCounter.get()}")
        return true
    }

    /**
     * Requests enabling automation.
     * Requires explicit user intent, verified accessibility availability, and active master switch.
     */
    @Synchronized
    fun requestEnable(
        hasAccessibilityService: Boolean,
        masterEnabled: Boolean,
        isUserExplicitAction: Boolean = false
    ): Boolean {
        // Silent re-enable prevented if STOP is latched unless user explicitly clicked enable
        if (stopLatched.get() && !isUserExplicitAction) {
            logW("Enable rejected: Emergency STOP is latched. Explicit user action required.")
            currentState.set(AutomationSafetyState.OFF)
            notifyStateChanged(AutomationSafetyState.OFF)
            return false
        }

        if (!hasAccessibilityService) {
            logW("Enable rejected: Accessibility service is unavailable.")
            currentState.set(AutomationSafetyState.BLOCKED)
            notifyStateChanged(AutomationSafetyState.BLOCKED)
            return false
        }

        if (!masterEnabled) {
            logD("Enable rejected: Master control setting is disabled.")
            currentState.set(AutomationSafetyState.OFF)
            notifyStateChanged(AutomationSafetyState.OFF)
            return false
        }

        // Clear latch on explicit user enable
        if (isUserExplicitAction) {
            stopLatched.set(false)
            persistLatch(false)
        }

        // Advance generation to ensure clean state
        generationCounter.incrementAndGet()
        currentState.set(AutomationSafetyState.ON)
        notifyStateChanged(AutomationSafetyState.ON)

        logI("Automation enabled: State=ON, Gen=${generationCounter.get()}")
        return true
    }

    /**
     * Requests normal user disabling of automation (non-emergency).
     */
    @Synchronized
    fun requestDisable(reason: String = "USER_DISABLED"): Boolean {
        generationCounter.incrementAndGet()
        currentState.set(AutomationSafetyState.OFF)

        for (listener in stopListeners) {
            try {
                listener.invoke()
            } catch (t: Throwable) {
                logE("Error in disable listener: ${t.message}", t)
            }
        }

        notifyStateChanged(AutomationSafetyState.OFF)
        logI("Automation disabled: reason=$reason, State=OFF")
        return true
    }

    /**
     * Called when accessibility service binds or connects.
     * Prevents silent auto-resume if latched or previously OFF.
     */
    @Synchronized
    fun notifyServiceConnected(masterEnabled: Boolean) {
        logD("Service connected: latched=${stopLatched.get()}, master=$masterEnabled")
        if (stopLatched.get()) {
            currentState.set(AutomationSafetyState.OFF)
            notifyStateChanged(AutomationSafetyState.OFF)
            return
        }

        if (!masterEnabled) {
            currentState.set(AutomationSafetyState.OFF)
            notifyStateChanged(AutomationSafetyState.OFF)
            return
        }

        // If previously blocked due to missing service, transition to OFF (await explicit enable)
        if (currentState.get() == AutomationSafetyState.BLOCKED) {
            currentState.set(AutomationSafetyState.OFF)
            notifyStateChanged(AutomationSafetyState.OFF)
        }
    }

    /**
     * Called when accessibility service unbinds or disconnects.
     * Fails safely to BLOCKED.
     */
    @Synchronized
    fun notifyServiceDisconnected() {
        logW("Service disconnected. Transitioning to BLOCKED.")
        generationCounter.incrementAndGet()
        currentState.set(AutomationSafetyState.BLOCKED)

        for (listener in stopListeners) {
            try {
                listener.invoke()
            } catch (t: Throwable) {
                logE("Error in disconnect listener: ${t.message}", t)
            }
        }
        notifyStateChanged(AutomationSafetyState.BLOCKED)
    }

    fun registerStopListener(listener: () -> Unit) {
        if (!stopListeners.contains(listener)) {
            stopListeners.add(listener)
        }
    }

    fun unregisterStopListener(listener: () -> Unit) {
        stopListeners.remove(listener)
    }

    fun registerStateChangeListener(listener: (AutomationSafetyState) -> Unit) {
        if (!stateChangeListeners.contains(listener)) {
            stateChangeListeners.add(listener)
            listener(currentState.get())
        }
    }

    fun unregisterStateChangeListener(listener: (AutomationSafetyState) -> Unit) {
        stateChangeListeners.remove(listener)
    }

    /**
     * Resets controller state for unit test isolation.
     */
    internal fun resetForTesting(initialState: AutomationSafetyState = AutomationSafetyState.OFF) {
        stopListeners.clear()
        stateChangeListeners.clear()
        stopLatched.set(false)
        generationCounter.set(1L)
        currentState.set(initialState)
    }

    private fun notifyStateChanged(newState: AutomationSafetyState) {
        for (listener in stateChangeListeners) {
            try {
                listener.invoke(newState)
            } catch (t: Throwable) {
                logE("Error in state change listener: ${t.message}", t)
            }
        }
    }

    private fun getPrefs(): SharedPreferences? {
        val ctx = appContext ?: return null
        return try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    private fun persistLatch(latched: Boolean) {
        try {
            getPrefs()?.edit()?.putBoolean(KEY_STOP_LATCHED, latched)?.apply()
        } catch (_: Throwable) {
            // Null-safe for unit testing without Android context
        }
    }

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logE(msg: String, tr: Throwable? = null) {
        try { Log.e(TAG, msg, tr) } catch (_: Throwable) {}
    }
}
