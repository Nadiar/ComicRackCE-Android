# WiFi Sync Package

> **Package**: `com.comicrack.viewer.wifi`

This package implements the WiFi synchronization protocol between ComicRack Android
and ComicRack CE Desktop (Legacy). It enables wireless sync of comic metadata (.cbp)
and sidecar files (.cbp.xml) over the local network.

## Architecture Overview

```
wifi/
├── SyncIdentity.kt          # Protocol constants & identity generation
├── broadcast/               # UDP multicast discovery
├── file/                    # SAF-based file operations
├── model/                   # Data classes (DiscoveredServer, SyncResult)
├── network/                 # Network connectivity monitoring
├── progress/                # Sync progress tracking
├── protocol/                # Binary wire protocol implementation
├── server/                  # TCP server for sync connections
├── service/                 # Android foreground service
├── ui/                      # Compose UI (settings screen, ViewModel)
└── worker/                  # Background WorkManager jobs
```

## Package Responsibilities

### `model/`
Data classes representing protocol entities:
- `DiscoveredServer` - A discovered desktop server on the network
- `SyncSession` - An active sync session with state tracking
- `SyncResult` - Results and statistics from a completed sync

### `protocol/`
Binary wire protocol implementation matching the legacy desktop:
- `Command` - Sealed class representing all 14 protocol commands
- `CommandHandler` - Processes incoming commands and generates responses
- `ProtocolCodec` - Binary encoding/decoding (big-endian, length-prefixed)

### `broadcast/`
UDP multicast discovery (224.34.123.90:7615):
- `BroadcastManager` - Listens for ServerAvailable packets
- `BroadcastConfig` - Multicast configuration constants

### `server/`
TCP server accepting desktop connections (port 7614):
- `SyncServer` - Manages connection lifecycle
- `SyncServerState` - State machine for server status

### `file/`
SAF (Storage Access Framework) based file operations:
- `FileManager` - Interface for sync file operations
- `SyncFileManager` - SAF implementation
- `SyncIniManager` - [REMOVED] Legacy comicrack.ini handling (now virtualized)

### `service/`
Android foreground service orchestrating sync:
- `WiFiSyncService` - Main service entry point

### `ui/`
Jetpack Compose settings UI:
- `WiFiSyncSettingsScreen` - Main settings composable
- `WiFiSyncViewModel` - UI state management
- `WiFiSyncSettingsState` - UI state data class

### `network/`
Network connectivity monitoring:
- `NetworkMonitor` - Tracks WiFi state changes
- `WiFiState` - Sealed class for connection state

### `progress/`
Sync progress tracking:
- `SyncProgressTracker` - Observable progress state

### `worker/`
Background sync scheduling:
- `SyncCheckWorker` - Periodic sync availability check

## Protocol Summary

The WiFi sync protocol consists of 10 session types:

| Session | Purpose | Command(s) |
|---------|---------|------------|
| 1 | Discovery | UDP broadcast + ReadFile (comicrack.ini) |
| 2 | Pairing | Start (0x06) + Info (0x09) |
| 3 | List Files | ListFiles (0x00) |
| 4 | Read Metadata | ReadMultiFile (0x0A) |
| 5 | Delete Files | DeleteFile (0x04) |
| 6 | Progress | ProgressUpdate (0x08) |
| 7 | Check Abort | CheckAbort (0x0B) |
| 8 | Write Files | WriteFile (0x05) |
| 9 | Complete | Completed (0x07) |
| 10 | Background | ServerAvailable (0x0D) |

## Key Constants

```kotlin
// Port numbers
const val TCP_PORT = 7614
const val UDP_PORT = 7615

// Multicast group
const val MULTICAST_GROUP = "224.34.123.90"

// Protocol version
const val SYNC_VERSION = 100

// Edition string
const val SYNC_EDITION = "Android Full"
```

## Usage Example

```kotlin
// Start sync via service
val intent = Intent(context, WiFiSyncService::class.java).apply {
    action = WiFiSyncService.ACTION_START_SYNC
    data = syncFolderUri
}
context.startForegroundService(intent)

// Observe progress
progressTracker.progress.collect { progress ->
    Log.d("Sync", "Progress: ${progress.percent}%")
}
```

## Testing

Protocol compatibility can be tested with Wireshark:
- Filter: `tcp.port == 7614 || udp.port == 7615`
- Expected: Session 1 discovery should succeed
- Current blocker: Session 2 pairing validation

## Related Documentation

- [WIFI_SYNC_LADDER_DIAGRAMS.md](.copilot/projects/[SRC-06-004]-WiFiSync-with-Legacy-CRCE/WIFI_SYNC_LADDER_DIAGRAMS.md)
- [QUICK_REFERENCE_CARD.md](.copilot/projects/[SRC-06-004]-WiFiSync-with-Legacy-CRCE/QUICK_REFERENCE_CARD.md)
- Desktop source: `ComicRackCE_Legacy/ComicRack.Engine/Sync/WirelessSyncProvider.cs`
