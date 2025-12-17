package com.example.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * FastKeyboardService - Pure Native Keyboard (No WebView)
 * 
 * මෙය 100% Native Java keyboard එකක්.
 * WebView නැති නිසා crash වෙන්නේ නැහැ.
 * 
 * Package: com.example.app
 */
public class FastKeyboardService extends InputMethodService implements View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = "FastKeyboard";
    
    // State
    private boolean isShift = false;
    private boolean isCaps = false;
    private boolean isNumbers = false;
    
    // Views
    private LinearLayout keyboardView;
    private Vibrator vibrator;
    
    // Layouts
    private static final String[][] LETTERS = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
        {"123", ",", "SPACE", ".", "ENTER"}
    };
    
    private static final String[][] NUMBERS = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"@", "#", "$", "%", "&", "-", "+", "(", ")"},
        {"=", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
        {"ABC", ",", "SPACE", ".", "ENTER"}
    };

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "✓ onCreate() - Service started");
        
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        } catch (Exception e) {
            Log.e(TAG, "Vibrator init failed", e);
        }
    }

    @Override
    public View onCreateInputView() {
        Log.d(TAG, "✓ onCreateInputView() - Creating keyboard UI");
        
        try {
            keyboardView = createKeyboard();
            Log.d(TAG, "✓ Keyboard created successfully!");
            return keyboardView;
        } catch (Exception e) {
            Log.e(TAG, "✗ Keyboard creation failed!", e);
            return createErrorView(e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // KEYBOARD CREATION
    // ════════════════════════════════════════════════════════════════════

    private LinearLayout createKeyboard() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setBackgroundColor(Color.parseColor("#1a1a2e"));
        keyboard.setPadding(dp(4), dp(8), dp(4), dp(12));
        
        // Layout params for keyboard
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(260)
        );
        keyboard.setLayoutParams(params);
        
        // Get current layout
        String[][] layout = isNumbers ? NUMBERS : LETTERS;
        
        // Create rows
        for (int i = 0; i < layout.length; i++) {
            keyboard.addView(createRow(layout[i], i));
        }
        
        return keyboard;
    }
    
    private LinearLayout createRow(String[] keys, int rowIndex) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        );
        rowParams.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowParams);
        
        // Add padding to row 2 (A-L row)
        if (rowIndex == 1) {
            row.setPadding(dp(16), 0, dp(16), 0);
        }
        
        for (String key : keys) {
            row.addView(createKey(key));
        }
        
        return row;
    }
    
    private TextView createKey(String key) {
        TextView keyView = new TextView(this);
        
        // Set text
        String displayText = getDisplayText(key);
        keyView.setText(displayText);
        keyView.setGravity(Gravity.CENTER);
        keyView.setTextColor(Color.WHITE);
        keyView.setTypeface(Typeface.DEFAULT_BOLD);
        
        // Text size based on key type
        if (key.length() == 1) {
            keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        } else {
            keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        }
        
        // Layout params
        float weight = getKeyWeight(key);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, weight
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        keyView.setLayoutParams(params);
        
        // Background
        keyView.setBackground(createKeyBackground(key));
        
        // Tag for identification
        keyView.setTag(key);
        
        // Click listeners
        keyView.setOnClickListener(this);
        keyView.setOnLongClickListener(this);
        
        // Make clickable
        keyView.setClickable(true);
        keyView.setFocusable(true);
        
        return keyView;
    }
    
    private String getDisplayText(String key) {
        switch (key) {
            case "SHIFT":
                return (isShift || isCaps) ? "⬆" : "⇧";
            case "DEL":
                return "⌫";
            case "ENTER":
                return "↵";
            case "SPACE":
                return "space";
            case "123":
                return "123";
            case "ABC":
                return "ABC";
            default:
                if (key.length() == 1 && Character.isLetter(key.charAt(0))) {
                    return (isShift || isCaps) ? key.toUpperCase() : key.toLowerCase();
                }
                return key;
        }
    }
    
    private float getKeyWeight(String key) {
        switch (key) {
            case "SPACE":
                return 4f;
            case "SHIFT":
            case "DEL":
            case "123":
            case "ABC":
            case "ENTER":
                return 1.4f;
            default:
                return 1f;
        }
    }
    
    private StateListDrawable createKeyBackground(String key) {
        StateListDrawable states = new StateListDrawable();
        
        // Colors based on key type
        int normalColor, pressedColor;
        
        switch (key) {
            case "ENTER":
                normalColor = Color.parseColor("#2060a0");
                pressedColor = Color.parseColor("#3080c0");
                break;
            case "SHIFT":
                if (isShift || isCaps) {
                    normalColor = Color.parseColor("#4080a0");
                    pressedColor = Color.parseColor("#50a0c0");
                } else {
                    normalColor = Color.parseColor("#28284a");
                    pressedColor = Color.parseColor("#404068");
                }
                break;
            case "DEL":
            case "123":
            case "ABC":
                normalColor = Color.parseColor("#28284a");
                pressedColor = Color.parseColor("#404068");
                break;
            case "SPACE":
                normalColor = Color.parseColor("#404065");
                pressedColor = Color.parseColor("#505080");
                break;
            default:
                normalColor = Color.parseColor("#3d3d5c");
                pressedColor = Color.parseColor("#5a5a8c");
        }
        
        // Pressed state
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(dp(6));
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        
        // Normal state
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(6));
        states.addState(new int[]{}, normal);
        
        return states;
    }

    // ════════════════════════════════════════════════════════════════════
    // CLICK HANDLING
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onClick(View v) {
        String key = (String) v.getTag();
        if (key == null) return;
        
        vibrate();
        handleKey(key);
    }
    
    @Override
    public boolean onLongClick(View v) {
        String key = (String) v.getTag();
        if (key == null) return false;
        
        // Long press on DEL = delete word
        if (key.equals("DEL")) {
            vibrate();
            deleteWord();
            return true;
        }
        
        return false;
    }
    
    private void handleKey(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.w(TAG, "InputConnection is null!");
            return;
        }
        
        switch (key) {
            case "SHIFT":
                handleShift();
                break;
                
            case "DEL":
                ic.deleteSurroundingText(1, 0);
                break;
                
            case "ENTER":
                handleEnter(ic);
                break;
                
            case "SPACE":
                ic.commitText(" ", 1);
                autoUnshift();
                break;
                
            case "123":
                isNumbers = true;
                refreshKeyboard();
                break;
                
            case "ABC":
                isNumbers = false;
                refreshKeyboard();
                break;
                
            default:
                // Regular character
                String text = key;
                if ((isShift || isCaps) && key.length() == 1 && Character.isLetter(key.charAt(0))) {
                    text = key.toUpperCase();
                }
                ic.commitText(text, 1);
                autoUnshift();
        }
    }
    
    private void handleShift() {
        if (isCaps) {
            // Turn off caps lock
            isCaps = false;
            isShift = false;
        } else if (isShift) {
            // Double tap = caps lock
            isCaps = true;
        } else {
            // Single tap = shift
            isShift = true;
        }
        refreshKeyboard();
    }
    
    private void autoUnshift() {
        if (isShift && !isCaps) {
            isShift = false;
            refreshKeyboard();
        }
    }
    
    private void handleEnter(InputConnection ic) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            
            if (action == EditorInfo.IME_ACTION_NONE || 
                action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                // No specific action, send newline
                ic.commitText("\n", 1);
            } else {
                // Perform the action (Search, Done, Next, etc.)
                ic.performEditorAction(action);
            }
        } else {
            ic.commitText("\n", 1);
        }
    }
    
    private void deleteWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        
        // Get text before cursor
        CharSequence before = ic.getTextBeforeCursor(50, 0);
        if (before == null || before.length() == 0) return;
        
        // Find last space
        String text = before.toString();
        int lastSpace = text.lastIndexOf(' ');
        
        if (lastSpace == -1) {
            // No space, delete all
            ic.deleteSurroundingText(text.length(), 0);
        } else {
            // Delete from last space to end
            ic.deleteSurroundingText(text.length() - lastSpace - 1, 0);
        }
    }
    
    private void refreshKeyboard() {
        if (keyboardView != null) {
            keyboardView.removeAllViews();
            
            String[][] layout = isNumbers ? NUMBERS : LETTERS;
            for (int i = 0; i < layout.length; i++) {
                keyboardView.addView(createRow(layout[i], i));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════

    private void vibrate() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(3, 
                        VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(3);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    
    private View createErrorView(String error) {
        TextView tv = new TextView(this);
        tv.setText("❌ Error: " + error);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#b71c1c"));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(260)
        );
        tv.setLayoutParams(params);
        
        return tv;
    }

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE CALLBACKS
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d(TAG, "✓ onStartInputView() - inputType: " + info.inputType);
        
        // Reset state when opening
        isShift = false;
        isCaps = false;
        
        // Auto number mode for number inputs
        int inputClass = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        isNumbers = (inputClass == EditorInfo.TYPE_CLASS_NUMBER || 
                    inputClass == EditorInfo.TYPE_CLASS_PHONE);
        
        refreshKeyboard();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "✓ onDestroy()");
        keyboardView = null;
        vibrator = null;
        super.onDestroy();
    }
}