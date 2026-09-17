package com.asdfstudio.vietfloat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "vietfloat";
    static final String KEY_API = "api_key";
    static final String KEY_MODEL = "model";
    private EditText apiKeyEdit;
    private EditText modelEdit;
    private TextView statusText;
    private TextView testText;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("VietFloat", 30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        root.addView(text("Zalo 화면의 베트남어를 자동 감지해 한국어로 표시합니다.", 15));

        statusText = text("접근성 서비스 확인 중...", 16);
        statusText.setPadding(0, dp(24), 0, dp(8));
        root.addView(statusText);

        Button accessibility = new Button(this);
        accessibility.setText("접근성 설정 열기");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        root.addView(text("OpenAI API 키", 14));
        apiKeyEdit = new EditText(this);
        apiKeyEdit.setHint("sk-...");
        apiKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(apiKeyEdit, matchWrap());

        root.addView(text("모델", 14));
        modelEdit = new EditText(this);
        modelEdit.setSingleLine(true);
        modelEdit.setText(prefs().getString(KEY_MODEL, "gpt-5-mini"));
        root.addView(modelEdit, matchWrap());

        Button save = new Button(this);
        save.setText("저장");
        save.setOnClickListener(v -> saveSettings(true));
        root.addView(save);

        Button test = new Button(this);
        test.setText("API 번역 테스트");
        test.setOnClickListener(v -> runTest());
        root.addView(test);

        testText = text("", 14);
        root.addView(testText);
        root.addView(text("사용 방법\n1. API 키 입력 후 저장\n2. 접근성 설정에서 ‘VietFloat 화면 번역’ 켜기\n3. Zalo를 열면 새 베트남어 메시지를 감지\n4. 화면 상단에 한국어 번역 표시", 14));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void saveSettings(boolean toast) {
        String key = apiKeyEdit.getText().toString().trim();
        String model = modelEdit.getText().toString().trim();
        SharedPreferences.Editor e = prefs().edit();
        if (!key.isEmpty()) e.putString(KEY_API, key);
        e.putString(KEY_MODEL, model.isEmpty() ? "gpt-5-mini" : model).apply();
        if (!key.isEmpty()) apiKeyEdit.setText("");
        if (toast) Toast.makeText(this, "저장했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void runTest() {
        saveSettings(false);
        String key = prefs().getString(KEY_API, "");
        if (key.isEmpty()) {
            testText.setText("API 키를 먼저 입력해 주세요.");
            return;
        }
        testText.setText("테스트 번역 중...");
        String model = prefs().getString(KEY_MODEL, "gpt-5-mini");
        executor.submit(() -> {
            try {
                String result = OpenAiTranslator.translate(key, model, "1 tiếng được 0.22 em");
                runOnUiThread(() -> testText.setText("성공: " + result));
            } catch (Exception ex) {
                runOnUiThread(() -> testText.setText("실패: " + ex.getMessage()));
            }
        });
    }

    private void refreshStatus() {
        String expected = new ComponentName(this, TranslationAccessibilityService.class).flattenToString();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = false;
        if (!TextUtils.isEmpty(enabled)) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabled);
            for (String s : splitter) if (expected.equalsIgnoreCase(s)) on = true;
        }
        statusText.setText(on ? "● 접근성 번역 서비스 켜짐" : "○ 접근성 번역 서비스 꺼짐");
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private TextView text(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
