import socket
import subprocess
import time
import uuid
import sys

# Configuration matches ComicRack Android defaults
MULTICAST_GROUP = '224.34.123.90'
UDP_PORT = 7615
TCP_PORT = 7614
BEACON_INTERVAL = 5 # seconds

def setup_port_forwarding():
    """Execute adb forward to map the TCP data port."""
    print(f"[*] Setting up ADB port forwarding for TCP {TCP_PORT}...")
    try:
        # Forward local TCP 7614 to emulator TCP 7614
        cmd = ["adb", "forward", f"tcp:{TCP_PORT}", f"tcp:{TCP_PORT}"]
        subprocess.check_call(cmd)
        print("[+] Port forwarding successful.")
    except subprocess.CalledProcessError as e:
        print(f"[-] Failed to run adb forward: {e}")
        print("    Ensure 'adb' is in your PATH and an emulator is connected.")
        sys.exit(1)
    except FileNotFoundError:
        print("[-] 'adb' command not found. Please install Android SDK Platform Tools/ADB.")
        sys.exit(1)

def get_beacon_data():
    """Generate the discovery beacon payload."""
    # Format: ComicRackAndroid:<DEVICE_ID>
    # The device ID acts as a unique key for the desktop app.
    # We generate a random UUID to simulate a unique emulator device.
    device_id = str(uuid.uuid4())
    payload = f"ComicRackAndroid:{device_id}"
    print(f"[*] Generated Emulator Device ID: {device_id}")
    return payload.encode('utf-8')

def main():
    setup_port_forwarding()

    # Create UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    
    # Set TTL to 1 (local network only)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)

    beacon_data = get_beacon_data()
    print(f"[*] Starting Multicast Beacon to {MULTICAST_GROUP}:{UDP_PORT}")
    print("[*] Press Ctrl+C to stop.")

    try:
        while True:
            # Send the beacon
            sock.sendto(beacon_data, (MULTICAST_GROUP, UDP_PORT))
            
            # Sleep until next beacon
            time.sleep(BEACON_INTERVAL)
    except KeyboardInterrupt:
        print("\n[*] Stopping beacon...")
    finally:
        sock.close()

if __name__ == "__main__":
    main()
