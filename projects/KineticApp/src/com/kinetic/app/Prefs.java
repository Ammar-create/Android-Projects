package com.kinetic.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * SharedPreferences wrapper for all Kinetic app settings.
 */
public class Prefs {
    private static final String PREFS = "kinetic_prefs";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_AVATAR = "avatar_data";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SETUP = "setup_complete";
    private static final String KEY_THEME = "theme";
    private static final String KEY_ANIMATIONS = "animations";
    private static final String KEY_DEFAULT_MODEL = "default_model";
    private static final String KEY_ENHANCE_ENABLED = "enhance_enabled";
    private static final String KEY_ENHANCE_WEB = "enhance_web";
    private static final String KEY_NAMING_ENABLED = "naming_enabled";
    private static final String KEY_CUSTOM_MODELS = "custom_models";
    private static final String KEY_RECENT_LIMIT = "recent_limit";
    private static final String KEY_SAVE_IMAGES = "save_images";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- Setup ---
    public boolean isSetupComplete() { return sp.getBoolean(KEY_SETUP, false); }
    public void setSetupComplete(boolean v) { sp.edit().putBoolean(KEY_SETUP, v).apply(); }

    // --- Profile ---
    public String getName() { return sp.getString(KEY_NAME, ""); }
    public void setName(String v) { sp.edit().putString(KEY_NAME, v).apply(); }
    public String getAvatar() { return sp.getString(KEY_AVATAR, null); }
    public void setAvatar(String v) { sp.edit().putString(KEY_AVATAR, v).apply(); }

    // --- API ---
    public String getApiKey() { return sp.getString(KEY_API_KEY, ""); }
    public void setApiKey(String v) { sp.edit().putString(KEY_API_KEY, v).apply(); }

    // --- Theme ---
    public String getTheme() { return sp.getString(KEY_THEME, "orange"); }
    public void setTheme(String v) { sp.edit().putString(KEY_THEME, v).apply(); }
    public boolean getAnimations() { return sp.getBoolean(KEY_ANIMATIONS, true); }
    public void setAnimations(boolean v) { sp.edit().putBoolean(KEY_ANIMATIONS, v).apply(); }

    // --- Model ---
    public String getDefaultModel() { return sp.getString(KEY_DEFAULT_MODEL, "gptimage-2"); }
    public void setDefaultModel(String v) { sp.edit().putString(KEY_DEFAULT_MODEL, v).apply(); }

    // --- Enhancement ---
    public boolean getEnhanceEnabled() { return sp.getBoolean(KEY_ENHANCE_ENABLED, false); }
    public void setEnhanceEnabled(boolean v) { sp.edit().putBoolean(KEY_ENHANCE_ENABLED, v).apply(); }
    public boolean getEnhanceWeb() { return sp.getBoolean(KEY_ENHANCE_WEB, false); }
    public void setEnhanceWeb(boolean v) { sp.edit().putBoolean(KEY_ENHANCE_WEB, v).apply(); }
    public boolean getNamingEnabled() { return sp.getBoolean(KEY_NAMING_ENABLED, false); }
    public void setNamingEnabled(boolean v) { sp.edit().putBoolean(KEY_NAMING_ENABLED, v).apply(); }

    // --- Storage ---
    public boolean getSaveImages() { return sp.getBoolean(KEY_SAVE_IMAGES, true); }
    public void setSaveImages(boolean v) { sp.edit().putBoolean(KEY_SAVE_IMAGES, v).apply(); }

    // --- Recent Limit ---
    public int getRecentLimit() { return sp.getInt(KEY_RECENT_LIMIT, 30); }
    public void setRecentLimit(int v) { sp.edit().putInt(KEY_RECENT_LIMIT, v).apply(); }

    // --- Custom Models (JSON array of {id, name, refImages}) ---
    public List<ModelItem> getCustomModels() {
        List<ModelItem> list = new ArrayList<>();
        String json = sp.getString(KEY_CUSTOM_MODELS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new ModelItem(
                    o.getString("id"),
                    o.getString("name"),
                    o.optBoolean("refImages", false),
                    true // custom = true
                ));
            }
        } catch (Exception e) { /* ignore */ }
        return list;
    }

    public void setCustomModels(List<ModelItem> models) {
        JSONArray arr = new JSONArray();
        for (ModelItem m : models) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("name", m.name);
                o.put("refImages", m.refImages);
                arr.put(o);
            } catch (Exception e) { /* ignore */ }
        }
        sp.edit().putString(KEY_CUSTOM_MODELS, arr.toString()).apply();
    }

    /**
     * Represents a model in the app.
     */
    public static class ModelItem {
        public final String id;
        public final String name;
        public final boolean refImages;
        public final boolean custom;

        public ModelItem(String id, String name, boolean refImages, boolean custom) {
            this.id = id;
            this.name = name;
            this.refImages = refImages;
            this.custom = custom;
        }
    }
}
