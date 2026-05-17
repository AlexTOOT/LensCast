package com.opencode.multilensipcam

import android.os.SystemClock
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MjpegHttpServer(
    private val port: Int,
    snapshotCacheRoot: File,
    private val stateProvider: () -> WebControlState,
    private val controlHandler: (WebControlCommand) -> Unit,
    private val h264RequestHandler: () -> Boolean,
    private val audioRequestHandler: (Boolean) -> Boolean,
    private val streamDebugProvider: () -> CameraStreamDebugState?,
    private val cameraDebugProvider: () -> CameraDebugState,
    private val cameraScanStateProvider: () -> CameraScanState,
    private val cameraScanHandler: () -> CameraScanResponse,
    private val cameraScanCacheDeleteHandler: (String?) -> CameraScanCacheDeleteResponse,
    private val cameraOpenProbeHandler: (CameraOpenProbeRequest) -> CameraOpenProbeResult,
    private val mjpegClientCountChanged: () -> Unit = {},
    private val h264ClientCountChanged: () -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val mediaStreams = WebMediaStreamBroker(
        snapshotCacheRoot = snapshotCacheRoot,
        h264RequestHandler = h264RequestHandler,
        runningProvider = { running.get() },
        mjpegClientCountChanged = mjpegClientCountChanged,
        h264ClientCountChanged = h264ClientCountChanged
    )
    private val controlResponses = WebControlResponses(
        stateProvider = stateProvider,
        controlHandler = controlHandler
    )
    private val cameraDebugResponses = WebCameraDebugResponses(
        stateProvider = stateProvider,
        cameraDebugProvider = cameraDebugProvider,
        cameraScanStateProvider = cameraScanStateProvider,
        cameraScanHandler = cameraScanHandler,
        cameraScanCacheDeleteHandler = cameraScanCacheDeleteHandler,
        cameraOpenProbeHandler = cameraOpenProbeHandler
    )

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var serverStartedAtElapsedMs: Long = 0

    // Server lifecycle and frame fanout.

    fun start() {
        if (!running.compareAndSet(false, true)) return
        resetDebugStats()
        serverStartedAtElapsedMs = SystemClock.elapsedRealtime()
        val currentExecutor = Executors.newCachedThreadPool()
        executor = currentExecutor

        currentExecutor.execute {
            val socket = ServerSocket()
            serverSocket = socket
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))

            while (running.get()) {
                try {
                    val client = socket.accept()
                    clients += client
                    currentExecutor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) {
                        return@execute
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        mediaStreams.shutdown()
    }

    fun closeActiveStreams() {
        mediaStreams.closeActiveStreamClientsAndFrames()
    }

    fun closeAudioStreams() {
        mediaStreams.closeAudioClients()
    }

    fun updateFrame(frame: MjpegFrame) {
        mediaStreams.updateFrame(frame)
    }

    fun updateH264(accessUnit: H264AccessUnit) {
        mediaStreams.updateH264(accessUnit)
    }

    fun updateAudio(accessUnit: AacAccessUnit) {
        mediaStreams.updateAudio(accessUnit)
    }

    fun audioClientCount(): Int {
        return mediaStreams.audioClientCount
    }

    fun mjpegClientCount(): Int {
        return mediaStreams.mjpegClientCount
    }

    fun h264ClientCount(): Int {
        return mediaStreams.h264ClientCount
    }

    fun hasRecentMjpegClients(graceMs: Long): Boolean {
        return mediaStreams.hasRecentMjpegClients(graceMs)
    }

    // Request routing and dashboard shell.

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15_000
            socket.getInputStream().bufferedReader().use { reader ->
                val requestLine = reader.readLine() ?: return
                val rawTarget = requestLine.split(" ").getOrNull(1) ?: "/"
                val target = rawTarget.substringBefore("?")
                val query = WebHttpQueryParser.parse(rawTarget.substringAfter("?", ""))
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Drain request headers.
                }

                val frozenSnapshotId = WebHttpRoutes.frozenSnapshotId(target)
                if (frozenSnapshotId != null) {
                    mediaStreams.writeFrozenSnapshot(socket.getOutputStream(), frozenSnapshotId)
                    return
                }

                when (WebHttpRoutes.resolve(target)) {
                    WebHttpRoute.INDEX -> writeIndex(socket.getOutputStream())
                    WebHttpRoute.SNAPSHOT -> mediaStreams.writeSnapshot(socket.getOutputStream())
                    WebHttpRoute.MJPEG -> mediaStreams.writeMjpeg(socket)
                    WebHttpRoute.H264 -> mediaStreams.writeH264(socket)
                    WebHttpRoute.AUDIO -> mediaStreams.writeAudio(socket, audioRequestHandler, query["wait"] == "1")
                    WebHttpRoute.STATE -> controlResponses.writeState(socket.getOutputStream())
                    WebHttpRoute.DEBUG_STREAM -> writeDebugStream(socket.getOutputStream())
                    WebHttpRoute.DEBUG_CAMERAS -> cameraDebugResponses.writeDebugCameras(socket.getOutputStream())
                    WebHttpRoute.CAMERA_SCAN_CACHE -> cameraDebugResponses.writeCameraScanState(socket.getOutputStream())
                    WebHttpRoute.CAMERA_SCAN_CACHE_DELETE -> cameraDebugResponses.writeCameraScanCacheDelete(socket.getOutputStream(), query)
                    WebHttpRoute.CAMERA_SCAN -> cameraDebugResponses.writeCameraScan(socket.getOutputStream())
                    WebHttpRoute.CAMERA_OPEN -> cameraDebugResponses.writeCameraOpenProbe(socket.getOutputStream(), query)
                    WebHttpRoute.CAMERA_OPEN_MATRIX -> cameraDebugResponses.writeCameraOpenMatrix(socket.getOutputStream(), query)
                    WebHttpRoute.CONTROL -> controlResponses.handleControl(socket.getOutputStream(), query)
                    WebHttpRoute.NOT_FOUND -> WebHttpResponses.writeNotFound(socket.getOutputStream())
                }
            }
        } catch (_: Exception) {
            // Client disconnected or sent an incomplete request.
        } finally {
            runCatching { socket.close() }
            clients.remove(socket)
            mediaStreams.removeSocket(socket)
        }
    }

    private fun writeIndex(output: OutputStream) {
        WebHttpResponses.writeText(output, WebDashboardPage.render(appVersionLabel()), "text/html; charset=utf-8")
    }

    // Debug and control API responses.

    private fun writeDebugStream(output: OutputStream) {
        val nowMs = SystemClock.elapsedRealtime()
        val state = stateProvider()
        val json = WebDebugStreamJson.build(
            mediaStreams.debugSnapshot(
                nowElapsedMs = nowMs,
                serverRunning = running.get(),
                serverStartedAtElapsedMs = serverStartedAtElapsedMs,
                streaming = state.streaming,
                httpClients = clients.size,
                cameraStreamDebug = streamDebugProvider()
            )
        )
        WebHttpResponses.writeText(output, json.toString(), "application/json; charset=utf-8")
    }

    // Debug counters and small parsers.

    private fun resetDebugStats() {
        mediaStreams.resetDebugStats()
    }

    private fun appVersionLabel(): String {
        return AppDisplayInfo.versionLabel()
    }

}

