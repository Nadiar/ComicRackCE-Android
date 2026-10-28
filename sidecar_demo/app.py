import socket
import struct
import threading
import time
import logging
from typing import Dict, List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Protocol constants (same as legacy)
CMD_SERVER_AVAILABLE = 13
MCAST_GRP = '224.34.123.90'
MCAST_PORT = 7615
TCP_TIMEOUT = 2.0

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('sidecar')

app = FastAPI(title='ComicRack Sidecar Demo')

class Device(BaseModel):
    ip: str
    key: str
    lastSeen: float
    paired: bool = False

class PairRequest(BaseModel):
    deviceKey: str
    pairingCode: str = Field(default="")

class NotifyRequest(BaseModel):
    deviceIp: str
    controlPort: int

# in-memory device store keyed by deviceKey
devices: Dict[str, Device] = {}
devices_lock = threading.Lock()


def _listen_multicast(mcast_grp: str = MCAST_GRP, mcast_port: int = MCAST_PORT):
    """Background multicast listener for "ComicRack:<key>[:Sync]" announcements."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(('', mcast_port))
    except Exception as e:
        logger.warning('Failed to bind multicast socket: %s', e)
        return
    mreq = struct.pack('4sl', socket.inet_aton(mcast_grp), socket.INADDR_ANY)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
    logger.info('Multicast listener started on %s:%d', mcast_grp, mcast_port)
    while True:
        try:
            data, addr = sock.recvfrom(2048)
            try:
                text = data.decode('utf-8')
            except Exception:
                continue
            parts = text.split(':')
            if not parts or not parts[0].startswith('ComicRack'):
                continue
            key = parts[1] if len(parts) > 1 else ''
            now = time.time()
            with devices_lock:
                devices[key] = Device(ip=addr[0], key=key, lastSeen=now, paired=devices.get(key, Device(ip=addr[0], key=key, lastSeen=now)).paired)
            logger.info('Discovered device %s @%s', key, addr[0])
        except Exception as e:
            logger.debug('Multicast listener error: %s', e)
            time.sleep(1)


@app.on_event('startup')
def startup_event():
    t = threading.Thread(target=_listen_multicast, daemon=True)
    t.start()


@app.get('/api/wireless/devices', response_model=List[Device])
def get_devices():
    with devices_lock:
        return list(devices.values())


@app.post('/api/wireless/pair')
def pair_device(req: PairRequest):
    with devices_lock:
        dev = devices.get(req.deviceKey)
        if not dev:
            raise HTTPException(status_code=404, detail='Device not found')
        dev.paired = True
        devices[req.deviceKey] = dev
    return {'success': True, 'deviceKey': req.deviceKey}


@app.post('/api/wireless/notify-available')
def notify_available(req: NotifyRequest):
    # Connect to device and send COMMAND_SERVER_AVAILABLE(13) + int(controlPort) big-endian
    logger.info('notify-available: %s:%d', req.deviceIp, req.controlPort)
    try:
        s = socket.create_connection((req.deviceIp, 7614), timeout=TCP_TIMEOUT)
        s.sendall(bytes([CMD_SERVER_AVAILABLE]))
        # send int32 big-endian
        s.sendall(struct.pack('>I', req.controlPort))
        s.close()
        return {'success': True}
    except Exception as e:
        logger.exception('Failed notify-available')
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == '__main__':
    import uvicorn
    uvicorn.run('sidecar_demo.app:app', host='0.0.0.0', port=8000, reload=False)
