# ssXcraft Refactoring Documentation

## Overview
Refactoring from Wayland/Rust to X11/Pure Java 25 with Panama FFM.

## Phase Status

### Phase 1: Build System Preparation ✅
- [x] Remove Rust/Cargo dependencies from native/ directory
- [x] Update build.gradle to remove native library loading
- [x] Remove old evvie/waylandcraft Java files

### Phase 2: XCB Infrastructure (Pure Java 25) ✅
- [x] Create XCB bindings using Panama FFM
- [x] Implement X11Display class with FFM bindings
- [x] Implement XCB extensions (Composite, Damage, SHM, XTest)
- [x] Implement basic event loop

### Phase 3: Zero-Copy Display Pipeline
- [ ] Create bridge integration
- [ ] Implement shared memory buffer mapping
- [ ] Integrate with WindowFramebuffer rendering

### Phase 4: Input Forwarding Layer ✅
- [x] Map mouse/keyboard to XCB fake input (XTest)
- [x] Adapt input handling

### Phase 5: Cleanup
- [x] Rename to ssXcraft package
- [x] Update mod metadata (fabric.mod.json)
- [x] Create ssXcraft.mixins.json
- [ ] Remove native/ directory (Rust code)
- [x] Update gradle.properties

### Phase 6: Build Verification
- [ ] Run ./gradlew build to verify compilation

---

## Architecture Notes

### Target: Java 25 + XCB Pipeline
- Headless Xvfb server (DISPLAY=:99)
- XComposite for window redirection
- XDamage for async pixel updates
- MIT-SHM for zero-copy shared memory
- XTest for input injection

### Key Classes Created
- com.supersonic.xserver.ssXcraft.SsXcraft.java (main mod entry)
- com.supersonic.xserver.ssXcraft.X11Display.java (display bridge with FFM)

### Package Structure
- Old: dev.evvie.waylandcraft.* (removed)
- New: com.supersonic.xserver.ssXcraft.*
