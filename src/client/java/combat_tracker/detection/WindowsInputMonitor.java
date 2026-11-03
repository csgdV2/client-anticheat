package combat_tracker.detection;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinUser;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public final class WindowsInputMonitor {
    public static final int MOUSE = 0, KEYBOARD = 1, MOVEMENT = 2, WHEEL = 3;
    private final AtomicLongArray counts = new AtomicLongArray(4);
    private final AtomicLongArray lastEventEpoch = new AtomicLongArray(4);
    private final AtomicInteger threadId = new AtomicInteger();
    private volatile long gameWindow;
    private volatile long activeUntilNano;
    private volatile boolean running;
    private volatile String status = "Not started";
    private Thread thread;
    private WinUser.LowLevelMouseProc mouseCallback;
    private WinUser.LowLevelKeyboardProc keyboardCallback;

    public synchronized void start() {
        if (thread != null) return;
        if (!Platform.isWindows()) {
            status = "Unavailable on this OS; behavioral checks remain available";
            return;
        }
        running = true;
        thread = new Thread(this::runHooks, "client-anticheat-windows-input");
        thread.setDaemon(true);
        thread.start();
    }

    public void setGameplayWindow(long hwnd, boolean active) {
        gameWindow = hwnd;
        activeUntilNano = active ? System.nanoTime() + 100_000_000L : 0;
    }

    public void stop() {
        running = false;
        activeUntilNano = 0;
        int id = threadId.get();
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, null, null);
    }

    public long count(int kind) {
        return counts.get(kind);
    }

    public long lastEventEpochMs(int kind) {
        return lastEventEpoch.get(kind);
    }

    public String status() {
        return status;
    }

    private boolean focused(User32 user) {
        long hwnd = gameWindow;
        if (!running || hwnd == 0 || System.nanoTime() >= activeUntilNano) return false;
        HWND foreground = user.GetForegroundWindow();
        return foreground != null && Pointer.nativeValue(foreground.getPointer()) == hwnd;
    }

    private void runHooks() {
        WinUser.HHOOK mouseHook = null;
        WinUser.HHOOK keyboardHook = null;
        try {
            User32 user = User32.INSTANCE;
            threadId.set(Kernel32.INSTANCE.GetCurrentThreadId());
            WinUser.MSG message = new WinUser.MSG();
            user.PeekMessage(message, null, 0, 0, 0);
            mouseCallback = (code, type, data) -> {
                try {
                    if (code >= 0 && (data.flags & 1) != 0 && focused(user)) {
                        int kind = mouseKind(type.intValue());
                        if (kind >= 0) increment(kind);
                    }
                } catch (RuntimeException unavailable) {
                    activeUntilNano = 0;
                    status = "Windows mouse callback failed; observations may be incomplete";
                }
                return user.CallNextHookEx(null, code, type, new LPARAM(Pointer.nativeValue(data.getPointer())));
            };
            keyboardCallback = (code, type, data) -> {
                try {
                    if (code >= 0 && (data.flags & 0x10) != 0
                        && (type.intValue() == 0x100 || type.intValue() == 0x104) && focused(user)) {
                        increment(KEYBOARD);
                    }
                } catch (RuntimeException unavailable) {
                    activeUntilNano = 0;
                    status = "Windows keyboard callback failed; observations may be incomplete";
                }
                return user.CallNextHookEx(null, code, type, new LPARAM(Pointer.nativeValue(data.getPointer())));
            };
            mouseHook = user.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseCallback,
                    Kernel32.INSTANCE.GetModuleHandle(null), 0);
            keyboardHook = user.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardCallback,
                    Kernel32.INSTANCE.GetModuleHandle(null), 0);
            if (mouseHook == null || keyboardHook == null) {
                status = "Windows input hooks unavailable; error " + Kernel32.INSTANCE.GetLastError();
                return;
            }
            status = "Windows input hooks installed; coverage is not guaranteed";
            while (running) {
                int result = user.GetMessage(message, null, 0, 0);
                if (result <= 0) {
                    if (result < 0) status = "Windows input message loop failed";
                    break;
                }
                user.TranslateMessage(message);
                user.DispatchMessage(message);
            }
        } catch (LinkageError | RuntimeException unavailable) {
            status = "Windows input monitoring unavailable: " + unavailable.getClass().getSimpleName();
        } finally {
            if (mouseHook != null) User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
            if (keyboardHook != null) User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            threadId.set(0);
            mouseCallback = null;
            keyboardCallback = null;
            running = false;
            if (status.startsWith("Windows input hooks installed")) status = "Windows input monitoring stopped";
        }
    }

    private void increment(int kind) {
        lastEventEpoch.set(kind, System.currentTimeMillis());
        counts.updateAndGet(kind, value -> Math.min(1_000_000_000L, value + 1));
    }

    static int mouseKind(int message) {
        return switch (message) {
            case 0x201, 0x204, 0x207, 0x20b -> MOUSE;
            case 0x200 -> MOVEMENT;
            case 0x20a, 0x20e -> WHEEL;
            default -> -1;
        };
    }
}
