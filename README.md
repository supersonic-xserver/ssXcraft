# ssXcraft

A zero-latency, pure Java 25 + NeoForge + XCB implementation for running a XCB compositor inside Minecraft 1.21.3.

## Architectural Overview

### The Problem: Legacy Wayland Compositor Lag

Previous implementations embedded a Rust/Smithay compositor inside the Java game loop via JNI. This architecture suffers from **indirect composition pipeline lag**:

```
Java Game Loop → JNI Bridge → Rust Compositor → Wayland Protocol → Client
     ↑________________________________________________________|
     (synchronous blocking, ~8-16ms per frame overhead)
```

The compositor runs in the same thread as Minecraft's render loop, causing:
- Synchronous protocol round-trips blocking the render thread
- Event dispatch latency compounding each frame
- Memory allocation pressure from cross-language buffer copies
- Unpredictable frame pacing due to JNI overhead

### The ssXcraft Solution: Zero-Latency X11 Pipeline

ssXcraft abandons the embedded Rust compositor entirely, implementing a pure Java 25 X11 stack using Project Panama FFM:

```
Minecraft Render Thread
        ↓
Headless Xvfb (:99)
        ↓
XComposite (manual redirection)
        ↓
XDamage (event-driven texture updates)
        ↓
MIT-SHM zero-copy memory mapping
        ↓
XTest raw input injection
```

**Key architectural differences:**

| Component | Legacy (Wayland/Rust) | ssXcraft (X11/Pure Java) |
|-----------|----------------------|-------------------------|
| Protocol | Wayland (synchronous) | X11 (async event-driven) |
| Compositor | Embedded Rust/Smithay | External Xvfb daemon |
| Memory | JNI buffer copies | MIT-SHM direct mapping |
| Input | Via Rust bridge | XTest direct injection |
| Frame sync | Blocking JNI calls | XDamage event-driven |

## Pipeline Components

### 1. Headless Xvfb Display Server
- Virtual framebuffer running on `:99`
- No GPU/hardware required
- Acts as the primary X11 display for all windows

### 2. XComposite Manual Redirection
- Windows redirected to off-screen buffers via `XCompositeRedirectWindow`
- Manual compositing allows precise control over update timing
- Eliminates compositor-to-client round-trip latency

### 3. XDamage Event-Driven Updates
- Subscribes to `XDamageNotify` events
- Textures updated only when regions change
- Async notification prevents unnecessary GPU transfers

### 4. MIT-SHM Zero-Copy Memory Mapping
- Shared memory segments via `shmget()`/`shmat()`
- Direct memory access through Project Panama FFM
- Eliminates buffer copy overhead between X server and client

### 5. XTest Input Injection
- Raw input events via `XTestFakeInput`
- Mouse position, keyboard presses injected directly
- Bypasses input method frameworks for minimal latency

## Platform Requirements

### Runtime Dependencies
- **OS**: Linux (X11 environment)
- **Java**: Java 25 (JDK 25+)
- **Mod Loader**: NeoForge 21.3.0.3+
- **Minecraft**: 1.21.3

### System Binaries
ssXcraft requires the following libxcb system libraries (typically installed by default on Linux):

- `libxcb1`, `libxcb-composite0`, `libxcb-damage0`
- `libxcb-shm0`, `libxcb-xtest0`, `libxcb-xfixes0`
- `xvfb` (virtual framebuffer)

On Debian/Ubuntu-based systems:
```bash
sudo apt install libxcb1 libxcb-composite0 libxcb-damage0 libxcb-shm0 libxcb-xtest0 libxcb-xfixes0 xvfb
```

## Compilation

### CI-Based Build Pipeline

Compilation is offloaded to an isolated GitHub Actions CI pipeline to keep the local workspace clean. The CI workflow:

1. Runs on `ubuntu-latest`
2. Sets up Java 25 (Temurin distribution)
3. Executes `./gradlew build --no-daemon`
4. Produces artifacts in `build/libs/`

**No local Gradle build required.** Simply push to the repository and download artifacts from CI.

### Local Development (Optional)

If you must build locally:
```bash
./gradlew build
```

Output: `build/libs/ssXcraft-${mod_version}.jar`

## Project Structure

```
src/main/java/com/supersonic/xserver/ssXcraft/
├── ssXcraft.java          # NeoForge mod entry point
└── X11Display.java       # X11 display bridge with FFM bindings
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `ssXcraft` | NeoForge mod initialization, event bus registration |
| `X11Display` | XCB connection management, FFM bindings for X11 extensions |

## Version Information

- **Minecraft**: 1.21.3
- **NeoForge**: 21.3.0.3
- **Java**: 25
- **Gradle**: Wrapper-based (NeoGradle 6.0.21)

## Disclaimer

This implementation is in active development. Use at your own risk.

---

*The entire project was written without the usage of any generative AI.* AI assisted PR is allowed just mark it.
