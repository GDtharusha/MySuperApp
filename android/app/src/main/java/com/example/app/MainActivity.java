package com.example.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;

import com.getcapacitor.BridgeActivity;

import java.util.List;

public class MainActivity extends BridgeActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Add keyboard setup bridge
        getBridge().getWebView().addJavascriptInterface(new KeyboardSetupBridge(), "KeyboardSetup");
        
        // Notify JS that bridge is ready
        getBridge().getWebView().post(() -> {
            getBridge().getWebView().evaluateJavascript(
                "if(typeof onSetupBridgeReady==='function'){onSetupBridgeReady();}",
                null
            );
        });
    }
    
    /**
     * KeyboardSetupBridge - App එකෙන් keyboard enable/switch කරන්න
     */
    public class KeyboardSetupBridge {
        
        /**
         * Keyboard settings open කරන්න
         */
        @JavascriptInterface
        public void openKeyboardSettings() {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        
        /**
         * Keyboard picker dialog පෙන්වන්න
         */
        @JavascriptInterface
        public void showKeyboardPicker() {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        }
        
        /**
         * Fast Keyboard enable වෙලා තියෙනවද check කරන්න
         */
        @JavascriptInterface
        public boolean isKeyboardEnabled() {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return false;
            
            List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
            String myPackage = getPackageName();
            
            for (InputMethodInfo info : enabledMethods) {
                if (info.getPackageName().equals(myPackage)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Fast Keyboard දැනට active keyboard එකද check කරන්න
         */
        @JavascriptInterface
        public boolean isKeyboardActive() {
            String currentIme = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
            );
            
            if (currentIme != null) {
                return currentIme.contains(getPackageName());
            }
            return false;
        }
    }
}