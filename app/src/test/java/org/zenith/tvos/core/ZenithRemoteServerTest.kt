package org.zenith.tvos.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.remote.ZenithRemoteServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ZenithRemoteServerTest {

    @Test
    fun testKeyMapping() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ZenithRemoteServer.mapKey("UP"))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ZenithRemoteServer.mapKey("DOWN"))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ZenithRemoteServer.mapKey("LEFT"))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ZenithRemoteServer.mapKey("RIGHT"))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, ZenithRemoteServer.mapKey("OK"))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, ZenithRemoteServer.mapKey("SELECT"))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, ZenithRemoteServer.mapKey("CENTER"))
        assertEquals(KeyEvent.KEYCODE_BACK, ZenithRemoteServer.mapKey("BACK"))
        assertEquals(KeyEvent.KEYCODE_HOME, ZenithRemoteServer.mapKey("HOME"))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, ZenithRemoteServer.mapKey("PLAY"))
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, ZenithRemoteServer.mapKey("VOLUP"))
        assertEquals(KeyEvent.KEYCODE_VOLUME_DOWN, ZenithRemoteServer.mapKey("VOLDOWN"))
        assertEquals(KeyEvent.KEYCODE_MENU, ZenithRemoteServer.mapKey("MENU"))
        assertEquals(KeyEvent.KEYCODE_SEARCH, ZenithRemoteServer.mapKey("SEARCH"))
        assertNull(ZenithRemoteServer.mapKey("UNKNOWN_KEY_FOO"))
    }

    @Test
    fun testQueryParamsParser() {
        val query = "key=UP&source=phone%20safari&count=1"
        val params = ZenithRemoteServer.parseQueryParams(query)

        assertEquals("UP", params["key"])
        assertEquals("phone safari", params["source"])
        assertEquals("1", params["count"])

        val empty = ZenithRemoteServer.parseQueryParams("")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testRemoteHtmlTemplateAndAppsSerializer() {
        val html = ZenithRemoteServer.getRemoteHtmlPage()
        assertTrue(html.contains("Zenith TV Remote"))
        assertTrue(html.contains("trackpad"))
        assertTrue(html.contains("/api/input?key="))
        assertTrue(html.contains("/api/apps"))
        assertTrue(html.contains("/api/launch?pkg="))

        val json = ZenithRemoteServer.serializeAppsJson(
            listOf(
                ZenithRemoteServer.RemoteAppEntry("com.netflix.ninja", "Netflix", "Streaming")
            )
        )
        assertTrue(json.contains("\"pkg\":\"com.netflix.ninja\""))
        assertTrue(json.contains("\"label\":\"Netflix\""))
    }

    @Test
    fun testServerLifecycleAndHttpDispatch() {
        val testPort = 19528
        val server = ZenithRemoteServer(port = testPort)

        var receivedKey: Int? = null
        var launchedPkg: String? = null
        val latch = CountDownLatch(1)

        server.onKeyReceived = { keyCode ->
            receivedKey = keyCode
            latch.countDown()
        }
        server.appListProvider = {
            listOf(ZenithRemoteServer.RemoteAppEntry("com.plexapp.android", "Plex", "Streaming"))
        }
        server.onLaunchPackageRequested = { pkg ->
            launchedPkg = pkg
            pkg == "com.plexapp.android"
        }

        val started = server.start()
        assertTrue("Server failed to start on port $testPort", started)
        assertTrue(server.isServerRunning())

        try {
            // 1. Test /api/input?key=UP
            val url = URL("http://127.0.0.1:$testPort/api/input?key=UP")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)
            val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).readText()
            assertTrue(responseBody.contains("\"status\":\"ok\""))
            assertTrue(responseBody.contains("\"key\":\"UP\""))

            val received = latch.await(2, TimeUnit.SECONDS)
            assertTrue("Key event was not dispatched within timeout", received)
            assertEquals(KeyEvent.KEYCODE_DPAD_UP, receivedKey)

            // 2. Test /api/apps
            val appsConn = URL("http://127.0.0.1:$testPort/api/apps").openConnection() as HttpURLConnection
            assertEquals(200, appsConn.responseCode)
            val appsBody = BufferedReader(InputStreamReader(appsConn.inputStream)).readText()
            assertTrue(appsBody.contains("com.plexapp.android"))

            // 3. Test /api/launch?pkg=com.plexapp.android
            val launchConn = URL("http://127.0.0.1:$testPort/api/launch?pkg=com.plexapp.android").openConnection() as HttpURLConnection
            assertEquals(200, launchConn.responseCode)
            assertEquals("com.plexapp.android", launchedPkg)
        } finally {
            server.stop()
        }
    }
}
