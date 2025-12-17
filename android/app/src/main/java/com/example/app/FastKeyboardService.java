package com.example.app;

import android.content.Context;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * FastKeyboardService - ස්ථාවර හයිබ්‍රිඩ් කීබෝඩ්
 * 
 * මෙම implementation එක Android 10-15 සඳහා crash-proof ලෙස නිර්මාණය කර ඇත.
 * Capacitor projects සමඟ ගැටුම් නොවන ලෙස WebView isolated කර ඇත.
 * 
 * @version 3.0.0-stable
 */
public class FastKeyboardService extends InputMethodService {

    // ════════════════════════════════════════════════════════════════════
    // නියතයන් (Constants)
    // ════════════════════════════════════════════════════════════════════
    
    private static final String TAG = "FastKeyboard";
    private static final String BRIDGE_NAME = "AndroidBridge";
    private static final String KEYBOARD_URL = "file:///android_asset/keyboard.html";
    private static final int KEYBOARD_HEIGHT_DP = 260;

    // ════════════════════════════════════════════════════════════════════
    // Instance Variables
    // ════════════════════════════════════════════════════════════════════
    
    private FrameLayout mRootContainer;
    private WebView mWebView;
    private TextView mFallbackView;
    
    private boolean mIsWebViewReady = false;
    private boolean mWebViewFailed = false;
    
    private Handler mHandler;
    private Vibrator mVibrator;

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreate - Service එක ආරම්භ වන විට
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() - Service ආරම්භ විය");
        
        // Main thread handler එක initialize කරනවා
        mHandler = new Handler(Looper.getMainLooper());
        
        // Vibrator service එක ආරක්ෂිතව ලබාගන්නවා
        initializeVibrator();
    }
    
    /**
     * Vibrator service එක ආරක්ෂිතව initialize කරනවා
     * Android 12+ සඳහා VibratorManager භාවිතා කරනවා
     */
    private void initializeVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    mVibrator = vm.getDefaultVibrator();
                }
            } else {
                mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            Log.d(TAG, "Vibrator initialized successfully");
        } catch (Exception e) {
            Log.w(TAG, "Vibrator service ලබාගැනීමට නොහැකි විය", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreateInputView - කීබෝඩ් UI සාදන ප්‍රධාන method එක
    // ════════════════════════════════════════════════════════════════════

    @Override
    public View onCreateInputView() {
        Log.d(TAG, "onCreateInputView() - කීබෝඩ් UI සාදමින්");
        
        try {
            // පියවර 1: Root container එක සාදනවා (ආරක්ෂිත context එකෙන්)
            mRootContainer = createSafeContainer();
            
            // පියවර 2: WebView සෑදීමට උත්සාහ කරනවා
            if (!mWebViewFailed) {
                try {
                    mWebView = createSafeWebView();
                    
                    if (mWebView != null) {
                        // WebView එක container එකට add කරනවා
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        );
                        mRootContainer.addView(mWebView, params);
                        
                        // keyboard.html load කරනවා
                        mWebView.loadUrl(KEYBOARD_URL);
                        Log.d(TAG, "WebView සාදා HTML load කෙරිණි: " + KEYBOARD_URL);
                    } else {
                        throw new RuntimeException("WebView creation null ලැබුණි");
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "WebView සෑදීම අසාර්ථක විය, fallback භාවිතා කරනවා", e);
                    mWebViewFailed = true;
                    addFallbackView();
                }
            } else {
                addFallbackView();
            }
            
            return mRootContainer;
            
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: onCreateInputView සම්පූර්ණයෙන් අසාර්ථක විය", e);
            // Crash වළක්වන්න minimal safe view එකක් return කරනවා
            return createEmergencyView();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ආරක්ෂිත Container සෑදීම
    // ════════════════════════════════════════════════════════════════════

    private FrameLayout createSafeContainer() {
        // ContextThemeWrapper භාවිතයෙන් නිසි theme එකක් ලබාදෙනවා
        // මෙය WebView crash වීම වළක්වයි
        Context themedContext = createThemedContext();
        
        FrameLayout container = new FrameLayout(themedContext);
        
        // Fixed height එකක් සහිත layout params සකසනවා
        int heightPx = dpToPx(KEYBOARD_HEIGHT_DP);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            heightPx
        );
        container.setLayoutParams(params);
        
        // පසුබිම් වර්ණය සකසනවා (transparent keyboard වැළැක්වීමට)
        container.setBackgroundColor(Color.parseColor("#1a1a2e"));
        
        // Software rendering භාවිතයෙන් GPU driver bugs වළක්වනවා
        container.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        
        Log.d(TAG, "Container සාදන ලදී - height: " + heightPx + "px");
        
        return container;
    }

    // ════════════════════════════════════════════════════════════════════
    // Themed Context සෑදීම - Crash Fix ප්‍රධාන තාක්ෂණය
    // ════════════════════════════════════════════════════════════════════

    private Context createThemedContext() {
        try {
            // InputMethodService එකට WebView වලට අවශ්‍ය theme attributes නැහැ
            // ContextThemeWrapper භාවිතයෙන් ඒවා ලබාදෙනවා
            return new ContextThemeWrapper(
                this, 
                android.R.style.Theme_DeviceDefault_NoActionBar
            );
        } catch (Exception e) {
            Log.w(TAG, "ContextThemeWrapper අසාර්ථක විය, service context භාවිතා කරනවා", e);
            return this;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ආරක්ෂිත WebView සෑදීම
    // ════════════════════════════════════════════════════════════════════

    private WebView createSafeWebView() {
        Context themedContext = createThemedContext();
        
        // WebView class එක device එකේ තිබේදැයි පරීක්ෂා කරනවා
        try {
            Class.forName("android.webkit.WebView");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "WebView class මෙම device එකේ නැත!");
            return null;
        }
        
        WebView webView;
        
        try {
            webView = new WebView(themedContext);
            Log.d(TAG, "WebView themed context එකෙන් සාදන ලදී");
        } catch (Exception e) {
            Log.e(TAG, "WebView constructor අසාර්ථක විය", e);
            
            // Fallback: Application context එකෙන් උත්සාහ කරනවා
            try {
                webView = new WebView(getApplicationContext());
                Log.d(TAG, "WebView application context එකෙන් සාදන ලදී");
            } catch (Exception e2) {
                Log.e(TAG, "WebView app context එකෙනුත් අසාර්ථක විය", e2);
                return null;
            }
        }
        
        // WebView settings configure කරනවා
        configureWebViewSettings(webView);
        
        // WebView clients set කරනවා
        configureWebViewClients(webView);
        
        // JavaScript bridge එක add කරනවා
        addJavaScriptBridge(webView);
        
        return webView;
    }

    /**
     * WebView settings ආරක්ෂිතව configure කරනවා
     */
    private void configureWebViewSettings(WebView webView) {
        try {
            WebSettings settings = webView.getSettings();
            
            // ═══ අත්‍යවශ්‍ය Settings ═══
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            
            // ═══ Cache Settings ═══
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            
            // ═══ Zoom අක්‍රිය කරනවා ═══
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            
            // ═══ File Access ═══
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            
            // ═══ Text Settings ═══
            settings.setTextZoom(100);
            settings.setDefaultTextEncodingName("UTF-8");
            
            // ═══ Layout Settings ═══
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            
            // ═══ අනවශ්‍ය features අක්‍රිය කරනවා ═══
            settings.setGeolocationEnabled(false);
            
            // Safe browsing delays ඇති කරයි, අක්‍රිය කරනවා
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.setSafeBrowsingEnabled(false);
            }
            
            // Mixed content allow කරනවා (local files සඳහා)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            
            // Render priority HIGH set කරනවා (deprecated but still effective)
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
            
            Log.d(TAG, "WebView settings සාර්ථකව configure කෙරිණි");
            
        } catch (Exception e) {
            Log.e(TAG, "WebView settings configure කිරීමේ දෝෂයක්", e);
        }
    }

    /**
     * WebView clients configure කරනවා
     */
    private void configureWebViewClients(WebView webView) {
        try {
            // Scrolling අක්‍රිය කරනවා - gesture conflicts වළක්වනවා
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            
            // පසුබිම transparent කරනවා
            webView.setBackgroundColor(Color.TRANSPARENT);
            
            // ═══ WebViewClient - Page loading handle කරනවා ═══
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page load සම්පූර්ණයි: " + url);
                    mIsWebViewReady = true;
                    
                    // JavaScript bridge ready බව දැනුම් දෙනවා
                    mHandler.post(() -> {
                        try {
                            view.evaluateJavascript(
                                "if(typeof onBridgeReady==='function'){onBridgeReady();}",
                                null
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "onBridgeReady call කිරීමේ දෝෂයක්", e);
                        }
                    });
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, 
                                            String description, String failingUrl) {
                    Log.e(TAG, "WebView දෝෂය: " + errorCode + " - " + description);
                    mHandler.post(() -> showErrorInWebView(description));
                }
                
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    // සියලුම navigation block කරනවා - keyboard navigate නොකළ යුතුයි
                    return true;
                }
            });
            
            // ═══ WebChromeClient - Console logging (debugging සඳහා) ═══
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage cm) {
                    Log.d(TAG, "JS Console: " + cm.message() + 
                          " (line " + cm.lineNumber() + ")");
                    return true;
                }
            });
            
            Log.d(TAG, "WebView clients configure කෙරිණි");
            
        } catch (Exception e) {
            Log.e(TAG, "WebView clients configure කිරීමේ දෝෂයක්", e);
        }
    }

    /**
     * JavaScript Interface එක add කරනවා
     */
    private void addJavaScriptBridge(WebView webView) {
        try {
            webView.addJavascriptInterface(new AndroidBridge(), BRIDGE_NAME);
            Log.d(TAG, "JavaScript bridge add කෙරිණි: " + BRIDGE_NAME);
        } catch (Exception e) {
            Log.e(TAG, "JavaScript bridge add කිරීම අසාර්ථක විය", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Fallback View (WebView අසාර්ථක වූ විට)
    // ════════════════════════════════════════════════════════════════════

    private void addFallbackView() {
        if (mRootContainer == null) return;
        
        mFallbackView = new TextView(this);
        mFallbackView.setText("⌨️ කීබෝඩ් Load කිරීම අසාර්ථක විය\n\nකරුණාකර Android System WebView update කරන්න");
        mFallbackView.setTextColor(Color.WHITE);
        mFallbackView.setTextSize(16);
        mFallbackView.setGravity(Gravity.CENTER);
        mFallbackView.setBackgroundColor(Color.parseColor("#1a1a2e"));
        mFallbackView.setPadding(32, 32, 32, 32);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        mRootContainer.addView(mFallbackView, params);
        
        Log.w(TAG, "Fallback view පෙන්වන ලදී");
    }

    /**
     * සියල්ල අසාර්ථක වූ විට minimal safe view එකක් return කරනවා
     * මෙය app crash වීම වළක්වයි
     */
    private View createEmergencyView() {
        TextView emergency = new TextView(this);
        emergency.setText("⚠️ කීබෝඩ් දෝෂයක්");
        emergency.setTextColor(Color.WHITE);
        emergency.setGravity(Gravity.CENTER);
        emergency.setBackgroundColor(Color.parseColor("#1a1a2e"));
        emergency.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(KEYBOARD_HEIGHT_DP)
        ));
        return emergency;
    }

    /**
     * WebView එකේ error message එකක් පෙන්වනවා
     */
    private void showErrorInWebView(String error) {
        if (mWebView != null) {
            String html = "<html><body style='background:#1a1a2e;color:white;display:flex;" +
                         "align-items:center;justify-content:center;height:100vh;margin:0;" +
                         "font-family:sans-serif;'>" +
                         "<div style='text-align:center;padding:20px;'>" +
                         "<h2>⚠️ දෝෂයක්</h2><p>" + error + "</p></div></body></html>";
            mWebView.loadData(html, "text/html", "UTF-8");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Input View Lifecycle
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d(TAG, "onStartInputView() - inputType: " + info.inputType);
        
        if (mWebView != null && mIsWebViewReady) {
            try {
                int inputType = info.inputType & EditorInfo.TYPE_MASK_CLASS;
                int imeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
                
                String jsCall = String.format(
                    "if(typeof onInputStart==='function'){onInputStart(%d,%d);}",
                    inputType, imeAction
                );
                mWebView.evaluateJavascript(jsCall, null);
            } catch (Exception e) {
                Log.e(TAG, "JS input start notify කිරීමේ දෝෂයක්", e);
            }
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        Log.d(TAG, "onFinishInputView()");
    }

    // ════════════════════════════════════════════════════════════════════
    // JavaScript Bridge - Thread-Safe Implementation
    // ════════════════════════════════════════════════════════════════════

    /**
     * AndroidBridge - JavaScript සිට Java වෙත සන්නිවේදනය කරන Interface එක
     * 
     * සියලුම methods Thread-safe ලෙස ක්‍රියාත්මක වේ.
     * UI operations Main thread එකේ execute වේ.
     */
    public class AndroidBridge {

        /**
         * Text එකක් input field එකට commit කරනවා
         * @param text Commit කළ යුතු text එක
         */
        @JavascriptInterface
        public void commitText(final String text) {
            Log.d(TAG, "Bridge: commitText('" + text + "')");
            
            mHandler.post(() -> {
                try {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null && text != null) {
                        ic.commitText(text, 1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "commitText දෝෂයක්", e);
                }
            });
        }

        /**
         * Cursor එකට පෙර characters delete කරනවා
         * @param count Delete කළ යුතු characters ගණන
         */
        @JavascriptInterface
        public void deleteText(final int count) {
            Log.d(TAG, "Bridge: deleteText(" + count + ")");
            
            mHandler.post(() -> {
                try {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null && count > 0) {
                        ic.deleteSurroundingText(count, 0);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "deleteText දෝෂයක්", e);
                }
            });
        }

        /**
         * Raw key event එකක් send කරනවා
         * @param keyCode Android KeyEvent code එක
         */
        @JavascriptInterface
        public void sendKeyEvent(final int keyCode) {
            Log.d(TAG, "Bridge: sendKeyEvent(" + keyCode + ")");
            
            mHandler.post(() -> {
                try {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        long now = System.currentTimeMillis();
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "sendKeyEvent දෝෂයක්", e);
                }
            });
        }

        /**
         * Editor action එක perform කරනවා (Done, Next, Search, etc.)
         */
        @JavascriptInterface
        public void performEditorAction() {
            Log.d(TAG, "Bridge: performEditorAction()");
            
            mHandler.post(() -> {
                try {
                    InputConnection ic = getCurrentInputConnection();
                    EditorInfo ei = getCurrentInputEditorInfo();
                    if (ic != null && ei != null) {
                        int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
                        ic.performEditorAction(action);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "performEditorAction දෝෂයක්", e);
                }
            });
        }

        /**
         * Haptic feedback (vibration) කරනවා
         * @param durationMs Vibration කාලය milliseconds වලින්
         */
        @JavascriptInterface
        public void vibrate(final int durationMs) {
            try {
                if (mVibrator != null && mVibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mVibrator.vibrate(
                            VibrationEffect.createOneShot(
                                durationMs, 
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        );
                    } else {
                        mVibrator.vibrate(durationMs);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Vibration අසාර්ථක විය", e);
            }
        }

        /**
         * කීබෝඩ් එක hide කරනවා
         */
        @JavascriptInterface
        public void hideKeyboard() {
            Log.d(TAG, "Bridge: hideKeyboard()");
            mHandler.post(() -> {
                try {
                    requestHideSelf(0);
                } catch (Exception e) {
                    Log.e(TAG, "hideKeyboard දෝෂයක්", e);
                }
            });
        }

        /**
         * Debug logging සඳහා
         * @param message Log කළ යුතු message එක
         */
        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS Log: " + message);
        }
        
        /**
         * Selected text ලබාගන්නවා
         * @return Selected text එක හෝ empty string
         */
        @JavascriptInterface
        public String getSelectedText() {
            try {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    CharSequence selected = ic.getSelectedText(0);
                    return selected != null ? selected.toString() : "";
                }
            } catch (Exception e) {
                Log.e(TAG, "getSelectedText දෝෂයක්", e);
            }
            return "";
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ════════════════════════════════════════════════════════════════════

    /**
     * DP units pixels වලට convert කරනවා
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // ════════════════════════════════════════════════════════════════════
    // Cleanup - Memory Leaks වළක්වීම
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() - Service නැවැත්වෙමින්");
        
        cleanupWebView();
        
        mHandler = null;
        mVibrator = null;
        
        super.onDestroy();
    }

    /**
     * WebView ආරක්ෂිතව cleanup කරනවා
     */
    private void cleanupWebView() {
        if (mWebView != null) {
            try {
                mWebView.stopLoading();
                mWebView.removeJavascriptInterface(BRIDGE_NAME);
                
                if (mRootContainer != null) {
                    mRootContainer.removeView(mWebView);
                }
                
                mWebView.clearHistory();
                mWebView.clearCache(false);
                mWebView.destroy();
                
                Log.d(TAG, "WebView cleanup සාර්ථකයි");
            } catch (Exception e) {
                Log.e(TAG, "WebView cleanup කිරීමේ දෝෂයක්", e);
            }
            mWebView = null;
        }
        
        mRootContainer = null;
        mFallbackView = null;
        mIsWebViewReady = false;
    }
}