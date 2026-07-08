package com.kinetic.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * SQLite database for storing generated image metadata.
 * Actual image files are stored in internal storage.
 */
public class KineticDB extends SQLiteOpenHelper {
    private static final String DB_NAME = "kinetic.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "images";

    public KineticDB(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "prompt TEXT," +
            "original_prompt TEXT," +
            "name TEXT," +
            "model TEXT," +
            "ratio TEXT," +
            "style TEXT," +
            "file_path TEXT," +
            "favorite INTEGER DEFAULT 0," +
            "batch_id TEXT," +
            "timestamp INTEGER" +
        ")");
        db.execSQL("CREATE INDEX idx_batch ON " + TABLE + "(batch_id)");
        db.execSQL("CREATE INDEX idx_ts ON " + TABLE + "(timestamp DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(ImageEntry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("prompt", e.prompt);
        cv.put("original_prompt", e.originalPrompt);
        cv.put("name", e.name);
        cv.put("model", e.model);
        cv.put("ratio", e.ratio);
        cv.put("style", e.style);
        cv.put("file_path", e.filePath);
        cv.put("favorite", e.favorite ? 1 : 0);
        cv.put("batch_id", e.batchId);
        cv.put("timestamp", e.timestamp);
        long id = db.insert(TABLE, null, cv);
        e.id = id;
        return id;
    }

    public List<ImageEntry> getAll() {
        List<ImageEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            list.add(cursorToEntry(c));
        }
        c.close();
        return list;
    }

    public List<ImageEntry> getFavorites() {
        List<ImageEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "favorite=1", null, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            list.add(cursorToEntry(c));
        }
        c.close();
        return list;
    }

    public void toggleFavorite(long id, boolean fav) {
        ContentValues cv = new ContentValues();
        cv.put("favorite", fav ? 1 : 0);
        getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateName(long id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    private ImageEntry cursorToEntry(Cursor c) {
        ImageEntry e = new ImageEntry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.prompt = c.getString(c.getColumnIndexOrThrow("prompt"));
        e.originalPrompt = c.getString(c.getColumnIndexOrThrow("original_prompt"));
        e.name = c.getString(c.getColumnIndexOrThrow("name"));
        e.model = c.getString(c.getColumnIndexOrThrow("model"));
        e.ratio = c.getString(c.getColumnIndexOrThrow("ratio"));
        e.style = c.getString(c.getColumnIndexOrThrow("style"));
        e.filePath = c.getString(c.getColumnIndexOrThrow("file_path"));
        e.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) == 1;
        e.batchId = c.getString(c.getColumnIndexOrThrow("batch_id"));
        e.timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"));
        return e;
    }

    /**
     * Image entry stored in the database.
     */
    public static class ImageEntry {
        public long id;
        public String prompt;
        public String originalPrompt;
        public String name;
        public String model;
        public String ratio;
        public String style;
        public String filePath;
        public boolean favorite;
        public String batchId;
        public long timestamp;
    }
}
