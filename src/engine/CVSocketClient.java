package engine;

// Talks to cv_server.py over localhost:9999.
// sendCapture() blocks until the Python side saves the frame and responds with the path.
// Lifecycle: connect() -> sendCapture() (repeat) -> disconnect()

import java.io.*;
import java.net.Socket;

public class CVSocketClient {

    private static final String HOST    = "127.0.0.1";
    private static final int    PORT    = 9999;
    private static final int    TIMEOUT = 10_000; // ms - enough for slow first-frame grab

    private Socket           socket;
    private BufferedReader   reader;
    private PrintWriter      writer;

    // Opens the connection to cv_server.py.
    // Call this after you've already launched the Python process and given it ~1s to start.
    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        socket.setSoTimeout(TIMEOUT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    // Sends CAPTURE, waits for "DONE:<path>" or "ERROR:<msg>".
    // Returns the image path string on success.
    // Throws IOException on error or timeout.
    // Holds the parsed result of a CAPTURE response
    public static class CaptureResult {
        public final String imagePath;
        public final String yoloLabel;   // null if YOLO:NONE
        public final float  yoloConf;    // 0 if YOLO:NONE

        public CaptureResult(String imagePath, String yoloLabel, float yoloConf) {
            this.imagePath = imagePath;
            this.yoloLabel = yoloLabel;
            this.yoloConf  = yoloConf;
        }

        public boolean hasYoloHit() { return yoloLabel != null; }
    }

    public CaptureResult sendCapture() throws IOException {
        if (socket == null || socket.isClosed())
            throw new IOException("Not connected to cv_server.");

        writer.println("CAPTURE");

        String response = reader.readLine();
        if (response == null)
            throw new IOException("cv_server closed connection unexpectedly.");

        if (response.startsWith("ERROR:"))
            throw new IOException("cv_server error: " + response.substring(6));

        if (!response.startsWith("DONE:"))
            throw new IOException("Unexpected response: " + response);

        // Format: DONE:<path>|YOLO:<label>:<conf>  or  DONE:<path>|YOLO:NONE
        String[] parts     = response.substring(5).split("\\|", 2);
        String   imagePath = parts[0].trim();
        String   yoloLabel = null;
        float    yoloConf  = 0f;

        if (parts.length == 2 && parts[1].startsWith("YOLO:")) {
            String yoloPart = parts[1].substring(5); // e.g. "knife:0.9100" or "NONE"
            if (!yoloPart.equals("NONE")) {
                String[] lc = yoloPart.split(":", 2);
                yoloLabel = lc[0].trim().toLowerCase();
                if (lc.length == 2) {
                    try { yoloConf = Float.parseFloat(lc[1].trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        return new CaptureResult(imagePath, yoloLabel, yoloConf);
    }

    // Sends STOP and closes the socket cleanly.
    public void disconnect() {
        try {
            if (writer != null) writer.println("STOP");
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        reader = null;
        writer = null;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}