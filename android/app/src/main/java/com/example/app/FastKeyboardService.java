package com.example.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * FastKeyboardService - Ultimate Hybrid Keyboard Engine
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * මෙය HTML/CSS/JS වලින් fully customizable keyboard එකක් සඳහා Java engine එකයි.
 * 
 * Features:
 * - WebView based UI (HTML/CSS/JS වලින් ඕනෑම දෙයක් කරන්න පුළුවන්)
 * - Crash-proof (WebView fail වුණොත් native fallback)
 * - Hardware accelerated (fast performance)
 * - Full bridge API (JS වලින් ඕනෑම Android feature එකක් access කරන්න පුළුවන්)
 * - Voice input support
 * - Clipboard support
 * - Proper height & positioning
 * 
 * Package: com.example.app
 * @version 5.0.0-ultimate
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class FastKeyboardService extends InputMethodService {

    private static final String TAG = "FastKeyboard";
    private static final String BRIDGE_NAME = "AndroidBridge";
    private static final String KEYBOARD_URL = "file:///android_asset/keyboard.html";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBOARD HEIGHT CONFIGURATION
    // Portrait mode: 320dp, Landscape mode: 200dp
    // මෙය HTML/JS වලින් dynamic වෙනස් කරන්නත් පුළුවන්
    // ═══════════════════════════════════════════════════════════════════════════
    private static final int KEYBOARD_HEIGHT_PORTRAIT_DP = 320;
    private static final int KEYBOARD_HEIGHT_LANDSCAPE_DP = 200;
    
    // Views
    private FrameLayout rootContainer;
    private WebView webView;
    private LinearLayout nativeFallback;
    
    // State
    private boolean isWebViewReady = false;
    private boolean isWebViewFailed = false;
    private int currentHeight = 0;
    
    // Utilities
    private Handler mainHandler;
    private Vibrator vibrator;
    private ClipboardManager clipboardManager;
    private AudioManager audioManager;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    
    // Current input info
    private EditorInfo currentEditorInfo;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreate
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "════════════════════════════════════════════");
        Log.d(TAG, "  FastKeyboard Service Started!");
        Log.d(TAG, "════════════════════════════════════════════");
        
        mainHandler = new Handler(Looper.getMainLooper());
        initializeServices();
    }
    
    private void initializeServices() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Log.d(TAG, "✓ System services initialized");
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to initialize services", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE: onCreateInputView - මෙය keyboard UI create කරන ප්‍රධාන method එක
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public View onCreateInputView() {
        Log.d(TAG, "onCreateInputView() - Creating keyboard UI...");
        
        try {
            // Calculate proper height
            currentHeight = calculateKeyboardHeight();
            Log.d(TAG, "Keyboard height: " + currentHeight + "px");
            
            // Create root container
            rootContainer = createRootContainer();
            
            // Try WebView first, with delay for stability
            mainHandler.postDelayed(this::initializeWebView, 50);
            
            return rootContainer;
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Critical error in onCreateInputView", e);
            return createErrorView("Keyboard initialization failed: " + e.getMessage());
        }
    }
    
    private int calculateKeyboardHeight() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        
        int heightDp = isLandscape ? KEYBOARD_HEIGHT_LANDSCAPE_DP : KEYBOARD_HEIGHT_PORTRAIT_DP;
        int heightPx = Math.round(heightDp * dm.density);
        
        // Ensure minimum height
        int minHeight = Math.round(200 * dm.density);
        return Math.max(heightPx, minHeight);
    }
    
    private FrameLayout createRootContainer() {
        FrameLayout container = new FrameLayout(this);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            currentHeight
        );
        container.setLayoutParams(params);
        container.setBackgroundColor(Color.parseColor("#1a1a2e"));
        
        // Hardware acceleration for performance
        container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // Loading indicator
        TextView loading = new TextView(this);
        loading.setText("⌨️ Loading...");
        loading.setTextColor(Color.WHITE);
        loading.setGravity(Gravity.CENTER);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        loading.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        container.addView(loading);
        
        return container;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEBVIEW INITIALIZATION - Delayed & Safe
    // ═══════════════════════════════════════════════════════════════════════════

    private void initializeWebView() {
        if (isWebViewFailed) {
            createNativeFallback();
            return;
        }
        
        Log.d(TAG, "Initializing WebView...");
        
        try {
            // Create WebView
            webView = new WebView(this);
            
            // Configure WebView
            configureWebView();
            
            // Add JavaScript Bridge
            webView.addJavascriptInterface(new KeyboardBridge(), BRIDGE_NAME);
            
            // Set layout params
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            webView.setLayoutParams(params);
            
            // Clear container and add WebView
            rootContainer.removeAllViews();
            rootContainer.addView(webView);
            
            // Load keyboard HTML
            webView.loadUrl(KEYBOARD_URL);
            Log.d(TAG, "✓ WebView created, loading: " + KEYBOARD_URL);
            
        } catch (Exception e) {
            Log.e(TAG, "✗ WebView initialization failed", e);
            isWebViewFailed = true;
            createNativeFallback();
        }
    }
    
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        
        // Essential settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        
        // Performance settings
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        
        // Disable unnecessary features
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setGeolocationEnabled(false);
        
        // File access for local assets
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // Text settings
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");
        
        // Layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // Safe browsing off for speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        
        // Mixed content (for API calls)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // Disable scrolling
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        
        // Background
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        // WebView clients
        setupWebViewClients();
    }
    
    private void setupWebViewClients() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "✓ Page loaded: " + url);
                isWebViewReady = true;
                
                // Notify JavaScript that bridge is ready
                callJavaScript("onBridgeReady", null);
                
                // Send current input info
                if (currentEditorInfo != null) {
                    sendInputInfo(currentEditorInfo);
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "✗ WebView error: " + errorCode + " - " + description);
                isWebViewFailed = true;
                mainHandler.post(() -> createNativeFallback());
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                String level = cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? "ERROR" : "LOG";
                Log.d(TAG, "JS " + level + ": " + cm.message() + " (line " + cm.lineNumber() + ")");
                return true;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE FALLBACK KEYBOARD - WebView fail වුණොත් මෙය පෙන්වනවා
    // ═══════════════════════════════════════════════════════════════════════════

    private void createNativeFallback() {
        Log.w(TAG, "Creating native fallback keyboard...");
        
        if (rootContainer == null) return;
        rootContainer.removeAllViews();
        
        nativeFallback = new LinearLayout(this);
        nativeFallback.setOrientation(LinearLayout.VERTICAL);
        nativeFallback.setBackgroundColor(Color.parseColor("#1a1a2e"));
        nativeFallback.setPadding(dp(4), dp(8), dp(4), dp(16));
        
        // Warning message
        TextView warning = new TextView(this);
        warning.setText("⚠️ WebView failed - Using fallback keyboard");
        warning.setTextColor(Color.parseColor("#ff9800"));
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, dp(4), 0, dp(8));
        nativeFallback.addView(warning);
        
        // Simple QWERTY rows
        String[][] rows = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"},
            {"123", ",", "space", ".", "↵"}
        };
        
        for (String[] row : rows) {
            nativeFallback.addView(createNativeRow(row));
        }
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootContainer.addView(nativeFallback, params);
    }
    
    private LinearLayout createNativeRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        );
        rowParams.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowParams);
        
        for (String key : keys) {
            TextView keyView = createNativeKey(key);
            row.addView(keyView);
        }
        
        return row;
    }
    
    private TextView createNativeKey(final String key) {
        TextView keyView = new TextView(this);
        keyView.setText(key.equals("space") ? " " : key);
        keyView.setGravity(Gravity.CENTER);
        keyView.setTextColor(Color.WHITE);
        keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, key.length() > 1 ? 14 : 20);
        keyView.setTypeface(Typeface.DEFAULT_BOLD);
        
        float weight = key.equals("space") ? 4f : (key.length() > 1 ? 1.4f : 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        keyView.setLayoutParams(params);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(key.equals("↵") ? "#2060a0" : "#3d3d5c"));
        bg.setCornerRadius(dp(6));
        keyView.setBackground(bg);
        
        keyView.setOnClickListener(v -> {
            doVibrate(3);
            handleNativeKeyPress(key);
        });
        
        return keyView;
    }
    
    private void handleNativeKeyPress(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        
        switch (key) {
            case "⌫":
                ic.deleteSurroundingText(1, 0);
                break;
            case "↵":
                handleEnterKey(ic);
                break;
            case "space":
                ic.commitText(" ", 1);
                break;
            case "⇧":
                // Toggle shift - simplified
                break;
            case "123":
                // Toggle numbers - simplified
                break;
            default:
                ic.commitText(key, 1);
        }
    }
    
    private View createErrorView(String message) {
        TextView error = new TextView(this);
        error.setText("❌ " + message);
        error.setTextColor(Color.WHITE);
        error.setBackgroundColor(Color.parseColor("#b71c1c"));
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(16), dp(16), dp(16), dp(16));
        error.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, currentHeight
        ));
        return error;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentEditorInfo = info;
        
        Log.d(TAG, "onStartInputView - inputType: " + info.inputType + ", imeOptions: " + info.imeOptions);
        
        if (isWebViewReady && webView != null) {
            sendInputInfo(info);
        }
    }
    
    private void sendInputInfo(EditorInfo info) {
        int inputType = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        int inputVariation = info.inputType & EditorInfo.TYPE_MASK_VARIATION;
        int imeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        
        String actionLabel = "";
        if (info.actionLabel != null) {
            actionLabel = info.actionLabel.toString();
        }
        
        String jsCall = String.format(Locale.US,
            "onInputStart({inputType:%d,inputVariation:%d,imeAction:%d,actionLabel:'%s'})",
            inputType, inputVariation, imeAction, actionLabel.replace("'", "\\'")
        );
        
        callJavaScript(jsCall, null);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        Log.d(TAG, "onFinishInputView");
        callJavaScript("onInputEnd", null);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Recalculate height on orientation change
        int newHeight = calculateKeyboardHeight();
        if (newHeight != currentHeight) {
            currentHeight = newHeight;
            if (rootContainer != null) {
                ViewGroup.LayoutParams params = rootContainer.getLayoutParams();
                params.height = currentHeight;
                rootContainer.setLayoutParams(params);
            }
            callJavaScript("onOrientationChange", newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "'landscape'" : "'portrait'");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JAVASCRIPT BRIDGE - මෙය HTML/JS වලින් call කරන්න පුළුවන් methods
    // ═══════════════════════════════════════════════════════════════════════════

    public class KeyboardBridge {
        
        // ═══════════════════════════════════════════════════════════════════
        // TEXT INPUT METHODS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Text commit කරන්න (type කරන්න)
         * JS: AndroidBridge.commitText("hello")
         */
        @JavascriptInterface
        public void commitText(final String text) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && text != null) {
                    ic.commitText(text, 1);
                }
            });
        }
        
        /**
         * Characters delete කරන්න
         * JS: AndroidBridge.deleteText(1) - එක character delete
         * JS: AndroidBridge.deleteText(5) - 5 characters delete
         */
        @JavascriptInterface
        public void deleteText(final int count) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && count > 0) {
                    ic.deleteSurroundingText(count, 0);
                }
            });
        }
        
        /**
         * Forward delete (cursor ඉස්සරහින් delete)
         * JS: AndroidBridge.deleteForward(1)
         */
        @JavascriptInterface
        public void deleteForward(final int count) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && count > 0) {
                    ic.deleteSurroundingText(0, count);
                }
            });
        }
        
        /**
         * Word එකක් delete කරන්න
         * JS: AndroidBridge.deleteWord()
         */
        @JavascriptInterface
        public void deleteWord() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                
                CharSequence before = ic.getTextBeforeCursor(50, 0);
                if (before == null || before.length() == 0) return;
                
                String text = before.toString();
                text = text.trim();
                int lastSpace = text.lastIndexOf(' ');
                
                int deleteCount = (lastSpace == -1) ? before.length() : (before.length() - lastSpace - 1);
                ic.deleteSurroundingText(deleteCount, 0);
            });
        }
        
        /**
         * සියල්ල delete කරන්න
         * JS: AndroidBridge.deleteAll()
         */
        @JavascriptInterface
        public void deleteAll() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                
                CharSequence before = ic.getTextBeforeCursor(10000, 0);
                CharSequence after = ic.getTextAfterCursor(10000, 0);
                
                int beforeLen = before != null ? before.length() : 0;
                int afterLen = after != null ? after.length() : 0;
                
                ic.deleteSurroundingText(beforeLen, afterLen);
            });
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // CURSOR & SELECTION METHODS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Cursor move කරන්න
         * JS: AndroidBridge.moveCursor(-1) - left
         * JS: AndroidBridge.moveCursor(1) - right
         */
        @JavascriptInterface
        public void moveCursor(final int offset) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                
                CharSequence before = ic.getTextBeforeCursor(10000, 0);
                int currentPos = before != null ? before.length() : 0;
                int newPos = Math.max(0, currentPos + offset);
                
                ic.setSelection(newPos, newPos);
            });
        }
        
        /**
         * Text select කරන්න
         * JS: AndroidBridge.setSelection(0, 5) - පළමු 5 characters select
         */
        @JavascriptInterface
        public void setSelection(final int start, final int end) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.setSelection(start, end);
                }
            });
        }
        
        /**
         * සියල්ල select කරන්න
         * JS: AndroidBridge.selectAll()
         */
        @JavascriptInterface
        public void selectAll() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                
                ic.performContextMenuAction(android.R.id.selectAll);
            });
        }
        
        /**
         * Selected text ගන්න
         * JS: var text = AndroidBridge.getSelectedText()
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
        
        /**
         * Cursor ට පෙර text ගන්න
         * JS: var text = AndroidBridge.getTextBeforeCursor(10)
         */
        @JavascriptInterface
        public String getTextBeforeCursor(int length) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence text = ic.getTextBeforeCursor(length, 0);
                return text != null ? text.toString() : "";
            }
            return "";
        }
        
        /**
         * Cursor ට පසු text ගන්න
         * JS: var text = AndroidBridge.getTextAfterCursor(10)
         */
        @JavascriptInterface
        public String getTextAfterCursor(int length) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence text = ic.getTextAfterCursor(length, 0);
                return text != null ? text.toString() : "";
            }
            return "";
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // EDITOR ACTIONS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Editor action perform කරන්න (Enter/Search/Done/Next/Go)
         * JS: AndroidBridge.performEditorAction()
         */
        @JavascriptInterface
        public void performEditorAction() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && currentEditorInfo != null) {
                    int action = currentEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
                    ic.performEditorAction(action);
                }
            });
        }
        
        /**
         * Specific action perform කරන්න
         * JS: AndroidBridge.performAction(3) // EditorInfo.IME_ACTION_SEARCH
         * 
         * Actions:
         * 0 = NONE, 1 = GO, 2 = SEARCH, 3 = SEND, 4 = NEXT, 5 = DONE, 6 = PREVIOUS
         */
        @JavascriptInterface
        public void performAction(final int action) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.performEditorAction(action);
                }
            });
        }
        
        /**
         * Enter key (new line) send කරන්න
         * JS: AndroidBridge.sendEnter()
         */
        @JavascriptInterface
        public void sendEnter() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText("\n", 1);
                }
            });
        }
        
        /**
         * Tab send කරන්න
         * JS: AndroidBridge.sendTab()
         */
        @JavascriptInterface
        public void sendTab() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText("\t", 1);
                }
            });
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // KEY EVENTS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Raw key event send කරන්න
         * JS: AndroidBridge.sendKeyEvent(66) // KEYCODE_ENTER
         */
        @JavascriptInterface
        public void sendKeyEvent(final int keyCode) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    long now = System.currentTimeMillis();
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                }
            });
        }
        
        /**
         * Key down event විතරක්
         * JS: AndroidBridge.sendKeyDown(21) // KEYCODE_DPAD_LEFT
         */
        @JavascriptInterface
        public void sendKeyDown(final int keyCode) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    long now = System.currentTimeMillis();
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                }
            });
        }
        
        /**
         * Key up event විතරක්
         * JS: AndroidBridge.sendKeyUp(21)
         */
        @JavascriptInterface
        public void sendKeyUp(final int keyCode) {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    long now = System.currentTimeMillis();
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                }
            });
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // CLIPBOARD METHODS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Clipboard එකට copy කරන්න
         * JS: AndroidBridge.copyToClipboard("text to copy")
         */
        @JavascriptInterface
        public void copyToClipboard(final String text) {
            mainHandler.post(() -> {
                if (clipboardManager != null && text != null) {
                    ClipData clip = ClipData.newPlainText("text", text);
                    clipboardManager.setPrimaryClip(clip);
                }
            });
        }
        
        /**
         * Clipboard එකෙන් paste කරන්න
         * JS: AndroidBridge.pasteFromClipboard()
         */
        @JavascriptInterface
        public void pasteFromClipboard() {
            mainHandler.post(() -> {
                String text = getClipboardText();
                if (!text.isEmpty()) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(text, 1);
                    }
                }
            });
        }
        
        /**
         * Clipboard text ගන්න
         * JS: var text = AndroidBridge.getClipboardText()
         */
        @JavascriptInterface
        public String getClipboardText() {
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    return text != null ? text.toString() : "";
                }
            }
            return "";
        }
        
        /**
         * Cut (selected text cut කරන්න)
         * JS: AndroidBridge.cut()
         */
        @JavascriptInterface
        public void cut() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.performContextMenuAction(android.R.id.cut);
                }
            });
        }
        
        /**
         * Copy (selected text copy කරන්න)
         * JS: AndroidBridge.copy()
         */
        @JavascriptInterface
        public void copy() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.performContextMenuAction(android.R.id.copy);
                }
            });
        }
        
        /**
         * Paste
         * JS: AndroidBridge.paste()
         */
        @JavascriptInterface
        public void paste() {
            mainHandler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.performContextMenuAction(android.R.id.paste);
                }
            });
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // HAPTIC FEEDBACK & SOUND
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Vibrate කරන්න
         * JS: AndroidBridge.vibrate(10) // 10ms vibration
         */
        @JavascriptInterface
        public void vibrate(final int durationMs) {
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(durationMs);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Vibration failed", e);
            }
        }
        
        /**
         * System key click sound play කරන්න
         * JS: AndroidBridge.playKeySound()
         */
        @JavascriptInterface
        public void playKeySound() {
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1);
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // KEYBOARD VISIBILITY
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Keyboard hide කරන්න
         * JS: AndroidBridge.hideKeyboard()
         */
        @JavascriptInterface
        public void hideKeyboard() {
            mainHandler.post(() -> requestHideSelf(0));
        }
        
        /**
         * Keyboard switch කරන්න (system keyboard selector)
         * JS: AndroidBridge.switchKeyboard()
         */
        @JavascriptInterface
        public void switchKeyboard() {
            mainHandler.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            });
        }
        
        /**
         * Keyboard height change කරන්න
         * JS: AndroidBridge.setKeyboardHeight(350)
         */
        @JavascriptInterface
        public void setKeyboardHeight(final int heightDp) {
            mainHandler.post(() -> {
                if (rootContainer != null) {
                    currentHeight = dp(heightDp);
                    ViewGroup.LayoutParams params = rootContainer.getLayoutParams();
                    params.height = currentHeight;
                    rootContainer.setLayoutParams(params);
                }
            });
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // VOICE INPUT
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Voice input start කරන්න
         * JS: AndroidBridge.startVoiceInput()
         */
        @JavascriptInterface
        public void startVoiceInput() {
            mainHandler.post(() -> startVoiceRecognition());
        }
        
        /**
         * Voice input stop කරන්න
         * JS: AndroidBridge.stopVoiceInput()
         */
        @JavascriptInterface
        public void stopVoiceInput() {
            mainHandler.post(() -> stopVoiceRecognition());
        }
        
        /**
         * Voice input available ද check කරන්න
         * JS: var available = AndroidBridge.isVoiceInputAvailable()
         */
        @JavascriptInterface
        public boolean isVoiceInputAvailable() {
            return SpeechRecognizer.isRecognitionAvailable(FastKeyboardService.this);
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // UTILITY METHODS
        // ═══════════════════════════════════════════════════════════════════
        
        /**
         * Toast message පෙන්වන්න
         * JS: AndroidBridge.showToast("Hello!")
         */
        @JavascriptInterface
        public void showToast(final String message) {
            mainHandler.post(() -> Toast.makeText(FastKeyboardService.this, message, Toast.LENGTH_SHORT).show());
        }
        
        /**
         * Log message (debugging සඳහා)
         * JS: AndroidBridge.log("Debug message")
         */
        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS: " + message);
        }
        
        /**
         * Current input type ගන්න
         * JS: var type = AndroidBridge.getInputType()
         */
        @JavascriptInterface
        public int getInputType() {
            return currentEditorInfo != null ? currentEditorInfo.inputType : 0;
        }
        
        /**
         * Current IME action ගන්න
         * JS: var action = AndroidBridge.getImeAction()
         */
        @JavascriptInterface
        public int getImeAction() {
            return currentEditorInfo != null ? (currentEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION) : 0;
        }
        
        /**
         * App package name ගන්න (current app එක)
         * JS: var pkg = AndroidBridge.getCurrentPackage()
         */
        @JavascriptInterface
        public String getCurrentPackage() {
            return currentEditorInfo != null && currentEditorInfo.packageName != null 
                ? currentEditorInfo.packageName : "";
        }
        
        /**
         * Device info ගන්න
         * JS: var info = AndroidBridge.getDeviceInfo()
         */
        @JavascriptInterface
        public String getDeviceInfo() {
            return String.format(Locale.US,
                "{\"manufacturer\":\"%s\",\"model\":\"%s\",\"sdk\":%d,\"version\":\"%s\"}",
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.RELEASE
            );
        }
        
        /**
         * Orientation ගන්න
         * JS: var orientation = AndroidBridge.getOrientation() // "portrait" or "landscape"
         */
        @JavascriptInterface
        public String getOrientation() {
            int orientation = getResources().getConfiguration().orientation;
            return orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait";
        }
        
        /**
         * Screen dimensions ගන්න
         * JS: var dims = AndroidBridge.getScreenDimensions()
         */
        @JavascriptInterface
        public String getScreenDimensions() {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            return String.format(Locale.US,
                "{\"width\":%d,\"height\":%d,\"density\":%.2f}",
                dm.widthPixels, dm.heightPixels, dm.density
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VOICE RECOGNITION
    // ═══════════════════════════════════════════════════════════════════════════

    private void startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callJavaScript("onVoiceError", "'Voice recognition not available'");
            return;
        }
        
        if (isListening) {
            stopVoiceRecognition();
            return;
        }
        
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    callJavaScript("onVoiceStart", null);
                }
                
                @Override
                public void onBeginningOfSpeech() {}
                
                @Override
                public void onRmsChanged(float rmsdB) {
                    callJavaScript("onVoiceRms", String.valueOf(rmsdB));
                }
                
                @Override
                public void onBufferReceived(byte[] buffer) {}
                
                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    callJavaScript("onVoiceEnd", null);
                }
                
                @Override
                public void onError(int error) {
                    isListening = false;
                    callJavaScript("onVoiceError", "'" + getVoiceErrorMessage(error) + "'");
                }
                
                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        callJavaScript("onVoiceResult", "'" + text.replace("'", "\\'") + "'");
                        
                        // Auto commit text
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(text + " ", 1);
                        }
                    }
                }
                
                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        callJavaScript("onVoicePartial", "'" + matches.get(0).replace("'", "\\'") + "'");
                    }
                }
                
                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
            
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            
            speechRecognizer.startListening(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Voice recognition failed", e);
            callJavaScript("onVoiceError", "'" + e.getMessage() + "'");
        }
    }
    
    private void stopVoiceRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
    }
    
    private String getVoiceErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Speech timeout";
            default: return "Unknown error";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleEnterKey(InputConnection ic) {
        if (currentEditorInfo != null) {
            int action = currentEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.commitText("\n", 1);
            } else {
                ic.performEditorAction(action);
            }
        } else {
            ic.commitText("\n", 1);
        }
    }
    
    private void callJavaScript(String function, String args) {
        if (webView != null && isWebViewReady) {
            String call = args != null ? 
                "if(typeof " + function + "==='function'){" + function + "(" + args + ");}" :
                "if(typeof " + function + "==='function'){" + function + "();}";
            
            mainHandler.post(() -> webView.evaluateJavascript(call, null));
        }
    }
    
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void doVibrate(int durationMs) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Vibration failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy - Cleaning up...");
        
        stopVoiceRecognition();
        
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface(BRIDGE_NAME);
            webView.destroy();
            webView = null;
        }
        
        rootContainer = null;
        nativeFallback = null;
        vibrator = null;
        clipboardManager = null;
        audioManager = null;
        
        super.onDestroy();
    }
}