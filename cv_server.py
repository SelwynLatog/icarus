import socket
import threading
import cv2
import os
import time
from ultralytics import YOLO

# --- Config ---
HOST         = "127.0.0.1"
PORT         = 9999
CAPTURE_DIR  = "assets/temp"
CAPTURE_PATH = os.path.join(CAPTURE_DIR, "capture.jpg")
MODEL_NAME   = "yolov8n.pt"
WINDOW_NAME  = "ICARUS - Live Detection  (Enter to capture, Q to quit)"

# Minimum confidence to trust a YOLO detection
YOLO_CONF_THRESHOLD = 0.15

# COCO labels we care about - everything else is ignored by Layer 1
# Java falls through to ORB (Layer 2) for anything not in this set
RELEVANT_LABELS = {
    "bottle",
    "cup",
    "knife",
    "scissors",
    "wine glass",
    "fork",
    "spoon",
    "cell phone",
    "cigarette",
}

# Shared state
latest_frame  = None
latest_labels = None        # (label, confidence) or None
frame_lock    = threading.Lock()
capture_event = threading.Event()   # set by Enter key, consumed by handle_client
running       = True


def pick_best_label(results):
    """
    Returns (label, confidence) of the highest-confidence relevant detection,
    or None if nothing in RELEVANT_LABELS exceeded the threshold.
    """
    best_label = None
    best_conf  = 0.0

    for result in results:
        for box in result.boxes:
            conf  = float(box.conf[0])
            cls   = int(box.cls[0])
            label = result.names[cls].lower()

            if conf < YOLO_CONF_THRESHOLD:
                continue
            if label not in RELEVANT_LABELS:
                continue
            if conf > best_conf:
                best_conf  = conf
                best_label = label

    return (best_label, best_conf) if best_label else None


def do_capture():
    """
    Saves the latest frame to disk and returns the response string.
    Called both from handle_client (CAPTURE command) and Enter key path.
    """
    with frame_lock:
        frame  = latest_frame.copy() if latest_frame is not None else None
        labels = latest_labels

    if frame is None:
        return None, "ERROR:No frame available\n"

    os.makedirs(CAPTURE_DIR, exist_ok=True)
    cv2.imwrite(CAPTURE_PATH, frame)

    if labels:
        label, conf = labels
        response = f"DONE:{CAPTURE_PATH}|YOLO:{label}:{conf:.4f}\n"
    else:
        response = f"DONE:{CAPTURE_PATH}|YOLO:NONE\n"

    return frame, response


def webcam_loop():
    """
    Runs on the main thread (OpenCV requires imshow on main thread on Windows).
    Reads frames, runs YOLO, updates shared state, handles Enter/Q keypresses.
    """
    global latest_frame, latest_labels, running

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("[ERROR] Could not open webcam.", flush=True)
        running = False
        return

    model = YOLO(MODEL_NAME)
    print("[READY] Webcam open. YOLO loaded.", flush=True)

    # Fullscreen window
    cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)
    cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_FULLSCREEN)

    while running:
        ret, frame = cap.read()
        if not ret:
            continue

        results   = model(frame, verbose=False)

        # Only draw boxes for relevant labels - suppress everything else
        best = pick_best_label(results)

        # Build a clean annotated frame showing only relevant detections
        annotated = frame.copy()
        for result in results:
            for box in result.boxes:
                cls   = int(box.cls[0])
                conf  = float(box.conf[0])
                label = result.names[cls].lower()
                if label not in RELEVANT_LABELS or conf < YOLO_CONF_THRESHOLD:
                    continue
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                color = (0, 255, 80)
                cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 2)
                cv2.putText(annotated, f"{label} {conf:.0%}",
                            (x1, y1 - 8), cv2.FONT_HERSHEY_SIMPLEX,
                            0.7, color, 2)

        # Status overlay
        if best:
            status = f"DETECTED: {best[0].upper()}  ({best[1]:.0%})  -  Press ENTER to capture"
            color  = (0, 255, 80)
        else:
            status = "Scanning. No relevant item detected"
            color  = (180, 180, 180)

        cv2.putText(annotated, status, (10, 36),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.4, color, 2)

        cv2.imshow(WINDOW_NAME, annotated)

        key = cv2.waitKey(1) & 0xFF
        if key == ord('q'):
            running = False
            break
        elif key == ord('e'):  # Enter
            capture_event.set()
            print("[E] Capture triggered from window.", flush=True)

        with frame_lock:
            latest_frame  = frame.copy()
            latest_labels = best

    cap.release()
    cv2.destroyAllWindows()


def handle_client(conn):
    """Handles a single Java connection. Responds to CAPTURE and STOP commands,
    and also pushes captures triggered by the Enter key."""
    global running
    print("[CONN] Java client connected.", flush=True)

    conn.settimeout(0.1)   # non-blocking so we can poll capture_event

    with conn:
        buf = ""
        while running:
            # --- Enter key capture (pushed to Java without it asking) ---
            if capture_event.is_set():
                capture_event.clear()
                _, response = do_capture()
                conn.sendall(response.encode("utf-8"))
                print(f"[ENTER-CAPTURE] {response.strip()}", flush=True)
                continue

            # --- Normal Java-initiated command ---
            try:
                data = conn.recv(1024).decode("utf-8")
                if not data:
                    break
                buf += data

                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    cmd = line.strip()

                    if cmd == "CAPTURE":
                        _, response = do_capture()
                        conn.sendall(response.encode("utf-8"))
                        print(f"[CAPTURE] {response.strip()}", flush=True)

                    elif cmd == "STOP":
                        print("[STOP] Shutdown requested.", flush=True)
                        running = False
                        conn.sendall(b"OK\n")
                        return

            except socket.timeout:
                continue    # normal - just loop back and check capture_event
            except Exception as e:
                print(f"[ERROR] Client handler: {e}", flush=True)
                break

    print("[CONN] Client disconnected.", flush=True)


def server_loop():
    """Accepts one Java connection at a time on localhost:9999."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((HOST, PORT))
        srv.listen(1)
        srv.settimeout(1.0)
        print(f"[SERVER] Listening on {HOST}:{PORT}", flush=True)

        while running:
            try:
                conn, addr = srv.accept()
                handle_client(conn)
            except socket.timeout:
                continue
            except Exception as e:
                if running:
                    print(f"[ERROR] Server: {e}", flush=True)


if __name__ == "__main__":
    os.makedirs(CAPTURE_DIR, exist_ok=True)

    server_thread = threading.Thread(target=server_loop, daemon=True)
    server_thread.start()

    time.sleep(0.3)
    webcam_loop()

    print("[EXIT] cv_server.py shut down.", flush=True)