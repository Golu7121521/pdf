package com.example.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.model.PdfFile;

import java.util.ArrayList;
import java.util.List;

public class PdfDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "pdf_reader.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_RECENTS = "recents";
    private static final String TABLE_CACHE = "scanned_cache";

    // Column Names
    private static final String KEY_FILEPATH = "filepath";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_NAME = "name";
    private static final String KEY_SIZE = "size";
    private static final String KEY_DATE = "date_modified";

    public PdfDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Favorites Table
        String createFavoritesTable = "CREATE TABLE " + TABLE_FAVORITES + " (" +
                KEY_FILEPATH + " TEXT PRIMARY KEY)";
        db.execSQL(createFavoritesTable);

        // Recents Table
        String createRecentsTable = "CREATE TABLE " + TABLE_RECENTS + " (" +
                KEY_FILEPATH + " TEXT PRIMARY KEY, " +
                KEY_TIMESTAMP + " INTEGER)";
        db.execSQL(createRecentsTable);

        // Cache Table
        String createCacheTable = "CREATE TABLE " + TABLE_CACHE + " (" +
                KEY_FILEPATH + " TEXT PRIMARY KEY, " +
                KEY_NAME + " TEXT, " +
                KEY_SIZE + " INTEGER, " +
                KEY_DATE + " INTEGER)";
        db.execSQL(createCacheTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CACHE);
        onCreate(db);
    }

    // --- FAVORITES OPERATIONS ---

    public boolean addFavorite(String filepath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_FILEPATH, filepath);
        long id = db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return id != -1;
    }

    public boolean removeFavorite(String filepath) {
        SQLiteDatabase db = this.getWritableDatabase();
        int affected = db.delete(TABLE_FAVORITES, KEY_FILEPATH + " = ?", new String[]{filepath});
        return affected > 0;
    }

    public boolean isFavorite(String filepath) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{KEY_FILEPATH}, KEY_FILEPATH + " = ?",
                new String[]{filepath}, null, null, null);
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) {
            cursor.close();
        }
        return exists;
    }

    public List<String> getFavorites() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{KEY_FILEPATH}, null, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    // --- RECENTS OPERATIONS ---

    public void addRecent(String filepath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_FILEPATH, filepath);
        values.put(KEY_TIMESTAMP, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_RECENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<String> getRecents() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // Order by timestamp descending
        Cursor cursor = db.query(TABLE_RECENTS, new String[]{KEY_FILEPATH}, null, null, null, null, KEY_TIMESTAMP + " DESC");
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    public void removeRecent(String filepath) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECENTS, KEY_FILEPATH + " = ?", new String[]{filepath});
    }

    // --- CACHE OPERATIONS ---

    public void cachePdfFiles(List<PdfFile> pdfFiles) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (PdfFile file : pdfFiles) {
                ContentValues values = new ContentValues();
                values.put(KEY_FILEPATH, file.getFilepath());
                values.put(KEY_NAME, file.getName());
                values.put(KEY_SIZE, file.getSize());
                values.put(KEY_DATE, file.getDateModified());
                db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<PdfFile> getCachedPdfFiles() {
        List<PdfFile> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CACHE, null, null, null, null, null, KEY_NAME + " COLLATE NOCASE ASC");
        if (cursor != null) {
            int pathIdx = cursor.getColumnIndex(KEY_FILEPATH);
            int nameIdx = cursor.getColumnIndex(KEY_NAME);
            int sizeIdx = cursor.getColumnIndex(KEY_SIZE);
            int dateIdx = cursor.getColumnIndex(KEY_DATE);
            
            if (cursor.moveToFirst()) {
                do {
                    list.add(new PdfFile(
                            cursor.getString(pathIdx),
                            cursor.getString(nameIdx),
                            cursor.getLong(sizeIdx),
                            cursor.getLong(dateIdx)
                    ));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    public void clearCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CACHE, null, null);
    }
    
    public void deleteFromDatabase(String filepath) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CACHE, KEY_FILEPATH + " = ?", new String[]{filepath});
        db.delete(TABLE_FAVORITES, KEY_FILEPATH + " = ?", new String[]{filepath});
        db.delete(TABLE_RECENTS, KEY_FILEPATH + " = ?", new String[]{filepath});
    }
}
