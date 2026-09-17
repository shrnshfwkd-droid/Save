package com.asdfstudio.vietfloat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslationAccessibilityService extends AccessibilityService {
    private static final String ZALO = "com.zing.zalo";
    private static final long DEBOUNCE_MS = 350L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> running = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Boolean> seen = Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(220, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) { return size() > 200; }
    });
    private WindowManager wm;
    private View overlay;
    private TextView overlayText;
    private final Runnable scan = this::scanWindow;

    @Override protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.packageNames = new String[]{ZALO};
        info.notificationTimeout = 120;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null || !ZALO.contentEquals(event.getPackageName())) return;
        main.removeCallbacks(scan);
        main.postDelayed(scan, DEBOUNCE_MS);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        removeOverlay();
        super.onDestroy();
    }

    private void scanWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        root.recycle();
        int visited = 0;
        while (!q.isEmpty() && visited++ < 700) {
            AccessibilityNodeInfo node = q.removeFirst();
            try {
                if (node.isVisibleToUser()) {
                    CharSequence t = node.getText();
                    if (t != null) consider(t.toString());
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) q.add(child);
                }
            } finally { node.recycle(); }
        }
    }

    private void consider(String raw) {
        String text = raw.replace('\u00A0',' ').replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (text.isEmpty() || !VietnameseDetector.isLikelyVietnamese(text)) return;
        if (seen.put(text, Boolean.TRUE) != null || !running.add(text)) return;

        String apiKey = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.KEY_API, "");
        String model = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.KEY_MODEL, "gpt-5-mini");
        if (apiKey.isEmpty()) {
            running.remove(text);
            showOverlay("VietFloat: 앱에서 OpenAI API 키를 먼저 입력해 주세요.");
            return;
        }

        executor.submit(() -> {
            try {
                String translated = OpenAiTranslator.translate(apiKey, model, text).trim();
                if (!translated.isEmpty() && !"__SKIP__".equals(translated)) main.post(() -> showOverlay(text + "\n→ " + translated));
            } catch (Exception e) {
                main.post(() -> showOverlay("번역 오류: " + shortError(e.getMessage())));
            } finally { running.remove(text); }
        });
    }

    private void showOverlay(String text) {
        if (wm == null) return;
        if (overlay == null) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(16), dp(10), dp(16), dp(10));
            box.setBackgroundColor(0xEE111318);
            overlayText = new TextView(this);
            overlayText.setTextColor(Color.WHITE);
            overlayText.setTextSize(16);
            TextView close = new TextView(this);
            close.setText("닫기");
            close.setTextColor(0xFFB8C4FF);
            close.setGravity(Gravity.END);
            close.setPadding(0, dp(5), 0, 0);
            close.setOnClickListener(v -> removeOverlay());
            box.addView(overlayText, new LinearLayout.LayoutParams(-1, -2));
            box.addView(close, new LinearLayout.LayoutParams(-1, -2));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = dp(8);
            try { wm.addView(box, p); overlay = box; } catch (Exception ignored) { return; }
        }
        overlayText.setText(text);
        overlay.setVisibility(View.VISIBLE);
    }

    private void removeOverlay() {
        if (overlay != null && wm != null) try { wm.removeView(overlay); } catch (Exception ignored) {}
        overlay = null;
        overlayText = null;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String shortError(String s) {
        if (s == null || s.trim().isEmpty()) return "알 수 없는 오류";
        s = s.replaceAll("[\\r\\n]+", " ").trim();
        return s.length() > 160 ? s.substring(0,160) + "…" : s;
    }
}
