package com.opencode.multilensipcam

enum class WebHttpRoute {
    INDEX,
    SNAPSHOT,
    MJPEG,
    H264,
    AUDIO,
    STATE,
    DEBUG_STREAM,
    DEBUG_CAMERAS,
    CAMERA_SCAN_CACHE,
    CAMERA_SCAN_CACHE_DELETE,
    CAMERA_SCAN,
    CAMERA_OPEN,
    CAMERA_OPEN_MATRIX,
    CONTROL,
    NOT_FOUND
}

object WebHttpRoutes {
    private val frozenSnapshotPattern = Regex("^/snapshots/(\\d{13}-\\d+)\\.jpg$")

    fun resolve(target: String): WebHttpRoute {
        return when (target) {
            "/", "/index.html" -> WebHttpRoute.INDEX
            "/snapshot.jpg", "/shot.jpg" -> WebHttpRoute.SNAPSHOT
            "/mjpeg", "/video" -> WebHttpRoute.MJPEG
            "/h264", "/video.h264" -> WebHttpRoute.H264
            "/audio.aac" -> WebHttpRoute.AUDIO
            "/api/state" -> WebHttpRoute.STATE
            "/api/debug/stream" -> WebHttpRoute.DEBUG_STREAM
            "/api/debug/cameras", "/api/debug/camera-report" -> WebHttpRoute.DEBUG_CAMERAS
            "/api/debug/camera-scan-cache" -> WebHttpRoute.CAMERA_SCAN_CACHE
            "/api/debug/camera-scan-cache/delete" -> WebHttpRoute.CAMERA_SCAN_CACHE_DELETE
            "/api/debug/camera-scan" -> WebHttpRoute.CAMERA_SCAN
            "/api/debug/camera-open" -> WebHttpRoute.CAMERA_OPEN
            "/api/debug/camera-open-matrix" -> WebHttpRoute.CAMERA_OPEN_MATRIX
            "/api/control" -> WebHttpRoute.CONTROL
            else -> WebHttpRoute.NOT_FOUND
        }
    }

    fun frozenSnapshotId(target: String): String? {
        return frozenSnapshotPattern.matchEntire(target)?.groupValues?.getOrNull(1)
    }
}
