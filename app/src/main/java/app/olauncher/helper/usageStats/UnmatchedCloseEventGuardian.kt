package app.olauncher.helper.usageStats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log

/**
 * "…a diminutive Guardian who traveled backward through time…"
 *
 * Guards [EventLogWrapper] against Faulty unmatched close events (per
 * [the documentation](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios))
 * by seeking backwards through time and scanning for the open event.
 */
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 // 24 hours
    }

    // Cache parsed events for a given queryStart to avoid redundant 24h IPC queries.
    // Multiple test() calls within the same getForegroundStatsByTimestamps invocation
    // share the same queryStart, so this eliminates 3-10x redundant queries.
    private var cachedQueryStart: Long = -1L
    private var cachedEvents: List<Triple<Int, String, Long>>? = null

    /**
     * Query events from the system and parse them into a lightweight list of triples
     * (eventType, packageName, timestamp) that can be replayed per-package.
     */
    private fun queryAndParseEvents(queryStart: Long): List<Triple<Int, String, Long>> {
        val usageEvents = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart)
        val result = mutableListOf<Triple<Int, String, Long>>()
        val e = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(e)
            result.add(Triple(e.eventType, e.packageName, e.timeStamp))
        }

        return result
    }

    /**
     * @param event      Event to validate
     * @param queryStart Timestamp at which original query started
     * @return True if the event is valid, false otherwise
     */
    fun test(event: UsageEvents.Event, queryStart: Long): Boolean {
        val events = if (queryStart == cachedQueryStart && cachedEvents != null) {
            cachedEvents!!
        } else {
            val queried = queryAndParseEvents(queryStart)
            cachedQueryStart = queryStart
            cachedEvents = queried
            queried
        }

        // Track whether the package is currently in foreground or background
        var open = false // Not open until opened

        for ((eventType, packageName, timestamp) in events) {
            if (eventType == UsageEvents.Event.DEVICE_STARTUP) {
                // Consider all apps closed after startup according to docs
                open = false
            }

            // Only consider events concerning our package otherwise
            if (event.packageName == packageName) {
                when (eventType) {
                    // see EventLogWrapper
                    UsageEvents.Event.ACTIVITY_RESUMED, 4 -> {
                        open = true
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    3 -> {
                        if (timestamp != event.timeStamp) {
                            // Don't flip to 'false' if we're looking at the original event itself
                            open = false
                        }
                    }
                }
            }
        }

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")

        // Event is valid if it was previously opened (within SCAN_INTERVAL)
        return open
    }
}
