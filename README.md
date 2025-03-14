# CNT5106C - P2P FILE SHARING SYSTEM

### Compilation Steps

```bash
make clean # needed if you have already compiled the code
make
make Peer
```

### File Descriptions

├── **Handler**
└── **`MsgHandler.java`** - Handles peer-to-peer message exchange and handshake processing.

├── **Logging**
└── **`Helper.java`** - Provides utility methods for logging messages to files.
└── **`LogFormatter.java`** - Formats log messages with timestamps for readability.

├── **Metadata**
└── **`MsgMetadata.java`** - Stores metadata related to messages exchanged between peers.
└── **`PeerMetadata.java`** - Manages information about peers, including their states and file availability.

├── **Msgs**
└── **`BitField.java`** - Manages bitfield representation of file piece availability.
└── **`Constants.java`** - Defines constants used throughout the system.
└── **`Details.java`** - Represents message details exchanged between peers.
└── **`FilePiece.java`** - Handles individual file pieces for file sharing.
└── **`Handshake.java`** - Manages handshake messages between peers.
└── **`Msg.java`** - Defines the structure of peer-to-peer messages.

├── **Process**
└── **`Peer.java`** - Main process managing peer initialization, configuration, and execution.

├── **Queue**
└── **`MsgQueue.java`** - Implements a queue for storing and processing incoming messages.

### Java Version Used

- `openjdk 21.0.6 2025-01-21`
- `OpenJDK Runtime Environment (build 21.0.6+7-Ubuntu-124.04.1)`
- `OpenJDK 64-Bit Server VM (build 21.0.6+7-Ubuntu-124.04.1, mixed mode, sharing)`
- `javac 21.0.6`