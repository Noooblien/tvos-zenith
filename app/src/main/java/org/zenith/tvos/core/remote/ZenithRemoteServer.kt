package org.zenith.tvos.core.remote

import android.view.KeyEvent
import org.zenith.tvos.core.security.IntentSanitizer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded, zero-dependency local Wi-Fi HTTP remote control server.
 * Enables zero-install phone control (iPhone / Android) of Zenith TV,
 * including live App Quick-Launch Grid, Spotlight Search text dispatch, and Glass Trackpad.
 */
class ZenithRemoteServer(
    val port: Int = DEFAULT_PORT
) {
    data class RemoteAppEntry(
        val packageName: String,
        val label: String,
        val category: String
    )

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newFixedThreadPool(4)

    var onKeyReceived: ((Int) -> Unit)? = null
    var onTextReceived: ((String) -> Unit)? = null
    var onLaunchPackageRequested: ((String) -> Boolean)? = null
    var appListProvider: (() -> List<RemoteAppEntry>)? = null

    fun start(): Boolean {
        if (isRunning.get()) return true
        return try {
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            threadPool.execute {
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        threadPool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            isRunning.set(false)
            false
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun isServerRunning(): Boolean = isRunning.get()

    private fun handleClient(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 3000
                val remoteIp = socket.inetAddress?.hostAddress
                val output = socket.getOutputStream()
                if (!IntentSanitizer.isPrivateOrLoopbackIpv4(remoteIp)) {
                    sendResponse(output, 403, "Forbidden", "application/json", "{\"error\":\"non-private client ip rejected\"}")
                    return
                }
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]

                val path = fullPath.substringBefore("?")
                val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

                // Read headers
                var line = reader.readLine()
                var contentLength = 0
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                if (contentLength > MAX_REQUEST_BODY_BYTES) {
                    sendResponse(output, 413, "Payload Too Large", "application/json", "{\"error\":\"request body exceeds 2048 byte limit\"}")
                    return
                }

                var body = ""
                if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    body = String(bodyChars, 0, readTotal)
                }

                when {
                    method == "OPTIONS" -> {
                        sendResponse(output, 204, "No Content", "text/plain", "")
                    }
                    path == "/" && method == "GET" -> {
                        val html = getRemoteHtmlPage()
                        sendResponse(output, 200, "OK", "text/html; charset=utf-8", html)
                    }
                    path == "/api/status" -> {
                        val json = "{\"status\":\"ok\",\"app\":\"Zenith TV\",\"ip\":\"${getLocalIpAddress() ?: "unknown"}\"}"
                        sendResponse(output, 200, "OK", "application/json", json)
                    }
                    path == "/api/apps" -> {
                        val apps = appListProvider?.invoke() ?: emptyList()
                        val json = serializeAppsJson(apps)
                        sendResponse(output, 200, "OK", "application/json", json)
                    }
                    path == "/api/launch" -> {
                        val queryParams = parseQueryParams(queryString)
                        val pkg = queryParams["pkg"]?.trim() ?: ""
                        if (pkg.isNotEmpty()) {
                            if (!IntentSanitizer.isValidPackageName(pkg)) {
                                sendResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"invalid package format\"}")
                                return
                            }
                            val accepted = onLaunchPackageRequested?.invoke(pkg) ?: false
                            if (accepted) {
                                sendResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\",\"launched\":\"${escapeJson(pkg)}\"}")
                            } else {
                                sendResponse(output, 404, "Not Found", "application/json", "{\"error\":\"package not found: ${escapeJson(pkg)}\"}")
                            }
                        } else {
                            sendResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"missing pkg parameter\"}")
                        }
                    }
                    path == "/api/input" -> {
                        val queryParams = parseQueryParams(queryString)
                        val keyName = queryParams["key"]?.uppercase() ?: ""
                        val keyCode = mapKey(keyName)
                        if (keyCode != null) {
                            onKeyReceived?.invoke(keyCode)
                            sendResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\",\"key\":\"$keyName\"}")
                        } else {
                            sendResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"invalid key: $keyName\"}")
                        }
                    }
                    path == "/api/type" -> {
                        val queryParams = parseQueryParams(queryString)
                        var text = queryParams["text"]
                        if (text.isNullOrEmpty() && body.isNotEmpty()) {
                            val bodyParams = parseQueryParams(body)
                            text = bodyParams["text"] ?: body
                        }
                        val sanitizedText = IntentSanitizer.sanitizeSearchQuery(text)
                        if (sanitizedText.isNotEmpty()) {
                            onTextReceived?.invoke(sanitizedText)
                            sendResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\",\"typed\":\"${escapeJson(sanitizedText)}\"}")
                        } else {
                            sendResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"missing text parameter\"}")
                        }
                    }
                    else -> {
                        sendResponse(output, 404, "Not Found", "application/json", "{\"error\":\"endpoint not found\"}")
                    }
                }
            }
        } catch (e: Exception) {
            // Socket handling error
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        if (bytes.isNotEmpty()) {
            output.write(bytes)
        }
        output.flush()
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val MAX_REQUEST_BODY_BYTES = 2048

        fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        fun serializeAppsJson(apps: List<RemoteAppEntry>): String {
            val items = apps.joinToString(",") { app ->
                "{\"pkg\":\"${escapeJson(app.packageName)}\",\"label\":\"${escapeJson(app.label)}\",\"category\":\"${escapeJson(app.category)}\"}"
            }
            return "{\"apps\":[$items]}"
        }

        /**
         * Resolves the primary local Wi-Fi IPv4 address.
         */
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (ip != null && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            return null
        }

        /**
         * Maps textual key commands to Android KeyEvent codes.
         */
        fun mapKey(name: String): Int? {
            return when (name.uppercase()) {
                "UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "OK", "SELECT", "CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                "BACK" -> KeyEvent.KEYCODE_BACK
                "HOME" -> KeyEvent.KEYCODE_HOME
                "PLAY", "PAUSE", "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "VOLUP", "VOLUME_UP" -> KeyEvent.KEYCODE_VOLUME_UP
                "VOLDOWN", "VOLUME_DOWN" -> KeyEvent.KEYCODE_VOLUME_DOWN
                "MENU", "CONTEXT" -> KeyEvent.KEYCODE_MENU
                "SEARCH", "SPOTLIGHT" -> KeyEvent.KEYCODE_SEARCH
                else -> null
            }
        }

        /**
         * Safely parses standard HTTP query parameters.
         */
        fun parseQueryParams(query: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            if (query.isBlank()) return map
            val pairs = query.split("&")
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                if (idx != -1) {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    map[key] = value
                } else {
                    val key = URLDecoder.decode(pair, "UTF-8")
                    map[key] = ""
                }
            }
            return map
        }

        /**
         * Complete self-contained Apple TV glassmorphic mobile web app with Quick-Launch App Drawer.
         */
        fun getRemoteHtmlPage(): String {
            return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Zenith TV Remote</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-user-select: none; }
        body {
            background: radial-gradient(circle at 50% 0%, #1B2436 0%, #090C15 100%);
            color: #F5F5F7;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: space-between;
            padding: 16px 20px 24px;
            overflow-x: hidden;
        }
        .header {
            width: 100%;
            max-width: 360px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 6px 4px;
        }
        .logo-title {
            font-size: 16px;
            font-weight: 700;
            letter-spacing: 0.4px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .status-badge {
            font-size: 11px;
            color: #30D158;
            background: rgba(48, 209, 88, 0.14);
            border: 1px solid rgba(48, 209, 88, 0.35);
            padding: 4px 10px;
            border-radius: 999px;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .status-dot { width: 6px; height: 6px; background: #30D158; border-radius: 50%; }

        .tab-bar {
            width: 100%;
            max-width: 360px;
            display: flex;
            background: rgba(255,255,255,0.07);
            border-radius: 999px;
            padding: 3px;
            margin: 8px 0;
        }
        .tab-btn {
            flex: 1;
            padding: 7px 0;
            border: none;
            background: transparent;
            color: #AEAEB2;
            font-size: 13px;
            font-weight: 600;
            border-radius: 999px;
            cursor: pointer;
        }
        .tab-btn.active {
            background: rgba(255,255,255,0.18);
            color: #FFFFFF;
        }

        /* Glass Trackpad Surface */
        .trackpad-container {
            width: 100%;
            max-width: 340px;
            height: 250px;
            background: linear-gradient(145deg, rgba(255,255,255,0.09) 0%, rgba(255,255,255,0.03) 100%);
            border: 1.5px solid rgba(255, 255, 255, 0.16);
            border-radius: 36px;
            position: relative;
            box-shadow: 0 20px 50px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.2);
            display: flex;
            align-items: center;
            justify-content: center;
            touch-action: none;
            transition: border-color 0.15s, transform 0.1s;
            margin: 6px 0;
        }
        .trackpad-container:active { border-color: rgba(41, 151, 255, 0.6); transform: scale(0.99); }
        .trackpad-center-btn {
            width: 86px;
            height: 86px;
            border-radius: 50%;
            background: rgba(255, 255, 255, 0.08);
            border: 1px solid rgba(255, 255, 255, 0.15);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            font-weight: 700;
            color: rgba(255,255,255,0.7);
            pointer-events: none;
        }
        .dpad-arrow {
            position: absolute;
            font-size: 18px;
            color: rgba(255,255,255,0.35);
            pointer-events: none;
        }
        .arrow-up { top: 16px; }
        .arrow-down { bottom: 16px; }
        .arrow-left { left: 20px; }
        .arrow-right { right: 20px; }

        /* Quick Launch App Grid Panel */
        .apps-panel {
            display: none;
            width: 100%;
            max-width: 340px;
            height: 250px;
            overflow-y: auto;
            background: rgba(255,255,255,0.04);
            border: 1px solid rgba(255,255,255,0.12);
            border-radius: 24px;
            padding: 12px;
            margin: 6px 0;
            grid-template-columns: 1fr 1fr;
            gap: 8px;
            align-content: start;
        }
        .app-chip {
            background: rgba(255,255,255,0.09);
            border: 1px solid rgba(255,255,255,0.14);
            border-radius: 14px;
            padding: 10px 12px;
            color: #F5F5F7;
            font-size: 13px;
            font-weight: 600;
            text-align: left;
            cursor: pointer;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .app-chip:active { background: rgba(41, 151, 255, 0.35); }

        /* Tactile Remote Buttons */
        .controls-grid {
            width: 100%;
            max-width: 340px;
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin-bottom: 4px;
        }
        .button-row {
            display: flex;
            gap: 10px;
            width: 100%;
        }
        .btn-tv {
            flex: 1;
            height: 48px;
            background: rgba(255, 255, 255, 0.09);
            border: 1px solid rgba(255, 255, 255, 0.14);
            border-radius: 18px;
            color: #F5F5F7;
            font-size: 14px;
            font-weight: 600;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            cursor: pointer;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            transition: transform 0.08s, background 0.08s;
        }
        .btn-tv:active {
            background: rgba(255, 255, 255, 0.25);
            transform: scale(0.95);
        }
        .btn-primary {
            background: rgba(41, 151, 255, 0.2);
            border-color: rgba(41, 151, 255, 0.4);
            color: #2997FF;
        }

        /* Keyboard Modal */
        .modal {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.8);
            backdrop-filter: blur(10px);
            align-items: center;
            justify-content: center;
            padding: 24px;
            z-index: 100;
        }
        .modal-box {
            background: #1C2436;
            border: 1.5px solid rgba(255,255,255,0.2);
            border-radius: 24px;
            width: 100%;
            max-width: 320px;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
        .modal-input {
            width: 100%;
            height: 48px;
            background: rgba(0,0,0,0.4);
            border: 1px solid rgba(255,255,255,0.2);
            border-radius: 12px;
            padding: 0 16px;
            color: #fff;
            font-size: 16px;
            outline: none;
        }
        .modal-input:focus { border-color: #2997FF; }
    </style>
</head>
<body>
    <div class="header">
        <div class="logo-title">Zenith TV Remote</div>
        <div class="status-badge"><div class="status-dot"></div> Connected</div>
    </div>

    <div class="tab-bar">
        <button class="tab-btn active" id="tabPad" onclick="switchTab('pad')">Touchpad</button>
        <button class="tab-btn" id="tabApps" onclick="switchTab('apps')">🚀 Quick Apps</button>
    </div>

    <!-- Swipe & Touch Glass Trackpad -->
    <div class="trackpad-container" id="trackpad">
        <span class="dpad-arrow arrow-up">▲</span>
        <span class="dpad-arrow arrow-down">▼</span>
        <span class="dpad-arrow arrow-left">◀</span>
        <span class="dpad-arrow arrow-right">▶</span>
        <div class="trackpad-center-btn">OK</div>
    </div>

    <!-- Quick Apps Panel -->
    <div class="apps-panel" id="appsPanel"></div>

    <!-- Controls -->
    <div class="controls-grid">
        <div class="button-row">
            <button class="btn-tv" onclick="sendKey('BACK')">◀ Back</button>
            <button class="btn-tv" onclick="sendKey('HOME')">📺 Home</button>
            <button class="btn-tv" onclick="sendKey('MENU')">☰ Menu</button>
        </div>
        <div class="button-row">
            <button class="btn-tv btn-primary" onclick="sendKey('SEARCH')">🔍 Search</button>
            <button class="btn-tv" onclick="openKeyboard()">⌨️ Type</button>
            <button class="btn-tv" onclick="sendKey('PLAY_PAUSE')">⏯ Play</button>
        </div>
        <div class="button-row">
            <button class="btn-tv" onclick="sendKey('VOLDOWN')">🔉 Vol -</button>
            <button class="btn-tv" onclick="sendKey('VOLUP')">🔊 Vol +</button>
        </div>
    </div>

    <!-- Keyboard Modal -->
    <div class="modal" id="keyboardModal">
        <div class="modal-box">
            <h3 style="font-size: 18px;">Spotlight Type to TV</h3>
            <input type="text" id="tvInput" class="modal-input" placeholder="Search app name..." autocomplete="off">
            <div style="display: flex; gap: 10px;">
                <button class="btn-tv" onclick="closeKeyboard()">Cancel</button>
                <button class="btn-tv btn-primary" onclick="submitText()">Search</button>
            </div>
        </div>
    </div>

    <script>
        function vibrate() {
            if (window.navigator && window.navigator.vibrate) {
                window.navigator.vibrate(12);
            }
        }

        function sendKey(key) {
            vibrate();
            fetch('/api/input?key=' + encodeURIComponent(key), { cache: 'no-store' }).catch(() => {});
        }

        function launchApp(pkg) {
            vibrate();
            fetch('/api/launch?pkg=' + encodeURIComponent(pkg), { cache: 'no-store' }).catch(() => {});
        }

        function switchTab(mode) {
            const pad = document.getElementById('trackpad');
            const apps = document.getElementById('appsPanel');
            document.getElementById('tabPad').classList.toggle('active', mode === 'pad');
            document.getElementById('tabApps').classList.toggle('active', mode === 'apps');
            if (mode === 'apps') {
                pad.style.display = 'none';
                apps.style.display = 'grid';
                loadApps();
            } else {
                pad.style.display = 'flex';
                apps.style.display = 'none';
            }
        }

        function loadApps() {
            const container = document.getElementById('appsPanel');
            fetch('/api/apps', { cache: 'no-store' })
                .then(r => r.json())
                .then(data => {
                    container.innerHTML = '';
                    (data.apps || []).forEach(app => {
                        const btn = document.createElement('button');
                        btn.className = 'app-chip';
                        btn.textContent = '🚀 ' + app.label;
                        btn.onclick = () => launchApp(app.pkg);
                        container.appendChild(btn);
                    });
                })
                .catch(() => {});
        }

        // Swipe & Tap Detection on Trackpad
        const trackpad = document.getElementById('trackpad');
        let startX = 0, startY = 0, lastX = 0, lastY = 0;
        let isMoved = false;

        trackpad.addEventListener('touchstart', (e) => {
            const touch = e.touches[0];
            startX = touch.clientX;
            startY = touch.clientY;
            lastX = startX;
            lastY = startY;
            isMoved = false;
        }, { passive: true });

        trackpad.addEventListener('touchmove', (e) => {
            const touch = e.touches[0];
            const dx = touch.clientX - lastX;
            const dy = touch.clientY - lastY;
            const threshold = 32;

            if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                isMoved = true;
                if (Math.abs(dx) > Math.abs(dy)) {
                    sendKey(dx > 0 ? 'RIGHT' : 'LEFT');
                } else {
                    sendKey(dy > 0 ? 'DOWN' : 'UP');
                }
                lastX = touch.clientX;
                lastY = touch.clientY;
            }
        }, { passive: true });

        trackpad.addEventListener('touchend', () => {
            if (!isMoved) {
                sendKey('OK');
            }
        });

        // Click fallback for desktop browsers
        trackpad.addEventListener('click', () => {
            if (!isMoved) sendKey('OK');
        });

        // Keyboard Modal Handlers
        const modal = document.getElementById('keyboardModal');
        const input = document.getElementById('tvInput');

        function openKeyboard() {
            modal.style.display = 'flex';
            setTimeout(() => input.focus(), 100);
        }

        function closeKeyboard() {
            modal.style.display = 'none';
            input.value = '';
        }

        function submitText() {
            const text = input.value;
            if (text) {
                vibrate();
                fetch('/api/type?text=' + encodeURIComponent(text), { cache: 'no-store' }).catch(() => {});
                closeKeyboard();
            }
        }

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                submitText();
            }
        });
    </script>
</body>
</html>
            """.trimIndent()
        }
    }
}
