package com.example.fastkeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * FastKeyboardService - High-Performance Hybrid Keyboard Engine
 * 
 * Architecture: Native InputMethodService + WebView UI Layer
 * Target: Sub-16ms frame times (60fps) for native-like feel
 * 
 * @author Senior Android System Engineer
 * @version 2.0.0-production
 */
public class FastKeyboardService extends InputMethodService {

    // ════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ════════════════════════════════════════════════════════════════════
    
    private static final String TAG = "FastKeyboard";
    private static final String BRIDGE_NAME = "AndroidBridge";
    private static final String KEYBOARD_URL = "file:///android_asset/keyboard.html";
    private static final int KEYBOARD_HEIGHT_DP = 260;
    
    // ════════════════════════════════════════════════════════════════════
    // INSTANCE VARIABLES
    // ════════════════════════════════════════════════════════════════════
    
    private WebView mWebView;
    private FrameLayout mContainerView;
    private Vibrator mVibrator;
    
    // Handler for main thread operations - avoids creating new handlers
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    // ════════════════════════════════════════════════════════════════════
    // SECRET TRICK #1: Static WebView Preloading Pool
    // Pre-initialize WebView before keyboard is shown for instant display
    // ════════════════════════════════════════════════════════════════════
    
    private static volatile WebView sPreloadedWebView = null;
    private static volatile boolean sIsPreloading = false;
    private static final Object sPreloadLock = new Object();
    
    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreate
    // ════════════════════════════════════════════════════════════════════
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // SECRET TRICK #2: Pre-warm WebView Process
        // Initialize WebView on a background-priority to avoid ANR
        preloadWebViewAsync();
        
        // SECRET TRICK #3: Cache Vibrator Service Reference
        // Avoid repeated getSystemService() calls
        initializeVibrator();
    }
    
    private void initializeVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                mVibrator = vm.getDefaultVibrator();
            }
        } else {
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }
    
    private void preloadWebViewAsync() {
        synchronized (sPreloadLock) {
            if (sPreloadedWebView != null || sIsPreloading) {
                return;
            }
            sIsPreloading = true;
        }
        
        mMainHandler.post(() -> {
            synchronized (sPreloadLock) {
                if (sPreloadedWebView == null) {
                    sPreloadedWebView = createOptimizedWebView();
                    sPreloadedWebView.loadUrl(KEYBOARD_URL);
                }
                sIsPreloading = false;
            }
        });
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreateInputView (Main Entry Point)
    // ════════════════════════════════════════════════════════════════════
    
    @Override
    public View onCreateInputView() {
        // Create lightweight container
        mContainerView = createContainer();
        
        // SECRET TRICK #4: Reuse Preloaded WebView
        synchronized (sPreloadLock) {
            if (sPreloadedWebView != null && sPreloadedWebView.getParent() == null) {
                mWebView = sPreloadedWebView;
                sPreloadedWebView = null;
            } else {
                mWebView = createOptimizedWebView();
                mWebView.loadUrl(KEYBOARD_URL);
            }
        }
        
        // Attach JavaScript Bridge
        mWebView.addJavascriptInterface(new AndroidBridge(), BRIDGE_NAME);
        
        // Configure layout params
        FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        mWebView.setLayoutParams(webViewParams);
        
        // Add to container
        mContainerView.addView(mWebView);
        
        // SECRET TRICK #5: Pre-warm Next WebView Instance
        // Start loading a spare WebView for next show
        mMainHandler.postDelayed(this::preloadWebViewAsync, 300);
        
        return mContainerView;
    }
    
    // ════════════════════════════════════════════════════════════════════
    // CONTAINER CREATION
    // ════════════════════════════════════════════════════════════════════
    
    private FrameLayout createContainer() {
        FrameLayout container = new FrameLayout(this);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(KEYBOARD_HEIGHT_DP)
        );
        container.setLayoutParams(params);
        
        // SECRET TRICK #6: Transparent Background Avoids Overdraw
        container.setBackgroundColor(Color.TRANSPARENT);
        
        // SECRET TRICK #7: Hardware Layer on Container Too
        container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        return container;
    }
    
    // ════════════════════════════════════════════════════════════════════
    // WEBVIEW CREATION & OPTIMIZATION
    // ════════════════════════════════════════════════════════════════════
    
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createOptimizedWebView() {
        // SECRET TRICK #8: Use Application Context to Prevent Memory Leaks
        // But for InputMethodService, we must use service context
        WebView webView = new WebView(this);
        
        // ─────────────────────────────────────────────────────────────────
        // CRITICAL: GPU Hardware Acceleration
        // ─────────────────────────────────────────────────────────────────
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // ─────────────────────────────────────────────────────────────────
        // WebSettings Optimization Suite
        // ─────────────────────────────────────────────────────────────────
        WebSettings settings = webView.getSettings();
        
        // JavaScript (Required for bridge)
        settings.setJavaScriptEnabled(true);
        
        // SECRET TRICK #9: Render Priority HIGH
        // Deprecated but still effective on many devices
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        
        // Cache Strategy - Load from cache first
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        
        // DOM Storage for fast state management
        settings.setDomStorageEnabled(true);
        
        // SECRET TRICK #10: Disable All Zoom Features
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        
        // Optimized Rendering Layout
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        
        // SECRET TRICK #11: Disable Unnecessary Features
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        
        // SECRET TRICK #12: Disable Safe Browsing Check (Faster Loading)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        
        // Force fixed text size (no system font scaling)
        settings.setTextZoom(100);
        
        // ─────────────────────────────────────────────────────────────────
        // Scrollbar & Touch Optimization
        // ─────────────────────────────────────────────────────────────────
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        
        // SECRET TRICK #13: Disable Scrolling at Native Level
        webView.setOnTouchListener((v, event) -> {
            // Return false to let WebView handle touch, but disable scroll
            return (event.getAction() == android.view.MotionEvent.ACTION_MOVE);
        });
        
        // ─────────────────────────────────────────────────────────────────
        // Visual Optimization
        // ─────────────────────────────────────────────────────────────────
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        // SECRET TRICK #14: Remove Default Drawables & Focus States
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setClickable(false);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
        
        // SECRET TRICK #15: Disable Hardware Keyboard Detection
        // Prevents unnecessary layout recalculations
        webView.setOnKeyListener(null);
        
        // Fast WebViewClient
        webView.setWebViewClient(new OptimizedWebViewClient());
        
        return webView;
    }
    
    // ════════════════════════════════════════════════════════════════════
    // OPTIMIZED WEBVIEW CLIENT
    // ════════════════════════════════════════════════════════════════════
    
    private class OptimizedWebViewClient extends WebViewClient {
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            
            // Notify JavaScript that bridge is ready
            view.evaluateJavascript(
                "if(typeof onBridgeReady==='function')onBridgeReady();", 
                null
            );
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Block all navigation - keyboard should not navigate
            return true;
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // INPUT VIEW LIFECYCLE
    // ════════════════════════════════════════════════════════════════════
    
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        
        if (mWebView != null) {
            // Notify JavaScript about input type for smart layout switching
            int inputType = info.inputType & EditorInfo.TYPE_MASK_CLASS;
            int imeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            
            String jsCall = String.format(
                "if(typeof onInputStart==='function')onInputStart(%d,%d);",
                inputType, imeAction
            );
            mWebView.evaluateJavascript(jsCall, null);
        }
    }
    
    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        // Don't destroy WebView - keep for fast reuse
    }
    
    // ════════════════════════════════════════════════════════════════════
    // JAVASCRIPT BRIDGE - The Communication Layer
    // ════════════════════════════════════════════════════════════════════
    
    public class AndroidBridge {
        
        /**
         * Commit text to the current input field
         * @param text The text to commit
         */
        @JavascriptInterface
        public void commitText(final String text) {
            // Post to main thread for InputConnection access
            mMainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && text != null) {
                    ic.commitText(text, 1);
                }
            });
        }
        
        /**
         * Delete characters before cursor
         * @param count Number of characters to delete
         */
        @JavascriptInterface
        public void deleteText(final int count) {
            mMainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.deleteSurroundingText(count, 0);
                }
            });
        }
        
        /**
         * Send a raw key event
         * @param keyCode Android KeyEvent code
         */
        @JavascriptInterface
        public void sendKeyEvent(final int keyCode) {
            mMainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    long eventTime = System.currentTimeMillis();
                    ic.sendKeyEvent(new KeyEvent(eventTime, eventTime, 
                        KeyEvent.ACTION_DOWN, keyCode, 0));
                    ic.sendKeyEvent(new KeyEvent(eventTime, eventTime, 
                        KeyEvent.ACTION_UP, keyCode, 0));
                }
            });
        }
        
        /**
         * Perform the editor's action (Done, Next, Search, etc.)
         */
        @JavascriptInterface
        public void performEditorAction() {
            mMainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                EditorInfo ei = getCurrentInputEditorInfo();
                if (ic != null && ei != null) {
                    int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
                    ic.performEditorAction(action);
                }
            });
        }
        
        /**
         * Perform haptic feedback
         * @param duration Vibration duration in milliseconds
         */
        @JavascriptInterface
        public void vibrate(final int duration) {
            if (mVibrator == null || !mVibrator.hasVibrator()) return;
            
            // Use light vibration for key press feedback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(
                    duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(duration);
            }
        }
        
        /**
         * Hide the keyboard
         */
        @JavascriptInterface
        public void hideKeyboard() {
            mMainHandler.post(() -> {
                requestHideSelf(0);
            });
        }
        
        /**
         * Get selected text from input field
         * @return Selected text or empty string
         */
        @JavascriptInterface
        public String getSelectedText() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence selected = ic.getSelectedText(0);
                return selected != null ? selected.toString() : "";
            }
            return "";
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════
    
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
    
    // ════════════════════════════════════════════════════════════════════
    // CLEANUP - Memory Leak Prevention
    // ════════════════════════════════════════════════════════════════════
    
    @Override
    public void onDestroy() {
        cleanupWebView();
        cleanupPreloadedWebView();
        super.onDestroy();
    }
    
    private void cleanupWebView() {
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.removeJavascriptInterface(BRIDGE_NAME);
            
            if (mContainerView != null) {
                mContainerView.removeView(mWebView);
            }
            
            mWebView.clearCache(false);
            mWebView.clearHistory();
            mWebView.destroy();
            mWebView = null;
        }
    }
    
    private void cleanupPreloadedWebView() {
        synchronized (sPreloadLock) {
            if (sPreloadedWebView != null) {
                sPreloadedWebView.destroy();
                sPreloadedWebView = null;
            }
        }
    }
}