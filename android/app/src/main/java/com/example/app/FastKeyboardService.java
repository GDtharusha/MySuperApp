package com.example.app;

import android.content.Context;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class FastKeyboardService extends InputMethodService {

    private static final String TAG = "FastKeyboard";
    private FrameLayout container;
    private WebView webView;
    private Handler handler;
    private Vibrator vibrator;
    private boolean webViewReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception e) {
            Log.e(TAG, "Vibrator error", e);
        }
        Log.d(TAG, "Service created");
    }

    @Override
    public View onCreateInputView() {
        Log.d(TAG, "Creating input view");
        
        // Create container
        container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(320)
        ));
        container.setBackgroundColor(Color.parseColor("#1a1a2e"));
        
        // Create WebView after short delay
        handler.postDelayed(this::createWebView, 100);
        
        return container;
    }
    
    private void createWebView() {
        try {
            Log.d(TAG, "Creating WebView");
            
            webView = new WebView(getApplicationContext());
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
            
            // Settings
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setSupportZoom(false);
            
            // Disable problematic features
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.setBackgroundColor(Color.TRANSPARENT);
            
            // Bridge
            webView.addJavascriptInterface(new KeyboardBridge(), "Native");
            
            // Client
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    webViewReady = true;
                    Log.d(TAG, "WebView ready");
                }
            });
            
            // Add to container
            container.addView(webView);
            
            // Load HTML
            String html = getKeyboardHtml();
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            
            Log.d(TAG, "WebView created successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "WebView creation failed: " + e.getMessage(), e);
        }
    }
    
    private String getKeyboardHtml() {
        return "<!DOCTYPE html>\n" +
"<html>\n" +
"<head>\n" +
"<meta charset=\"UTF-8\">\n" +
"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">\n" +
"<style>\n" +
"*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent;-webkit-user-select:none;user-select:none}\n" +
"html,body{width:100%;height:100%;overflow:hidden;background:#1a1a2e;font-family:system-ui,-apple-system,sans-serif}\n" +
"#kb{display:flex;flex-direction:column;height:100%;padding:8px 4px 12px}\n" +
".row{display:flex;justify-content:center;gap:5px;flex:1;margin:4px 0}\n" +
".row:nth-child(2){padding:0 5%}\n" +
".key{display:flex;align-items:center;justify-content:center;flex:1;min-width:28px;max-width:42px;background:linear-gradient(180deg,#3d3d5c,#2a2a45);border:none;border-radius:8px;color:#fff;font-size:22px;font-weight:500;box-shadow:0 2px 0 rgba(0,0,0,.3)}\n" +
".key:active{background:linear-gradient(180deg,#5a5a8c,#4a4a7a);transform:scale(.95)}\n" +
".key.sp{background:linear-gradient(180deg,#252540,#1a1a30);color:#9ca3af;font-size:14px;max-width:56px;flex:1.5}\n" +
".key.ent{background:linear-gradient(180deg,#2563eb,#1d4ed8);color:#fff}\n" +
".key.spc{max-width:none;flex:5;font-size:12px;color:#6b7280}\n" +
".key.sh.on{background:linear-gradient(180deg,#2563eb,#1d4ed8);color:#fff}\n" +
".key.sh.caps{background:linear-gradient(180deg,#059669,#047857);color:#fff}\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"<div id=\"kb\"></div>\n" +
"<script>\n" +
"var shift=false,caps=false,nums=false,syms=false;\n" +
"var L={\n" +
"letters:[['q','w','e','r','t','y','u','i','o','p'],['a','s','d','f','g','h','j','k','l'],['SH','z','x','c','v','b','n','m','DEL'],['123',',','SPC','.','ENT']],\n" +
"numbers:[['1','2','3','4','5','6','7','8','9','0'],['@','#','$','%','&','-','+','(',')'],['SYM','*','\"',\"'\":',';','!','?','DEL'],['ABC',',','SPC','.','ENT']],\n" +
"symbols:[['~','`','|','•','√','π','÷','×','¶','∆'],['£','€','¥','^','°','=','{','}','\\\\'],['123','©','®','™','✓','[',']','<','DEL'],['ABC',',','SPC','.','ENT']]\n" +
"};\n" +
"function render(){\n" +
"var layout=syms?L.symbols:nums?L.numbers:L.letters;\n" +
"var h='';\n" +
"layout.forEach(function(row){\n" +
"h+='<div class=\"row\">';\n" +
"row.forEach(function(k){\n" +
"var c='key',t=k;\n" +
"if(k=='SH'){c+=' sp sh';if(caps)c+=' caps';else if(shift)c+=' on';t=shift||caps?'⬆':'⇧';}\n" +
"else if(k=='DEL'){c+=' sp';t='⌫';}\n" +
"else if(k=='ENT'){c+=' sp ent';t='↵';}\n" +
"else if(k=='SPC'){c+=' spc';t='space';}\n" +
"else if(k=='123'||k=='ABC'||k=='SYM'){c+=' sp';}\n" +
"else if(k.length==1&&k.match(/[a-z]/)){t=(shift||caps)?k.toUpperCase():k;}\n" +
"h+='<div class=\"'+c+'\" data-k=\"'+k+'\">'+t+'</div>';\n" +
"});\n" +
"h+='</div>';\n" +
"});\n" +
"document.getElementById('kb').innerHTML=h;\n" +
"}\n" +
"function handle(k){\n" +
"Native.vibrate();\n" +
"if(k=='SH'){if(caps){caps=false;shift=false;}else if(shift){caps=true;}else{shift=true;}render();}\n" +
"else if(k=='DEL'){Native.del();}\n" +
"else if(k=='ENT'){Native.enter();}\n" +
"else if(k=='SPC'){Native.type(' ');if(shift&&!caps){shift=false;render();}}\n" +
"else if(k=='123'){nums=true;syms=false;render();}\n" +
"else if(k=='ABC'){nums=false;syms=false;render();}\n" +
"else if(k=='SYM'){syms=true;nums=false;render();}\n" +
"else{var t=k;if((shift||caps)&&k.match(/^[a-z]$/))t=k.toUpperCase();Native.type(t);if(shift&&!caps){shift=false;render();}}\n" +
"}\n" +
"document.addEventListener('DOMContentLoaded',function(){\n" +
"render();\n" +
"document.getElementById('kb').addEventListener('touchstart',function(e){\n" +
"e.preventDefault();\n" +
"var el=e.target.closest('.key');\n" +
"if(el)handle(el.dataset.k);\n" +
"},{passive:false});\n" +
"document.getElementById('kb').addEventListener('mousedown',function(e){\n" +
"var el=e.target.closest('.key');\n" +
"if(el)handle(el.dataset.k);\n" +
"});\n" +
"});\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }
    
    // Bridge class
    public class KeyboardBridge {
        
        @JavascriptInterface
        public void type(String text) {
            handler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && text != null) {
                    ic.commitText(text, 1);
                }
            });
        }
        
        @JavascriptInterface
        public void del() {
            handler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.deleteSurroundingText(1, 0);
                }
            });
        }
        
        @JavascriptInterface
        public void enter() {
            handler.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                EditorInfo ei = getCurrentInputEditorInfo();
                if (ic != null && ei != null) {
                    int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
                    if (action == EditorInfo.IME_ACTION_NONE) {
                        ic.commitText("\n", 1);
                    } else {
                        ic.performEditorAction(action);
                    }
                }
            });
        }
        
        @JavascriptInterface
        public void vibrate() {
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(3, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(3);
                    }
                }
            } catch (Exception e) {}
        }
    }
    
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
    
    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}