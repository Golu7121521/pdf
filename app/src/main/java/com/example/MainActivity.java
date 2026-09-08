package com.example;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.PdfAdapter;
import com.example.db.PdfDatabaseHelper;
import com.example.model.PdfFile;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PdfAdapter.OnPdfClickListener {

    private static final int REQ_STORAGE_PERMISSION = 100;
    private static final int REQ_MANAGE_STORAGE = 101;

    private BottomNavigationView bottomNav;
    private View contentContainer;
    private ImageView btnSearch;

    private PdfDatabaseHelper dbHelper;
    private ExecutorService scanExecutor;
    private SharedPreferences prefs;

    // Active View States (Views cache to prevent unnecessary inflations)
    private View homeView, favoriteView, recentView, settingsView;
    private PdfAdapter homeAdapter, favoriteAdapter, recentAdapter;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load Dark Mode Preference first to prevent screen flashing
        prefs = getSharedPreferences("pdf_reader_prefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_theme", false);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new PdfDatabaseHelper(this);
        scanExecutor = Executors.newSingleThreadExecutor();

        // Bind Main Views
        bottomNav = findViewById(R.id.bottom_navigation);
        contentContainer = findViewById(R.id.content_container);
        btnSearch = findViewById(R.id.btn_search);

        // Pre-inflate primary tab views
        inflateTabViews();

        // Listeners
        btnSearch.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showView(homeView);
                loadHomeData();
                return true;
            } else if (id == R.id.nav_favorite) {
                showView(favoriteView);
                loadFavoriteData();
                return true;
            } else if (id == R.id.nav_recent) {
                showView(recentView);
                loadRecentData();
                return true;
            } else if (id == R.id.nav_settings) {
                showView(settingsView);
                loadSettingsView();
                return true;
            }
            return false;
        });

        // App Launch: Check Storage Permissions & Dialog Prompt
        checkPermissionsAndScan();
    }

    private void inflateTabViews() {
        homeView = getLayoutInflater().inflate(R.layout.fragment_home, null);
        favoriteView = getLayoutInflater().inflate(R.layout.fragment_favorite, null);
        recentView = getLayoutInflater().inflate(R.layout.fragment_recent, null);
        settingsView = getLayoutInflater().inflate(R.layout.fragment_settings, null);

        // Setup Home RecyclerView
        RecyclerView rvHome = homeView.findViewById(R.id.rv_pdf_list);
        rvHome.setLayoutManager(new LinearLayoutManager(this));
        homeAdapter = new PdfAdapter(this);
        rvHome.setAdapter(homeAdapter);

        // Setup Favorite RecyclerView
        RecyclerView rvFav = favoriteView.findViewById(R.id.rv_favorite_list);
        rvFav.setLayoutManager(new LinearLayoutManager(this));
        favoriteAdapter = new PdfAdapter(this);
        rvFav.setAdapter(favoriteAdapter);

        // Setup Recent RecyclerView
        RecyclerView rvRecent = recentView.findViewById(R.id.rv_recent_list);
        rvRecent.setLayoutManager(new LinearLayoutManager(this));
        recentAdapter = new PdfAdapter(this);
        rvRecent.setAdapter(recentAdapter);
    }

    private void showView(View targetView) {
        contentContainer.setTag("content_view");
        ((android.view.ViewGroup) contentContainer).removeAllViews();
        ((android.view.ViewGroup) contentContainer).addView(targetView);
    }

    // --- TAB LOADING ACTIONS ---

    private void loadHomeData() {
        List<PdfFile> cached = dbHelper.getCachedPdfFiles();
        homeAdapter.setPdfList(cached);
        
        View emptyLayout = homeView.findViewById(R.id.layout_empty);
        ProgressBar progress = homeView.findViewById(R.id.scan_progress);
        
        if (isScanning) {
            progress.setVisibility(View.VISIBLE);
            emptyLayout.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.GONE);
            if (cached.isEmpty()) {
                emptyLayout.setVisibility(View.VISIBLE);
            } else {
                emptyLayout.setVisibility(View.GONE);
            }
        }
    }

    private void loadFavoriteData() {
        List<PdfFile> cachedAll = dbHelper.getCachedPdfFiles();
        List<PdfFile> favorites = new ArrayList<>();
        
        for (PdfFile f : cachedAll) {
            if (dbHelper.isFavorite(f.getFilepath())) {
                favorites.add(f);
            }
        }
        
        favoriteAdapter.setPdfList(favorites);
        
        View emptyLayout = favoriteView.findViewById(R.id.layout_empty);
        if (favorites.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
        } else {
            emptyLayout.setVisibility(View.GONE);
        }
    }

    private void loadRecentData() {
        List<PdfFile> cachedAll = dbHelper.getCachedPdfFiles();
        List<String> recentPaths = dbHelper.getRecents();
        List<PdfFile> recents = new ArrayList<>();
        
        for (String path : recentPaths) {
            for (PdfFile f : cachedAll) {
                if (f.getFilepath().equals(path)) {
                    recents.add(f);
                    break;
                }
            }
        }
        
        recentAdapter.setPdfList(recents);
        
        View emptyLayout = recentView.findViewById(R.id.layout_empty);
        if (recents.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
        } else {
            emptyLayout.setVisibility(View.GONE);
        }
    }

    private void loadSettingsView() {
        SwitchCompat switchTheme = settingsView.findViewById(R.id.switch_theme);
        LinearLayout btnTheme = settingsView.findViewById(R.id.btn_theme_setting);
        LinearLayout btnRescan = settingsView.findViewById(R.id.btn_rescan_setting);
        
        ImageView imgVersion = settingsView.findViewById(R.id.img_version);
        if (imgVersion != null) {
            imgVersion.setImageResource(R.drawable.ic_settings);
            imgVersion.setContentDescription("Version settings");
        }

        switchTheme.setChecked(prefs.getBoolean("dark_theme", false));

        btnTheme.setOnClickListener(v -> {
            boolean current = switchTheme.isChecked();
            boolean newVal = !current;
            switchTheme.setChecked(newVal);
            prefs.edit().putBoolean("dark_theme", newVal).apply();
            
            // Re-apply and recreate theme
            AppCompatDelegate.setDefaultNightMode(newVal ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
        });

        btnRescan.setOnClickListener(v -> {
            Toast.makeText(this, "Scanning started...", Toast.LENGTH_SHORT).show();
            startStorageScan();
        });
    }

    // --- PERMISSIONS MANAGEMENT ---

    private void checkPermissionsAndScan() {
        if (hasStoragePermission()) {
            // Permission already granted, load from cache or trigger initial scan
            showView(homeView);
            List<PdfFile> cached = dbHelper.getCachedPdfFiles();
            if (cached.isEmpty()) {
                startStorageScan();
            } else {
                loadHomeData();
            }
        } else {
            // Display permission dialog first time (as requested!)
            showPermissionExplanationDialog();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_allow, (dialog, which) -> requestStoragePermission())
                .setNegativeButton(R.string.btn_exit, (dialog, which) -> finish())
                .show();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, REQ_MANAGE_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQ_MANAGE_STORAGE);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showView(homeView);
                startStorageScan();
            } else {
                Toast.makeText(this, "Permission Denied. Closing app.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE_STORAGE) {
            if (hasStoragePermission()) {
                showView(homeView);
                startStorageScan();
            } else {
                Toast.makeText(this, "All Files access is required to scan PDFs. Closing app.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // --- RECURSIVE FILE SCANNING ---

    private void startStorageScan() {
        if (isScanning) return;
        isScanning = true;

        ProgressBar progress = homeView.findViewById(R.id.scan_progress);
        View emptyLayout = homeView.findViewById(R.id.layout_empty);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (emptyLayout != null) emptyLayout.setVisibility(View.GONE);

        scanExecutor.execute(() -> {
            List<PdfFile> filesFound = new ArrayList<>();
            File root = Environment.getExternalStorageDirectory();
            scanDirectory(root, filesFound);

            // Save results to Cache DB
            dbHelper.clearCache();
            dbHelper.cachePdfFiles(filesFound);

            new Handler(Looper.getMainLooper()).post(() -> {
                isScanning = false;
                if (bottomNav.getSelectedItemId() == R.id.nav_home) {
                    loadHomeData();
                }
                Toast.makeText(MainActivity.this, "Scan complete! Found " + filesFound.size() + " PDFs.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void scanDirectory(File dir, List<PdfFile> list) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                // Exclude hidden folders, system logs, cache and standard heavy media directories to speed up search
                if (name.startsWith(".") || name.equalsIgnoreCase("Android") || name.equalsIgnoreCase("obb")) {
                    continue;
                }
                scanDirectory(file, list);
            } else {
                if (file.getName().toLowerCase().endsWith(".pdf")) {
                    list.add(new PdfFile(file));
                }
            }
        }
    }

    // --- RECYCLERVIEW INTERACTION LISTENERS ---

    @Override
    public void onPdfClick(PdfFile pdfFile) {
        // Add to Recent PDF Database list
        dbHelper.addRecent(pdfFile.getFilepath());

        // Launch full-screen immersive PDF Reader
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra("FILE_PATH", pdfFile.getFilepath());
        startActivity(intent);
    }

    @Override
    public void onPdfOptionsClick(PdfFile pdfFile, View anchorView, int position) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.pdf_options_menu, popup.getMenu());

        // Update Toggle text dynamically
        boolean isFav = dbHelper.isFavorite(pdfFile.getFilepath());
        popup.getMenu().findItem(R.id.action_favorite).setTitle(
                isFav ? R.string.menu_unfavorite : R.string.menu_favorite
        );

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_favorite) {
                if (isFav) {
                    dbHelper.removeFavorite(pdfFile.getFilepath());
                    Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.addFavorite(pdfFile.getFilepath());
                    Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show();
                }
                // Update active list context
                refreshActiveTab();
                return true;
            } else if (itemId == R.id.action_rename) {
                showRenameDialog(pdfFile, position);
                return true;
            } else if (itemId == R.id.action_share) {
                sharePdfFile(pdfFile);
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteDialog(pdfFile, position);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void refreshActiveTab() {
        int currentId = bottomNav.getSelectedItemId();
        if (currentId == R.id.nav_home) {
            loadHomeData();
        } else if (currentId == R.id.nav_favorite) {
            loadFavoriteData();
        } else if (currentId == R.id.nav_recent) {
            loadRecentData();
        }
    }

    private void sharePdfFile(PdfFile pdfFile) {
        try {
            File file = new File(pdfFile.getFilepath());
            if (!file.exists()) {
                Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share PDF using"));
        } catch (Exception e) {
            Toast.makeText(this, "Sharing failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(PdfFile pdfFile, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename File");

        final EditText input = new EditText(this);
        input.setText(pdfFile.getName());
        input.setSelectAllOnFocus(true);
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newName.endsWith(".pdf")) {
                newName += ".pdf";
            }

            File oldFile = new File(pdfFile.getFilepath());
            File parentDir = oldFile.getParentFile();
            File newFile = new File(parentDir, newName);

            if (oldFile.renameTo(newFile)) {
                dbHelper.deleteFromDatabase(pdfFile.getFilepath());
                
                pdfFile.setFilepath(newFile.getAbsolutePath());
                pdfFile.setName(newName);
                
                List<PdfFile> listToSave = new ArrayList<>();
                listToSave.add(pdfFile);
                dbHelper.cachePdfFiles(listToSave);
                
                refreshActiveTab();
                Toast.makeText(this, "File Renamed Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to rename file. Check permissions.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteDialog(PdfFile pdfFile, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete PDF")
                .setMessage("Are you sure you want to delete this PDF file?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    File file = new File(pdfFile.getFilepath());
                    if (file.exists() && file.delete()) {
                        dbHelper.deleteFromDatabase(pdfFile.getFilepath());
                        refreshActiveTab();
                        Toast.makeText(this, "File Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to delete actual file. Check storage permission.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Automatically sync UI on tab resume to catch any changes from search action
        refreshActiveTab();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanExecutor != null) {
            scanExecutor.shutdown();
        }
    }
}
