package modder.hub.editor;

/** Host hook without a dependency from the reusable editor module back into the app. */
public final class EditorTaskBridge {
    public interface Listener {
        String begin(String title, String detail);
        void finish(String id, boolean success, String message);
    }
    public static volatile Listener listener;
    private EditorTaskBridge() {}
    public static String begin(String title, String detail) {
        Listener current = listener;
        return current == null ? null : current.begin(title, detail);
    }
    public static void finish(String id, boolean success, String message) {
        Listener current = listener;
        if (current != null && id != null) current.finish(id, success, message == null ? "Dateioperation fehlgeschlagen" : message);
    }
}
