import socket
import struct
import threading
import time

import pytest
from fastapi.testclient import TestClient

from sidecar_demo.app import app

client = TestClient(app)


def send_announce(key='test-device', mcast_grp='224.34.123.90', mcast_port=7615):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    ttl = struct.pack('b', 1)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, ttl)
    msg = f'ComicRack:{key}:Sync'.encode('utf-8')
    sock.sendto(msg, (mcast_grp, mcast_port))
    sock.close()


def run_dummy_listener(port=7614, expected_opcode=13, timeout=2.0):
    # accept a single connection and read opcode and int
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('127.0.0.1', port))
    s.listen(1)
    s.settimeout(timeout)
    conn, addr = s.accept()
    opcode = conn.recv(1)[0]
    data = conn.recv(4)
    control_port = struct.unpack('>I', data)[0]
    conn.close()
    s.close()
    return opcode, control_port


def test_discovery_and_pairing():
    # Send announce and allow listener to pick it up
    send_announce('test-device')
    # give background thread time to process
    time.sleep(0.2)
    r = client.get('/api/wireless/devices')
    assert r.status_code == 200
    devices = r.json()
    assert any(d['key'] == 'test-device' for d in devices)

    # Pair the device
    r = client.post('/api/wireless/pair', json={'deviceKey': 'test-device'})
    assert r.status_code == 200
    r = client.get('/api/wireless/devices')
    devices = r.json()
    assert any(d['key'] == 'test-device' and d['paired'] for d in devices)


def test_notify_available():
    # start dummy listener to receive notify
    results = {}

    def listener_thread():
        code, port = run_dummy_listener(port=7614)
        results['code'] = code
        results['port'] = port

    t = threading.Thread(target=listener_thread, daemon=True)
    t.start()
    time.sleep(0.05)
    r = client.post('/api/wireless/notify-available', json={'deviceIp': '127.0.0.1', 'controlPort': 9000})
    assert r.status_code == 200
    # allow listener to finish
    t.join(timeout=2)
    assert results['code'] == 13
    assert results['port'] == 9000
