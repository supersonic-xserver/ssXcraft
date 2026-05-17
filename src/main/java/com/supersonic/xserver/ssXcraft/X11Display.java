package com.supersonic.xserver.ssXcraft;

import java.lang.foreign.*;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * X11 Display connection using Java 25 Panama FFM.
 * Manages connection to headless X server and zero-copy shared memory pipeline.
 */
public class X11Display implements AutoCloseable {
    
    // XCB constants
    private static final int XCB_CURRENT_TIME = 0;
    private static final int XCB_MOTION_NOTIFY = 6;
    private static final int XCB_BUTTON_PRESS = 4;
    private static final int XCB_BUTTON_RELEASE = 5;
    private static final int XCB_KEY_PRESS = 2;
    private static final int XCB_KEY_RELEASE = 3;
    private static final int XCB_DAMAGE_NOTIFY = 31;
    private static final int XCB_EXPOSE = 12;
    
    // Linker instance
    private static final Linker LINKER = Linker.nativeLinker();
    
    // Native library handles (loaded on demand)
    private SymbolLookup libxcb = null;
    private SymbolLookup libxcbComposite = null;
    private SymbolLookup libxcbDamage = null;
    private SymbolLookup libxcbShm = null;
    private SymbolLookup libxcbXtest = null;
    
    private MemorySegment connection;
    private Arena arena;
    private Thread eventThread;
    private volatile boolean running = false;
    
    // Tracked windows
    private final Map<Integer, X11Window> windows = new ConcurrentHashMap<>();
    
    // Event callback for window updates
    private WindowUpdateCallback updateCallback;
    
    // XCB function handles
    private MethodHandle xcb_connect;
    private MethodHandle xcb_disconnect;
    private MethodHandle xcb_generate_id;
    private MethodHandle xcb_connection_has_error;
    private MethodHandle xcb_wait_for_event;
    private MethodHandle xcb_poll_for_event;
    private MethodHandle xcb_flush;
    private MethodHandle xcb_get_setup;
    private MethodHandle xcb_setup_roots_iterator;
    private MethodHandle xcb_screen_next;
    
    // XCB Composite handles
    private MethodHandle xcb_composite_redirect_subwindows;
    private MethodHandle xcb_composite_name_window_pixmap;
    
    // XCB Damage handles
    private MethodHandle xcb_damage_query_version;
    private MethodHandle xcb_damage_create;
    private MethodHandle xcb_damage_destroy;
    
    // XCB SHM handles
    private MethodHandle xcb_shm_get_image;
    private MethodHandle xcb_shm_attach;
    private MethodHandle xcb_shm_detach;
    private MethodHandle xcb_shm_create_segment;
    
    // XCB XTest handles
    private MethodHandle xcb_test_fake_input;
    
    public interface WindowUpdateCallback {
        void onWindowUpdate(int windowId, byte[] pixels, int width, int height);
    }
    
    public X11Display() {
        this.arena = Arena.ofConfined();
    }
    
    /**
     * Load all required XCB libraries and resolve function pointers.
     */
    private void loadLibraries() throws Throwable {
        // Load libraries dynamically
        libxcb = SymbolLookup.libraryLookup("libxcb.so.1", arena);
        libxcbComposite = SymbolLookup.libraryLookup("libxcb-composite.so.0", arena);
        libxcbDamage = SymbolLookup.libraryLookup("libxcb-damage.so.1", arena);
        libxcbShm = SymbolLookup.libraryLookup("libxcb-shm.so.1", arena);
        libxcbXtest = SymbolLookup.libraryLookup("libxcb-xtest.so.1", arena);
        
        // Resolve core XCB functions
        xcb_connect = LINKER.downcallHandle(
            libxcb.find("xcb_connect").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        xcb_disconnect = LINKER.downcallHandle(
            libxcb.find("xcb_disconnect").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        
        xcb_generate_id = LINKER.downcallHandle(
            libxcb.find("xcb_generate_id").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        xcb_connection_has_error = LINKER.downcallHandle(
            libxcb.find("xcb_connection_has_error").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        xcb_wait_for_event = LINKER.downcallHandle(
            libxcb.find("xcb_wait_for_event").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        xcb_poll_for_event = LINKER.downcallHandle(
            libxcb.find("xcb_poll_for_event").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        xcb_flush = LINKER.downcallHandle(
            libxcb.find("xcb_flush").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        xcb_get_setup = LINKER.downcallHandle(
            libxcb.find("xcb_get_setup").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        xcb_setup_roots_iterator = LINKER.downcallHandle(
            libxcb.find("xcb_setup_roots_iterator").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        
        xcb_screen_next = LINKER.downcallHandle(
            libxcb.find("xcb_screen_next").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        
        // XCB Composite functions
        xcb_composite_redirect_subwindows = LINKER.downcallHandle(
            libxcbComposite.find("xcb_composite_redirect_subwindows").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        xcb_composite_name_window_pixmap = LINKER.downcallHandle(
            libxcbComposite.find("xcb_composite_name_window_pixmap").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        // XCB Damage functions
        xcb_damage_query_version = LINKER.downcallHandle(
            libxcbDamage.find("xcb_damage_query_version").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        xcb_damage_create = LINKER.downcallHandle(
            libxcbDamage.find("xcb_damage_create").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        xcb_damage_destroy = LINKER.downcallHandle(
            libxcbDamage.find("xcb_damage_destroy").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        // XCB SHM functions
        xcb_shm_get_image = LINKER.downcallHandle(
            libxcbShm.find("xcb_shm_get_image").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, 
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        xcb_shm_attach = LINKER.downcallHandle(
            libxcbShm.find("xcb_shm_attach").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        xcb_shm_detach = LINKER.downcallHandle(
            libxcbShm.find("xcb_shm_detach").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        xcb_shm_create_segment = LINKER.downcallHandle(
            libxcbShm.find("xcb_shm_create_segment").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        // XCB XTest functions
        xcb_test_fake_input = LINKER.downcallHandle(
            libxcbXtest.find("xcb_test_fake_input").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, 
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        ssXcraft.LOGGER.info("XCB libraries loaded successfully");
    }
    
    /**
     * Connect to X server.
     * @param display Display string (e.g., ":99" for headless)
     */
    public void connect(String display) {
        if (display == null || display.isEmpty()) {
            display = ":99"; // Default headless
        }
        
        try {
            // Load libraries first
            loadLibraries();
            
            // Create display name segment
            MemorySegment displayPtr = arena.allocateFrom(display + "\0");
            MemorySegment screenPtr = arena.allocate(ValueLayout.ADDRESS);
            
            // xcb_connect
            this.connection = (MemorySegment) xcb_connect.invokeExact(displayPtr, screenPtr);
            
            if (connection.address() == 0) {
                throw new RuntimeException("Failed to connect to X server: " + display);
            }
            
            // Check for connection errors
            int error = getConnectionHasError();
            if (error != 0) {
                throw new RuntimeException("X connection error: " + error);
            }
            
            // Initialize extensions
            initializeExtensions();
            
            // Start event loop
            startEventLoop();
            
            ssXcraft.LOGGER.info("Connected to X server: " + display);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to connect to X server", e);
        }
    }
    
    private void initializeExtensions() throws Throwable {
        // Query XCB Composite version
        try {
            MemorySegment major = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment minor = arena.allocate(ValueLayout.JAVA_INT);
            major.set(ValueLayout.JAVA_INT, 0, 0);
            minor.set(ValueLayout.JAVA_INT, 0, 0);
            xcb_damage_query_version.invokeExact(connection, major, minor);
            ssXcraft.LOGGER.info("XCB Damage extension initialized");
        } catch (Throwable e) {
            ssXcraft.LOGGER.warn("XCB Damage extension not available", e);
        }
    }
    
    private int getConnectionHasError() {
        try {
            return (int) xcb_connection_has_error.invokeExact(connection);
        } catch (Throwable e) {
            return 1;
        }
    }
    
    private void startEventLoop() {
        running = true;
        eventThread = new Thread(() -> {
            while (running) {
                try {
                    processEvents();
                    Thread.sleep(1); // ~1000fps event processing
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    ssXcraft.LOGGER.error("Event loop error", e);
                }
            }
        }, "X11EventLoop");
        eventThread.start();
    }
    
    private void processEvents() {
        if (connection == null || connection.address() == 0) return;
        
        try {
            // Try non-blocking poll first
            MemorySegment event = (MemorySegment) xcb_poll_for_event.invokeExact(connection);
            
            if (event != null && event.address() != 0) {
                handleEvent(event);
            }
            
            // Flush the connection
            xcb_flush.invokeExact(connection);
            
        } catch (Throwable e) {
            // Event loop continues on error
        }
    }
    
    private void handleEvent(MemorySegment event) {
        try {
            // Read event type (first byte)
            byte eventType = event.get(ValueLayout.JAVA_BYTE, 0);
            
            // Handle damage notify events
            if (eventType == XCB_DAMAGE_NOTIFY) {
                handleDamageNotify(event);
            } else if (eventType == XCB_EXPOSE) {
                handleExpose(event);
            }
        } catch (Exception e) {
            ssXcraft.LOGGER.error("Error handling X event", e);
        }
    }
    
    private void handleDamageNotify(MemorySegment event) {
        // DamageNotify event structure:
        // response_type (1) + pad0 (1) + sequence (2) + drawable (4) + damage (4) + level (1) + more (1) + pad0 (2) + timestamp (4)
        int drawable = event.get(ValueLayout.JAVA_INT, 8);
        
        X11Window window = windows.get(drawable);
        if (window != null && updateCallback != null) {
            // Notify about damage - actual pixel fetch happens in render loop
            updateCallback.onWindowUpdate(drawable, null, window.getWidth(), window.getHeight());
        }
    }
    
    private void handleExpose(MemorySegment event) {
        // Expose event structure:
        // response_type (1) + pad0 (1) + sequence (2) + window (4) + x (2) + y (2) + width (2) + height (2) + count (2) + pad0 (2)
        int window = event.get(ValueLayout.JAVA_INT, 4);
        
        X11Window xwin = windows.get(window);
        if (xwin != null && updateCallback != null) {
            updateCallback.onWindowUpdate(window, null, xwin.getWidth(), xwin.getHeight());
        }
    }
    
    /**
     * Generate new X window ID.
     */
    public int generateId() {
        try {
            return (int) xcb_generate_id.invokeExact(connection);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to generate XID", e);
        }
    }
    
    /**
     * Create shared memory segment for zero-copy image transfer.
     */
    public long createShmSegment(int size) {
        try {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment shmid = arena.allocate(ValueLayout.JAVA_INT);
            shmid.set(ValueLayout.JAVA_INT, 0, 0);
            
            int result = (int) xcb_shm_create_segment.invokeExact(connection, seg, size, 1);
            if (result == 0) {
                return seg.get(ValueLayout.JAVA_LONG, 0);
            }
            return 0;
        } catch (Exception e) {
            ssXcraft.LOGGER.error("Failed to create SHM segment", e);
            return 0;
        }
    }
    
    /**
     * Attach window for damage tracking using XDamage.
     */
    public void trackWindow(int windowId, int width, int height) {
        try {
            int damageId = generateId();
            
            // Create damage object for the window
            xcb_damage_create.invokeExact(connection, damageId, windowId, 1);
            
            X11Window window = new X11Window(windowId, width, height);
            window.setDamageId(damageId);
            windows.put(windowId, window);
            
            ssXcraft.LOGGER.info("Tracking window: " + windowId + " with damage ID: " + damageId);
        } catch (Throwable e) {
            ssXcraft.LOGGER.error("Failed to track window", e);
        }
    }
    
    /**
     * Get window pixmap via XComposite for rendering.
     */
    public int getWindowPixmap(int windowId) {
        try {
            int pixmapId = generateId();
            xcb_composite_name_window_pixmap.invokeExact(connection, windowId, pixmapId);
            return pixmapId;
        } catch (Throwable e) {
            return 0;
        }
    }
    
    /**
     * Inject mouse move event into X server.
     */
    public void injectMouseMove(int x, int y) {
        try {
            xcb_test_fake_input.invokeExact(connection, 
                (byte) XCB_MOTION_NOTIFY, 
                (int) 0,  // detail (pointer)
                (int) XCB_CURRENT_TIME,
                x, y, 
                (int) 0
            );
        } catch (Throwable e) {
            ssXcraft.LOGGER.debug("XTest mouse move failed", e);
        }
    }
    
    /**
     * Inject mouse button event.
     */
    public void injectMouseButton(int button, boolean pressed) {
        try {
            byte type = pressed ? (byte) XCB_BUTTON_PRESS : (byte) XCB_BUTTON_RELEASE;
            xcb_test_fake_input.invokeExact(connection, type, button, XCB_CURRENT_TIME, 0, 0, 0);
        } catch (Throwable e) {
            ssXcraft.LOGGER.debug("XTest mouse button failed", e);
        }
    }
    
    /**
     * Inject key press/release event.
     */
    public void injectKeyEvent(int keycode, boolean pressed) {
        try {
            byte type = pressed ? (byte) XCB_KEY_PRESS : (byte) XCB_KEY_RELEASE;
            xcb_test_fake_input.invokeExact(connection, type, keycode, XCB_CURRENT_TIME, 0, 0, 0);
        } catch (Throwable e) {
            ssXcraft.LOGGER.debug("XTest key event failed", e);
        }
    }
    
    public void setWindowUpdateCallback(WindowUpdateCallback callback) {
        this.updateCallback = callback;
    }
    
    public Map<Integer, X11Window> getWindows() {
        return windows;
    }
    
    /**
     * Get the raw X connection for advanced operations.
     */
    public MemorySegment getConnection() {
        return connection;
    }
    
    @Override
    public void close() {
        running = false;
        if (eventThread != null) {
            eventThread.interrupt();
            try {
                eventThread.join(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (connection != null && connection.address() != 0) {
            try {
                xcb_disconnect.invokeExact(connection);
            } catch (Throwable e) {
                // Ignore
            }
        }
        if (arena != null) {
            arena.close();
        }
        ssXcraft.LOGGER.info("X11Display closed");
    }
    
    /**
     * X11 Window wrapper with tracking info.
     */
    public static class X11Window {
        private final int windowId;
        private int width, height;
        private int damageId;
        private int pixmapId;
        private byte[] latestPixels;
        
        public X11Window(int windowId, int width, int height) {
            this.windowId = windowId;
            this.width = width;
            this.height = height;
        }
        
        public int getWindowId() { return windowId; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getDamageId() { return damageId; }
        public void setDamageId(int id) { this.damageId = id; }
        public int getPixmapId() { return pixmapId; }
        public void setPixmapId(int id) { this.pixmapId = id; }
        public byte[] getLatestPixels() { return latestPixels; }
        public void setLatestPixels(byte[] pixels) { this.latestPixels = pixels; }
    }
}