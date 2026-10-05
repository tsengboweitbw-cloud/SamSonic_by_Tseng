package com.example.samsonic.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the app runs while it starts, while each tab and the
 * library's lists are scrolled, while a playlist is opened and while the full player is
 * opened, so they are compiled ahead of time on a new install.
 *
 * Sign in on the phone first, with English as the language (the steps find tabs by their
 * labels), and have something in the queue to record the player. Without a session the run
 * fails at the first step instead of recording the login screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndScroll() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "com.example.samsonic"
        // collect repeats the steps until the profile stops changing, up to 15 times by default.
        // Each pass takes minutes here, so stop after two matching passes and at most three.
        rule.collect(
            packageName = packageName,
            maxIterations = 3,
            stableIterations = 2,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.desc("Library")), 10_000)) {
                val seen = device.findObjects(By.pkg(packageName)).flatMap {
                    listOfNotNull(it.contentDescription, it.text)
                }.distinct().take(30)
                "The tab bar did not appear. Sign in on the phone first, with English as the " +
                    "language. On screen: $seen"
            }

            var scrolled = scrollMain()

            openTab("Library")
            for (section in listOf("Songs", "Albums", "Artists", "Playlists")) {
                device.findObject(By.text(section))?.click()
                device.waitForIdle()
                scrolled = scrollMain() || scrolled
            }
            scrolled = openFirstPlaylist() || scrolled

            openTab("Search")
            openTab("Settings")
            scrolled = scrollMain() || scrolled
            openTab("Home")

            openPlayer()

            check(scrolled) { "No list was found to scroll, so only start-up was recorded." }
        }
    }

    private fun MacrobenchmarkScope.openTab(label: String) {
        val tab = checkNotNull(device.findObject(By.desc(label))) { "No $label tab" }
        tab.click()
        device.waitForIdle()
    }

    /** Flings the biggest scrollable on screen down and up, a few times. False if there is none. */
    private fun MacrobenchmarkScope.scrollMain(): Boolean {
        device.wait(Until.hasObject(By.scrollable(true)), 3_000)
        var scrolled = false
        repeat(3) {
            for (direction in listOf(Direction.DOWN, Direction.UP)) {
                // Find the list again each time: a page that is still loading redraws its
                // list, which leaves the old handle stale.
                try {
                    val list = device.findObjects(By.scrollable(true))
                        .maxByOrNull { it.visibleBounds.height() } ?: return scrolled
                    // Stay off the screen edges, where the system's back gesture would take the swipe.
                    list.setGestureMargin(device.displayWidth / 5)
                    list.fling(direction)
                    scrolled = true
                } catch (_: StaleObjectException) {
                }
                device.waitForIdle()
            }
        }
        return scrolled
    }

    /** Opens the first playlist on the Playlists section, scrolls it and comes back. */
    private fun MacrobenchmarkScope.openFirstPlaylist(): Boolean {
        val list = device.findObjects(By.scrollable(true))
            .maxByOrNull { it.visibleBounds.height() } ?: return false
        val row = list.findObjects(By.clickable(true)).firstOrNull() ?: return false
        row.click()
        device.waitForIdle()
        val scrolled = scrollMain()
        device.pressBack()
        device.waitForIdle()
        // Back from a detail page stays in the app. If it went home instead, start it again.
        if (!device.wait(Until.hasObject(By.desc("Library")), 2_000)) startActivityAndWait()
        return scrolled
    }

    /** Opens the full player from the mini player and closes it. Skipped with nothing queued. */
    private fun MacrobenchmarkScope.openPlayer() {
        val next = device.findObject(By.desc("Next")) ?: return
        // The mini player's title area, left of its buttons.
        device.click(device.displayWidth / 3, next.visibleBounds.centerY())
        if (device.wait(Until.hasObject(By.desc("Collapse")), 3_000)) {
            device.waitForIdle()
            device.findObject(By.desc("Collapse"))?.click()
            device.waitForIdle()
        }
    }
}
