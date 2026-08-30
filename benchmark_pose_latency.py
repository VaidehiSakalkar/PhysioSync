import asyncio
import base64
import json
import statistics
import time
import cv2
import numpy as np
import websockets

def generate_test_frame():
    """Create a realistic 640x480 JPEG image."""
    img = np.zeros((480, 640, 3), dtype=np.uint8)
    # Draw simple background and geometric shapes
    cv2.circle(img, (320, 240), 100, (255, 255, 255), -1)
    _, buffer = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), 60])
    return base64.b64encode(buffer).decode()

async def benchmark(num_frames=100, uri="ws://localhost:8000/ws/pose"):
    b64_frame = generate_test_frame()
    latencies = []

    print(f"Connecting to {uri}...")
    async with websockets.connect(uri) as ws:
        # Warmup frame (MediaPipe first-run initialization)
        print("Sending warmup frame...")
        await ws.send(json.dumps({"frame": b64_frame}))
        await ws.recv()

        print(f"Benchmarking {num_frames} frames...")
        for i in range(num_frames):
            start = time.perf_counter()
            await ws.send(json.dumps({"frame": b64_frame}))
            resp = await ws.recv()
            duration_ms = (time.perf_counter() - start) * 1000.0
            latencies.append(duration_ms)
            await asyncio.sleep(0.03)  # ~30 FPS rate simulation

    print("\n--- Latency Benchmark Results ---")
    print(f"Total Frames Tested: {len(latencies)}")
    print(f"Average Latency:    {statistics.mean(latencies):.2f} ms")
    print(f"Median (P50):       {statistics.median(latencies):.2f} ms")
    p95 = statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else max(latencies)
    print(f"95th Percentile:    {p95:.2f} ms")
    print(f"Min / Max:          {min(latencies):.2f} ms / {max(latencies):.2f} ms")
    
    if statistics.mean(latencies) < 100.0:
        print("\nSUCCESS: Sub-100ms real-time latency validated! (Mean < 100ms)")
    else:
        print("\nWARNING: Mean latency exceeded 100ms threshold.")

if __name__ == "__main__":
    asyncio.run(benchmark(100))
