package org.zenith.tvos.core.remote

import android.view.KeyEvent
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
 * Enables zero-install phone control (iPhone / Android) of Zenith TV.
 */
class ZenithRemoteServer(
    val port: Int = DEFAULT_PORT
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newFixedThreadPool(4)

    var onKeyReceived: ((Int) -> Unit)? = null
    var onTextReceived: ((String) -> Unit)? = null

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
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val output = socket.getOutputStream()

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
                        if (!text.isNullOrEmpty()) {
                            onTextReceived?.invoke(text)
                            sendResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\",\"typed\":\"$text\"}")
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
         * Complete self-contained Apple TV glassmorphic mobile web app.
         */
        fun getRemoteHtmlPage(): String {
            return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="theme-color" content="#0B0E14">
    <title>Zenith TV Remote</title>
    <style>
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; user-select: none; }
        body {
            background-color: #0B0E14;
            color: #F5F5F7;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: space-between;
            height: 100vh;
            height: 100dvh;
            padding: env(safe-area-inset-top, 16px) 20px env(safe-area-inset-bottom, 20px);
            overflow: hidden;
        }
        .header {
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 4px;
        }
        .logo-title {
            font-size: 18px;
            font-weight: 700;
            letter-spacing: 0.5px;
            color: #F5F5F7;
        }
        .status-badge {
            background: rgba(41, 151, 255, 0.15);
            border: 1px solid rgba(41, 151, 255, 0.4);
            border-radius: 999px;
            padding: 4px 12px;
            font-size: 12px;
            font-weight: 600;
            color: #2997FF;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .status-dot { width: 7px; height: 7px; background: #30D158; border-radius: 50%; box-shadow: 0 0 6px #30D158; }
        
        /* Glassmorphic Touch Trackpad */
        .trackpad-container {
            width: 100%;
            max-width: 340px;
            height: 300px;
            background: radial-gradient(circle at 50% 50%, rgba(255,255,255,0.06) 0%, rgba(20,25,38,0.7) 100%);
            border: 1.5px solid rgba(255, 255, 255, 0.18);
            border-radius: 36px;
            box-shadow: 0 16px 40px rgba(0, 0, 0, 0.6), inset 0 1px 0 rgba(255,255,255,0.15);
            position: relative;
            display: flex;
            align-items: center;
            justify-content: center;
            touch-action: none;
            transition: border-color 0.15s, transform 0.1s;
        }
        .trackpad-container:active { border-color: rgba(41, 151, 255, 0.6); transform: scale(0.99); }
        .trackpad-center-btn {
            width: 90px;
            height: 90px;
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
        .arrow-up { top: 18px; }
        .arrow-down { bottom: 18px; }
        .arrow-left { left: 20px; }
        .arrow-right { right: 20px; }

        /* Tactile Remote Buttons */
        .controls-grid {
            width: 100%;
            max-width: 340px;
            display: flex;
            flex-direction: column;
            gap: 12px;
            margin-bottom: 8px;
        }
        .button-row {
            display: flex;
            gap: 12px;
            width: 100%;
        }
        .btn-tv {
            flex: 1;
            height: 52px;
            background: rgba(255, 255, 255, 0.09);
            border: 1px solid rgba(255, 255, 255, 0.14);
            border-radius: 20px;
            color: #F5F5F7;
            font-size: 15px;
            font-weight: 600;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
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

    <!-- Swipe & Touch Glass Trackpad -->
    <div class="trackpad-container" id="trackpad">
        <span class="dpad-arrow arrow-up">▲</span>
        <span class="dpad-arrow arrow-down">▼</span>
        <span class="dpad-arrow arrow-left">◀</span>
        <span class="dpad-arrow arrow-right">▶</span>
        <div class="trackpad-center-btn">OK</div>
    </div>

    <!-- Controls -->
    <div class="controls-grid">
        <div class="button-row">
            <button class="btn-tv" onclick="sendKey('BACK')">◀ Back</button>
            <button class="btn-tv" onclick="sendKey('HOME')">📺 Home</button>
            <button class="btn-tv" onclick="sendKey('MENU')">☰ Menu</button>
        </div>
        <div class="button-row">
            <button class="btn-tv btn-primary" onclick="sendKey('PLAY_PAUSE')">⏯ Play / Pause</button>
            <button class="btn-tv" onclick="openKeyboard()">⌨️ Type</button>
        </div>
        <div class="button-row">
            <button class="btn-tv" onclick="sendKey('VOLDOWN')">🔉 Vol -</button>
            <button class="btn-tv" onclick="sendKey('VOLUP')">🔊 Vol +</button>
        </div>
    </div>

    <!-- Keyboard Modal -->
    <div class="modal" id="keyboardModal">
        <div class="modal-box">
            <h3 style="font-size: 18px;">Type to TV</h3>
            <input type="text" id="tvInput" class="modal-input" placeholder="Type message or search..." autocomplete="off">
            <div style="display: flex; gap: 10px;">
                <button class="btn-tv" onclick="closeKeyboard()">Cancel</button>
                <button class="btn-tv btn-primary" onclick="submitText()">Send</button>
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
