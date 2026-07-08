package com.kinetic.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AquaDevs API client. Handles image generation, prompt enhancement,
 * auto-naming, and web search. All calls run on background threads.
 */
public class KineticApi {
    private static final String TAG = "KineticApi";
    private static final String BASE = "https://api.aquadevs.com";

    // --- Callbacks ---
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    // --- Image Generation ---
    public static void generateImage(String apiKey, String model, String prompt,
                                      String ratio, String refImageBase64,
                                      ApiCallback<Bitmap> callback) {
        new AsyncTask<Void, Void, Object>() {
            @Override
            protected Object doInBackground(Void... v) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("prompt", prompt);
                    body.put("n", 1);

                    // Map ratio
                    String size = mapRatio(ratio);
                    body.put("size", size);

                    if (refImageBase64 != null && !refImageBase64.isEmpty()) {
                        body.put("image", refImageBase64);
                    }

                    String resp = post("/v1/images/generations", apiKey, body);
                    JSONObject json = new JSONObject(resp);

                    // Handle response - could be URL or base64
                    if (json.has("data")) {
                        JSONArray data = json.getJSONArray("data");
                        if (data.length() > 0) {
                            JSONObject item = data.getJSONObject(0);
                            if (item.has("url")) {
                                String url = item.getString("url");
                                return downloadBitmap(url);
                            } else if (item.has("b64_json")) {
                                String b64 = item.getString("b64_json");
                                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            }
                        }
                    }

                    // Handle polling for models that return task_id
                    if (json.has("task_id")) {
                        String taskId = json.getString("task_id");
                        return pollTask(apiKey, taskId);
                    }

                    return "No image data in response";
                } catch (Exception e) {
                    Log.e(TAG, "generateImage error", e);
                    return e.getMessage() != null ? e.getMessage() : "Unknown error";
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void onPostExecute(Object result) {
                if (result instanceof Bitmap) {
                    callback.onSuccess((Bitmap) result);
                } else {
                    callback.onError(result != null ? result.toString() : "Unknown error");
                }
            }
        }.execute();
    }

    // --- Polling for async generation ---
    private static Bitmap pollTask(String apiKey, String taskId) throws Exception {
        for (int i = 0; i < 60; i++) { // max 5 minutes
            Thread.sleep(5000);
            String resp = get("/v1/images/tasks/" + taskId, apiKey);
            JSONObject json = new JSONObject(resp);
            String status = json.optString("status", "");
            if ("completed".equals(status) || "succeeded".equals(status)) {
                if (json.has("data")) {
                    JSONArray data = json.getJSONArray("data");
                    if (data.length() > 0) {
                        JSONObject item = data.getJSONObject(0);
                        if (item.has("url")) {
                            return downloadBitmap(item.getString("url"));
                        } else if (item.has("b64_json")) {
                            byte[] bytes = Base64.decode(item.getString("b64_json"), Base64.DEFAULT);
                            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        }
                    }
                }
                break;
            } else if ("failed".equals(status) || "error".equals(status)) {
                throw new Exception("Generation failed: " + json.optString("error", "Unknown"));
            }
        }
        throw new Exception("Generation timed out");
    }

    // --- Prompt Enhancement ---
    public static void enhancePrompt(String apiKey, String prompt, String model,
                                      boolean webSearch, ApiCallback<String> callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... v) {
                try {
                    String searchContext = "";
                    if (webSearch) {
                        searchContext = webSearch(apiKey, prompt);
                    }

                    String systemPrompt = "You are an expert image prompt engineer. Given a user prompt, rewrite it into a more detailed, vivid, and descriptive version optimized for AI image generation. Keep it under 300 characters. Return ONLY the enhanced prompt, nothing else.";

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 400);
                    body.put("temperature", 0.7);

                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", systemPrompt);
                    messages.put(sys);

                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    String userContent = prompt;
                    if (!searchContext.isEmpty()) {
                        userContent += "\n\nWeb search results:\n" + searchContext;
                    }
                    user.put("content", userContent);
                    messages.put(user);

                    body.put("messages", messages);

                    String resp = post("/v1/chat/completions", apiKey, body);
                    JSONObject json = new JSONObject(resp);
                    return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
                } catch (Exception e) {
                    Log.e(TAG, "enhancePrompt error", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    callback.onSuccess(result);
                } else {
                    callback.onError("Enhancement failed");
                }
            }
        }.execute();
    }

    // --- Auto-Naming ---
    public static void autoName(String apiKey, String prompt, String model,
                                 ApiCallback<String> callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... v) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 40);
                    body.put("temperature", 0.5);

                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", "Generate a short, catchy title (3-6 words) for an image based on the prompt. Return ONLY the title, nothing else. No quotes.");
                    messages.put(sys);

                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", prompt);
                    messages.put(user);

                    body.put("messages", messages);

                    String resp = post("/v1/chat/completions", apiKey, body);
                    JSONObject json = new JSONObject(resp);
                    return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .replaceAll("[\"']", "")
                        .trim();
                } catch (Exception e) {
                    return prompt.length() > 30 ? prompt.substring(0, 30) : prompt;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                callback.onSuccess(result);
            }
        }.execute();
    }

    // --- Web Search ---
    private static String webSearch(String apiKey, String query) {
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("depth", "fast");
            String resp = post("/v1/search", apiKey, body);
            JSONObject json = new JSONObject(resp);
            if (json.has("results")) {
                JSONArray results = json.getJSONArray("results");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    sb.append(r.optString("title", ""))
                      .append(": ")
                      .append(r.optString("snippet", r.optString("description", "")))
                      .append("\n");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "webSearch failed", e);
        }
        return "";
    }

    // --- Remix (using chat completions with reference image) ---
    public static void remixImage(String apiKey, String prompt, String model,
                                   String refImageBase64, ApiCallback<String> callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... v) {
                try {
                    String systemPrompt = "You are an image transformation specialist. Given a reference image and a transformation request, generate a detailed prompt describing the desired output. Return ONLY the prompt.";

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 400);
                    body.put("temperature", 0.7);

                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", systemPrompt);
                    messages.put(sys);

                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    JSONArray content = new JSONArray();
                    if (refImageBase64 != null) {
                        JSONObject img = new JSONObject();
                        img.put("type", "image_url");
                        JSONObject url = new JSONObject();
                        url.put("url", "data:image/jpeg;base64," + refImageBase64);
                        img.put("image_url", url);
                        content.put(img);
                    }
                    JSONObject txt = new JSONObject();
                    txt.put("type", "text");
                    txt.put("text", prompt);
                    content.put(txt);
                    user.put("content", content);
                    messages.put(user);

                    body.put("messages", messages);

                    String resp = post("/v1/chat/completions", apiKey, body);
                    JSONObject json = new JSONObject(resp);
                    return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) callback.onSuccess(result);
                else callback.onError("Remix failed");
            }
        }.execute();
    }

    // --- Helpers ---
    private static String mapRatio(String ratio) {
        if (ratio == null) return "1024x1024";
        switch (ratio) {
            case "square": return "1024x1024";
            case "portrait": return "768x1024";
            case "landscape": return "1024x768";
            default: return "1024x1024";
        }
    }

    private static String post(String endpoint, String apiKey, JSONObject body) throws Exception {
        URL url = new URL(BASE + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(data.length);
        OutputStream os = conn.getOutputStream();
        os.write(data);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            String errMsg = sb.toString();
            try {
                JSONObject err = new JSONObject(errMsg);
                errMsg = err.optJSONObject("error") != null
                    ? err.getJSONObject("error").optString("message", "API error " + code)
                    : "API error " + code;
            } catch (Exception e) {
                errMsg = "API error " + code;
            }
            throw new Exception(errMsg);
        }

        return sb.toString();
    }

    private static String get(String endpoint, String apiKey) throws Exception {
        URL url = new URL(BASE + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static Bitmap downloadBitmap(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        InputStream is = conn.getInputStream();
        Bitmap bmp = BitmapFactory.decodeStream(is);
        is.close();
        conn.disconnect();
        return bmp;
    }
}
