# BinaryPack Format Specification (Version 1)

## 1. Overview & Objectives

`BinaryPack` is an append-friendly, streamable archive container format developed for the `R3` runtime and utilized across projects like `Graffiti` and `GraffitiCore`. It packages files and web assets into a structured binary stream.

### Core Design Goals
- **$O(1)$ Mount Time**: All entry metadata is located at the front of each block. Mounting an archive requires only reading the index header and metadata table, requiring zero payload seeks or reads until an entry's content is requested.
- **Append & Concatenation Friendly**: Archives can be concatenated directly using byte-level concatenation (`cat a.pack b.pack > combined.pack`) or standard file append modes (`FileSink(append = true)`).
- **Crash & Corruption Resilience**: Prevents Out-Of-Memory (OOM) errors and unbounded allocations on corrupted data by utilizing a magic sync signature, 32-bit CRC index checksums, and strict upper bounds on string and table sizes.
- **Logical Deletions (Tombstones)**: Supports instant append-only file deletions without rewriting data payloads.
- **Built-in Compaction**: Allows purging overwritten revisions and tombstones by streaming live entries into a fresh single-block archive.

---

## 2. Byte Order & Data Types

All multi-byte numeric values are encoded in **Big-Endian** (network byte order), matching standard `java.io.DataOutputStream` conventions:

| Type | Size | Description |
| :--- | :--- | :--- |
| `Byte` | 1 byte | Signed 8-bit integer (`-128` to `127`) |
| `Short` | 2 bytes | Signed 16-bit big-endian integer |
| `Int` | 4 bytes | Signed 32-bit big-endian integer |
| `Long` | 8 bytes | Signed 64-bit big-endian integer |
| `SafeString` | $4 + N$ bytes | `Int` byte count followed by $N$ UTF-8 bytes |

### String Encoding (`SafeString`)
Strings (file paths and extensions) are prefixed with a 4-byte signed `Int` indicating length in bytes, followed by raw UTF-8 bytes:
```text
+-----------------------+-----------------------------+
| Byte Length: Int (4B) | UTF-8 Encoded Bytes (N B)   |
+-----------------------+-----------------------------+
```
- **Max Path Length**: 4,096 bytes (`MAX_PATH_LENGTH`).
- **Max Type/Ext Length**: 128 bytes (`MAX_TYPE_LENGTH`).
- Any string length outside `0..max` is rejected as corruption before memory allocation.

---

## 3. High-Level Layout

An archive consists of one or more **Blocks**. Each block contains a fixed 26-byte Index Header, a serialized Index Table (protected by CRC32), and the concatenated raw payloads for all active files declared in that block.

```text
+=============================================================================+
|                                 BLOCK 0                                     |
+-----------------------------------------------------------------------------+
| 1. Index Block Header (Fixed 26 bytes)                                      |
|    [Magic: "R3PK"] [Version: 1] [Entry Count] [Total Span] [Data Size] [CRC] |
+-----------------------------------------------------------------------------+
| 2. Serialized Index Table (indexDataSize bytes, CRC32 verified)             |
|    [Entry 0 Record] [Entry 1 Record] ... [Entry N Record]                   |
+-----------------------------------------------------------------------------+
| 3. Raw Data Payloads (totalSpan bytes)                                      |
|    [Payload 0] [Payload 1] ... [Payload N]                                  |
+=============================================================================+
|                                 BLOCK 1 (Appended Chunk)                    |
+-----------------------------------------------------------------------------+
| 1. Index Block Header (Fixed 26 bytes)                                      |
| 2. Serialized Index Table (indexDataSize bytes)                             |
| 3. Raw Data Payloads (totalSpan bytes)                                      |
+=============================================================================+
```

---

## 4. Detailed Block Structure

### 4.1 Index Block Header (26 Bytes)

Located at the start of each block:

| Offset | Field | Type | Constant / Range | Description |
| :--- | :--- | :--- | :--- | :--- |
| `0x00` | `magic` | `Int` | `0x5233504B` | ASCII `"R3PK"` signature |
| `0x04` | `version` | `Short` | `1` | Format specification version |
| `0x06` | `entryCount` | `Int` | `0 .. 10,000,000` | Number of entries defined in this block |
| `0x0A` | `totalSpan` | `Long` | $\ge 0$ | Cumulative byte length of all active payloads in this block |
| `0x12` | `indexDataSize`| `Int` | `0 .. 100,000,000` | Byte length of the Serialized Index Table |
| `0x16` | `expectedCrc` | `Int` | 32-bit CRC | IEEE 802.3 CRC32 checksum computed over the `indexData` bytes |

### 4.2 Serialized Index Table (`indexDataSize` Bytes)

Directly follows the 26-byte header. Contains `entryCount` serialized records:

#### Entry Record Format
For each entry ($i = 0 \dots \text{entryCount} - 1$):

| Field | Type | Max Size | Description |
| :--- | :--- | :--- | :--- |
| `flag` | `Byte` | 1 byte | Entry status: `0x00` = `FLAG_ACTIVE`, `0x01` = `FLAG_DELETED` |
| `path` | `SafeString` | 4B + 4096B | Relative file path identifier (e.g. `images/logo.png`) |
| `type` | `SafeString` | 4B + 128B | Content extension / MIME identifier (e.g. `png`) |
| `length` | `Long` | 8 bytes | Payload size in bytes ($\ge 0$ for active entries; `0L` for tombstones) |
| `created`| `Long` | 8 bytes | Unix epoch timestamp in milliseconds |

### 4.3 Raw Payload Region (`totalSpan` Bytes)

Directly follows the Serialized Index Table. Payloads for all entries with `flag == FLAG_ACTIVE` are written back-to-back in the exact sequence they were declared in the index table.

- If an entry has `flag == FLAG_DELETED`, it has `length == 0` and produces **zero** payload bytes.
- Total bytes in this region **must** strictly equal `totalSpan`:
  $$\text{totalSpan} = \sum_{e \in \text{entries}, \, e.\text{flag} = \text{ACTIVE}} e.\text{length}$$

---

## 5. Relative Offset Addressing & Multi-Block Traversal

A primary architectural feature of Version 1 is **relocatable relative addressing**. No entry record stores an absolute file offset.

### 5.1 Offset Calculation
For any given block starting at file position `currentBlockOffset`:
```text
payloadBasePos = currentBlockOffset + 26L + indexDataSize
```
As active entries in the index table are enumerated, their relative offsets are computed via a running sum:
$$\text{entryAbsolutePos}_0 = \text{payloadBasePos}$$
$$\text{entryAbsolutePos}_k = \text{entryAbsolutePos}_{k-1} + \text{length}_{k-1}$$

### 5.2 Traversal Algorithm
To iterate or mount an archive from an `InputStream`:
1. Read 4 bytes. If EOF, terminate cleanly.
2. Verify `magic == 0x5233504B`.
3. Read `version`, `entryCount`, `totalSpan`, `indexDataSize`, `expectedCrc`.
4. Read `indexDataSize` bytes into memory.
5. Compute CRC32 of these bytes and assert equality with `expectedCrc`.
6. Parse entry records and register them into the in-memory index.
7. Skip `totalSpan` payload bytes using `stream.skipFullBytes(totalSpan)`.
8. Update `currentBlockOffset = payloadBasePos + totalSpan` and repeat from step 1.

---

## 6. Deletions (Tombstones) & Compaction

### 6.1 Logical Deletions
To delete one or more files in an existing pack without rewriting the archive:
1. Append a new block with `entryCount = number_of_paths_to_delete`.
2. For each path, emit an entry with:
   - `flag = 0x01` (`FLAG_DELETED`)
   - `path = <target_path>`
   - `type = ""`
   - `length = 0L`
   - `created = <current_timestamp>`
3. Set `totalSpan = 0L` (no payloads written).
4. Set `indexDataSize` and compute CRC32 as usual.

When loaded into memory, a `FLAG_DELETED` entry purges all previous occurrences of `path` from the archive index.

### 6.2 Compaction
Over time, appending file updates and tombstones leaves unreferenced payload bytes in older blocks. Compaction is implemented via `BinaryPack.compact(source, sink)`:
- Reads the archive sequentially into a deduplicated, tombstone-purged in-memory map.
- Streams the remaining active entries into a brand new, single-block `BinaryPack`.
- Completely reclaims dead disk space.

---

## 7. Integrity & Corruption Mitigation

Version 1 specifically prevents issues observed in legacy one-way linked list formats:

| Vulnerability in Legacy Format | Version 1 Mitigation |
| :--- | :--- |
| **Out-Of-Memory on Noise**: Corrupted bytes parsed as 10MB string lengths or huge longs | Strict limits (`MAX_PATH_LENGTH=4096`, `MAX_INDEX_DATA_SIZE=100MB`) reject corrupt sizes before memory allocation. |
| **Header Desynchronization**: A corrupted length jumps reader into arbitrary payload data | Sync signature (`R3PK`) and CRC32 ensure corrupt headers are aborted immediately without reading raw data as headers. |
| **Payload Discrepancies**: Truncated writes | Verified by asserting `runningRelativeOffset == totalSpan`. |

---

## 8. Version History

- **Version 0 (Alpha Legacy)**:
  - Headerless stream of `[StringWritable(name)][StringWritable(type)][Long(length)][Long(created)][Payload...]`.
  - Required scanning every entry header sequentially to build index.
  - Susceptible to OOM when corrupted lengths caused misaligned header reads.
- **Version 1 (Current)**:
  - Introduced `"R3PK"` signature, 26-byte index header, CRC32 table protection.
  - Front-loaded index for instant $O(1)$ mounts.
  - Relative offset addressing for direct concatenation and streaming appends.
  - Support for `FLAG_DELETED` tombstones and single-pass compaction.
