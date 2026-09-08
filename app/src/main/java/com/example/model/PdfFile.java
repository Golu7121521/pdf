package com.example.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfFile {
    private String filepath;
    private String name;
    private long size;
    private long dateModified;

    public PdfFile(String filepath, String name, long size, long dateModified) {
        this.filepath = filepath;
        this.name = name;
        this.size = size;
        this.dateModified = dateModified;
    }

    public PdfFile(File file) {
        this.filepath = file.getAbsolutePath();
        this.name = file.getName();
        this.size = file.length();
        this.dateModified = file.lastModified();
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
        this.name = new File(filepath).getName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getDateModified() {
        return dateModified;
    }

    public void setDateModified(long dateModified) {
        this.dateModified = dateModified;
    }

    public String getFormattedSize() {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(dateModified));
    }
}
