package modder.hub.editor;

import android.content.Context;
import android.view.*;
import android.widget.*;

/** Small host-themed navigation drawer without another UI library or activity. */
final class EditorDrawerLayout extends FrameLayout {
    final LinearLayout rows;
    private final View scrim;
    private final LinearLayout sheet;
    private boolean open;
    private float downX, downY;
    private final int edge, slop;
    private Runnable onOpen;
    EditorDrawerLayout(Context context, View content, int surface) {
        super(context);
        edge = dp(24); slop = ViewConfiguration.get(context).getScaledTouchSlop();
        addView(content, new LayoutParams(-1, -1));
        scrim = new View(context); scrim.setBackgroundColor(0x66000000);
        addView(scrim, new LayoutParams(-1, -1)); scrim.setOnClickListener(v -> close());
        sheet = new LinearLayout(context); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setBackgroundColor(surface); sheet.setElevation(dp(8));
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2)); sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        int width = Math.min(dp(320), (int)(getResources().getDisplayMetrics().widthPixels * .85));
        addView(sheet, new LayoutParams(width, -1, Gravity.LEFT));
        scrim.setVisibility(GONE); sheet.setVisibility(GONE);
    }
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    boolean isOpen() { return open; }
    void setOnOpen(Runnable callback) { onOpen = callback; }
    void setSurfaceColor(int color) { sheet.setBackgroundColor(color); }
    void open() { if (onOpen != null) onOpen.run(); open = true; scrim.setVisibility(VISIBLE); sheet.setVisibility(VISIBLE); sheet.bringToFront(); }
    void close() { open = false; scrim.setVisibility(GONE); sheet.setVisibility(GONE); }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (open) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && downX < edge && event.getX() - downX > slop * 2 && Math.abs(event.getY() - downY) < event.getX() - downX) { open(); return true; }
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return open || super.onTouchEvent(event); }
}
