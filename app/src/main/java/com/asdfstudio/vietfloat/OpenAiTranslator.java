package com.asdfstudio.vietfloat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class OpenAiTranslator {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";

    private OpenAiTranslator() {}

    public static String translate(String apiKey, String model, String vietnamese) throws Exception {
        JSONObject request = new JSONObject();
        request.put("model", model == null || model.trim().isEmpty() ? "gpt-5-mini" : model.trim());
        request.put("store", false);
        request.put("max_output_tokens", 220);
        request.put("instructions", "Translate Vietnamese chat messages into natural Korean. Preserve names, numbers, percentages, game terms and emojis. Output only the Korean translation. If the input is not Vietnamese, output exactly __SKIP__.");
        request.put("input", vietnamese);

        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) { out.write(payload); }

        int status = c.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(stream);
        if (status < 200 || status >= 300) throw new IllegalStateException("OpenAI API 오류 " + status + ": " + extractError(body));

        JSONObject response = new JSONObject(body);
        String top = response.optString("output_text", "").trim();
        if (!top.isEmpty()) return top;
        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part != null && "output_text".equals(part.optString("type"))) {
                        String text = part.optString("text", "").trim();
                        if (!text.isEmpty()) return text;
                    }
                }
            }
        }
        throw new IllegalStateException("번역 응답에서 텍스트를 찾지 못했습니다.");
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    private static String extractError(String body) {
        try {
            JSONObject j = new JSONObject(body);
            JSONObject e = j.optJSONObject("error");
            if (e != null) return e.optString("message", body);
        } catch (Exception ignored) {}
        return body == null || body.isEmpty() ? "알 수 없는 오류" : body;
    }
}
