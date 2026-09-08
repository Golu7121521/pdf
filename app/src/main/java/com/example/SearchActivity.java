package com.example;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.PdfAdapter;
import com.example.db.PdfDatabaseHelper;
import com.example.model.PdfFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity implements PdfAdapter.OnPdfClickListener {

    private EditText editSearchQuery;
    private ImageView btnClearSearch;
    private RecyclerView rvSearchResults;
    private LinearLayout layoutEmptySearch;
    
    private PdfAdapter adapter;
    private PdfDatabaseHelper dbHelper;
    private List<PdfFile> allPdfFiles = new ArrayList<>();
    private List<PdfFile> filteredList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        dbHelper = new PdfDatabaseHelper(this);

        // Bind Views
        ImageView btnBack = findViewById(R.id.btn_back);
        editSearchQuery = findViewById(R.id.edit_search_query);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        rvSearchResults = findViewById(R.id.rv_search_results);
        layoutEmptySearch = findViewById(R.id.layout_empty_search);

        // Setup RecyclerView
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PdfAdapter(this);
        rvSearchResults.setAdapter(adapter);

        // Load Cached PDF files from DB
        allPdfFiles = dbHelper.getCachedPdfFiles();

        // Listeners
        btnBack.setOnClickListener(v -> finish());

        btnClearSearch.setOnClickListener(v -> editSearchQuery.setText(""));

        editSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    btnClearSearch.setVisibility(View.GONE);
                    filteredList.clear();
                    adapter.setPdfList(filteredList);
                    layoutEmptySearch.setVisibility(View.VISIBLE);
                } else {
                    btnClearSearch.setVisibility(View.VISIBLE);
                    performSearch(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Show empty initial search helper
        layoutEmptySearch.setVisibility(View.VISIBLE);
    }

    private void performSearch(String query) {
        filteredList.clear();
        for (PdfFile file : allPdfFiles) {
            if (file.getName().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(file);
            }
        }
        
        adapter.setPdfList(filteredList);
        
        if (filteredList.isEmpty()) {
            layoutEmptySearch.setVisibility(View.VISIBLE);
        } else {
            layoutEmptySearch.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPdfClick(PdfFile pdfFile) {
        // Open PDF Reader
        dbHelper.addRecent(pdfFile.getFilepath());
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra("FILE_PATH", pdfFile.getFilepath());
        startActivity(intent);
    }

    @Override
    public void onPdfOptionsClick(PdfFile pdfFile, View anchorView, int position) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.pdf_options_menu, popup.getMenu());

        // Update Favorite state menu text
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
                // Update file path and name
                dbHelper.deleteFromDatabase(pdfFile.getFilepath());
                
                pdfFile.setFilepath(newFile.getAbsolutePath());
                pdfFile.setName(newName);
                
                List<PdfFile> listToSave = new ArrayList<>();
                listToSave.add(pdfFile);
                dbHelper.cachePdfFiles(listToSave);
                
                adapter.notifyItemChanged(position);
                Toast.makeText(this, "File Renamed Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to rename file", Toast.LENGTH_SHORT).show();
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
                        filteredList.remove(position);
                        adapter.notifyItemRemoved(position);
                        Toast.makeText(this, "File Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to delete actual file. Check storage permission.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
