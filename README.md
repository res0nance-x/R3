Used by GraffitiCore (and other projects)

`R3` is the base layer providing networking primitives, cryptography, content handling, UPnP port mapping, and lightweight HTTP/WebSocket serving.

* **Embedded/Vendorized Packages**:
  * `org.nanohttpd.*`: Lightweight HTTP server & WebSocket engine
  * `r3.org.json.*`: JSON parser and object serialization.
* **Core Subsystems Provided**:
  * `r3.net`: Socket, IP discovery, and JSON communication.
  * `r3.http`: Request routing, captive portal, range requests, file and resource handlers.
  * `r3.pack`: Pack file format packaging, hashing, and streaming.
  * `r3.encryption` & `r3.pke`: Asymmetric and symmetric cryptography (AES, RSA, ECC).
  * `r3.upnp`: UPnP Gateway discovery and NAT port mapping.
