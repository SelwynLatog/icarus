import socket
import threading
import queue
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
WINDOW_NAME  = "ICARUS - Live Cam"

YOLO_CONF_THRESHOLD = 0.15

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
latest_frame     = None
latest_labels    = None        # (label, confidence) or None
latest_annotated = None        # pre-rendered frame ready to imshow
frame_lock       = threading.Lock()
capture_queue    = queue.Queue()   # each E press enqueues one capture token
running          = True


def pick_best_label(results):
    best_label = None
    best_conf  = 0.0
    for result in results:
        for box in result.boxes:
            conf  = float(box.conf[0])
            cls   = int(box.cls[0])
            label = result.names[cls].lower()
            if conf < YOLO_CONF_THRESHOLD or label not in RELEVANT_LABELS:
                continue
            if conf > best_conf:
                best_conf  = conf
                best_label = label
    return (best_label, best_conf) if best_label else None


def do_capture(model):
    with frame_lock:
        frame = latest_frame.copy() if latest_frame is not None else None

    if frame is None:
        return "ERROR:No frame available\n"

    os.makedirs(CAPTURE_DIR, exist_ok=True)
    cv2.imwrite(CAPTURE_PATH, frame)

    # Run YOLO synchronously on the exact saved frame - never stale
    results = model(frame, verbose=False)
    best    = pick_best_label(results)

    if best:
        label, conf = best
        return f"DONE:{CAPTURE_PATH}|YOLO:{label}:{conf:.4f}\n"
    else:
        return f"DONE:{CAPTURE_PATH}|YOLO:NONE\n"


def inference_loop(model):
    """
    Runs YOLO on a background thread so the main thread never blocks on inference.
    Pulls the latest raw frame, runs inference, and writes back the annotated frame
    and detected labels under frame_lock.
    """
    global latest_annotated, latest_labels

    while running:
        with frame_lock:
            frame = latest_frame.copy() if latest_frame is not None else None

        if frame is None:
            time.sleep(0.01)
            continue

        results = model(frame, verbose=False)
        best    = pick_best_label(results)

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

        if best:
            status = f"DETECTED: {best[0].upper()}  ({best[1]:.0%})"
            color  = (0, 255, 80)
        else:
            status = "Scanning. No relevant item detected"
            color  = (180, 180, 180)

        cv2.putText(annotated, status, (10, 36),
                    cv2.FONT_HERSHEY_TRIPLEX, 0.6, color, 2)

        with frame_lock:
            latest_annotated = annotated
            latest_labels    = best


def webcam_loop(model):
    """
    Main thread: reads frames and handles keypresses only.
    YOLO runs on a background thread so waitKey is never starved.
    """
    global latest_frame, running

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("[ERROR] Could not open webcam.", flush=True)
        running = False
        return

    model = YOLO(MODEL_NAME)
    print("[READY] Webcam open. YOLO loaded.", flush=True)

    cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)

    # Start inference on a background thread
    infer_thread = threading.Thread(target=inference_loop, args=(model,), daemon=True)
    infer_thread.start()

    while running:
        ret, frame = cap.read()
        if not ret:
            continue

        # Store latest raw frame for inference thread and do_capture()
        with frame_lock:
            latest_frame = frame.copy()
            annotated    = latest_annotated.copy() if latest_annotated is not None else frame.copy()

        cv2.imshow(WINDOW_NAME, annotated)

        # waitKey is now called every frame with no YOLO blocking it
        key = cv2.waitKey(1) & 0xFF
        if key == ord('q'):
            running = False
            break
        elif key == ord('e'):
            capture_queue.put(1)
            print("Capture queued.", flush=True)

    cap.release()
    cv2.destroyAllWindows()


def handle_client(conn):
    global running
    print("[CONN] Java client connected.", flush=True)

    conn.settimeout(0.1)

    with conn:
        buf = ""
        while running:
            # Drain all E-key captures and send each to Java
            while not capture_queue.empty():
                try:
                    capture_queue.get_nowait()
                    response = do_capture()
                    conn.sendall(response.encode("utf-8"))
                    print(f"[E-CAPTURE] {response.strip()}", flush=True)
                except queue.Empty:
                    break

            # Normal Java-initiated command
            try:
                data = conn.recv(1024).decode("utf-8")
                if not data:
                    break
                buf += data

                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    cmd = line.strip()

                    if cmd == "CAPTURE":
                        response = do_capture()
                        conn.sendall(response.encode("utf-8"))
                        print(f"[CAPTURE] {response.strip()}", flush=True)

                    elif cmd == "STOP":
                        print("[STOP] Shutdown requested.", flush=True)
                        running = False
                        conn.sendall(b"OK\n")
                        return

            except socket.timeout:
                continue
            except Exception as e:
                print(f"[ERROR] Client handler: {e}", flush=True)
                break

    print("[CONN] Client disconnected.", flush=True)


def handle_client(conn, model):
    global running
    print("[CONN] Java client connected.", flush=True)

    conn.settimeout(0.1)

    with conn:
        buf = ""
        while running:
            # Drain all E-key captures and send each to Java
            while not capture_queue.empty():
                try:
                    capture_queue.get_nowait()
                    response = do_capture(model)
                    conn.sendall(response.encode("utf-8"))
                    print(f"[CAPTURE] {response.strip()}", flush=True)
                except queue.Empty:
                    break

            # Normal Java-initiated command
            try:
                data = conn.recv(1024).decode("utf-8")
                if not data:
                    break
                buf += data

                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    cmd = line.strip()

                    if cmd == "CAPTURE":
                        response = do_capture(model)
                        conn.sendall(response.encode("utf-8"))
                        print(f"[CAPTURE] {response.strip()}", flush=True)

                    elif cmd == "STOP":
                        print("[STOP] Shutdown requested.", flush=True)
                        running = False
                        conn.sendall(b"OK\n")
                        return

            except socket.timeout:
                continue
            except Exception as e:
                print(f"[ERROR] Client handler: {e}", flush=True)
                break

    print("[CONN] Client disconnected.", flush=True)


def server_loop(model):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((HOST, PORT))
        srv.listen(1)
        srv.settimeout(1.0)
        print(f"[SERVER] Listening on {HOST}:{PORT}", flush=True)

        while running:
            try:
                conn, addr = srv.accept()
                handle_client(conn, model)
            except socket.timeout:
                continue
            except Exception as e:
                if running:
                    print(f"[ERROR] Server: {e}", flush=True)


if __name__ == "__main__":
    os.makedirs(CAPTURE_DIR, exist_ok=True)

    # Load model once - shared by inference_loop (display) and do_capture (decisions)
    model = YOLO(MODEL_NAME)

    server_thread = threading.Thread(target=server_loop, args=(model,), daemon=True)
    server_thread.start()

    time.sleep(0.3)
    webcam_loop(model)

    print("[EXIT] cv_server.py shut down.", flush=True)