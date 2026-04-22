package com.example.demo.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class StreamWebSocketHandler extends AbstractWebSocketHandler {


    private static final Logger log = LoggerFactory.getLogger(StreamWebSocketHandler.class);


    private final Map<String, Process> sessions = new ConcurrentHashMap<>();

    private final Map<String, OutputStream> pipes = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String[] parts = uri.getPath().split("/");

        String userId   = parts[parts.length - 2];
        String streamId = parts[parts.length - 1];

        log.info("New stream started — userId={} streamId={}", userId, streamId);

        String rtmpUrl = "rtmp://localhost/live/" + userId + "_" + streamId;

        // ── Build the FFmpeg command ──────────────────────────────
        // Each String is one word. Never put spaces inside a String.
        //
        // -f webm         → input format is WebM (what MediaRecorder sends)
        // -i pipe:0       → read input from stdin (file descriptor 0)
        // -c:v libx264    → encode video with H.264 codec
        // -preset ultrafast → fastest encoding, lowest CPU usage
        // -tune zerolatency → optimize for live streaming, low delay
        // -c:a aac        → encode audio with AAC codec
        // -ar 44100       → audio sample rate 44,100 Hz
        // -f flv          → output format FLV (required for RTMP)
        // rtmpUrl         → push to this RTMP address
        // ─────────────────────────────────────────────────────────
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-f",       "webm",
                "-i",       "pipe:0",
                "-c:v",     "libx264",
                "-preset",  "ultrafast",
                "-tune",    "zerolatency",
                "-c:a",     "aac",
                "-ar",      "44100",
                "-f",       "flv",
                rtmpUrl
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        sessions.put(session.getId(), process);
        pipes.put(session.getId(), process.getOutputStream());
        Thread.ofVirtual().start(() -> {
            try (var reader = process.inputReader()) {
                reader.lines().forEach(line ->
                        log.debug("[ffmpeg] {}", line)
                );
            } catch (Exception e) {
                log.warn("FFmpeg log reader stopped — {}", e.getMessage());
            }
        });
        log.info("FFmpeg started — pushing to {}", rtmpUrl);
    }
    @Override
    protected void handleBinaryMessage(WebSocketSession session,
                                       BinaryMessage message) throws Exception {
        OutputStream pipe = pipes.get(session.getId());
        if (pipe == null) {
            log.warn("No pipe found for session {}", session.getId());
            return;
        }
        try {
            byte[] bytes = message.getPayload().array();
            pipe.write(bytes);
            pipe.flush();
        } catch (Exception e) {
            log.error("Failed to write to FFmpeg pipe for session {} — {}",
                    session.getId(), e.getMessage());
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) throws Exception {

        log.info("Stream ended — session={} status={}", session.getId(), status);

        OutputStream pipe = pipes.remove(session.getId());
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Exception e) {
                log.warn("Error closing pipe — {}", e.getMessage());
            }
        }
        Process process = sessions.remove(session.getId());
        if (process != null) {
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg did not finish in 10 seconds — force killed");
            } else {
                log.info("FFmpeg exited cleanly with code {}", process.exitValue());
            }
        }
    }
    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable exception) throws Exception {
        log.error("Transport error on session {} — {}", session.getId(), exception.getMessage());
        // Close the session — this will trigger afterConnectionClosed
        // which handles FFmpeg cleanup
        session.close(CloseStatus.SERVER_ERROR);
    }
}