package com.example.util;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.widget.ImageView;

import com.example.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailLoader {

    private static ThumbnailLoader instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ThumbnailLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8; // Use 1/8th of available memory for cache
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static synchronized ThumbnailLoader getInstance() {
        if (instance == null) {
            instance = new ThumbnailLoader();
        }
        return instance;
    }

    public void loadThumbnail(final String filePath, final ImageView imageView) {
        imageView.setTag(filePath);
        Bitmap cached = memoryCache.get(filePath);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set fallback vector icon first
        imageView.setImageResource(R.drawable.ic_pdf);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = renderFirstPage(filePath);
                if (bitmap != null) {
                    memoryCache.put(filePath, bitmap);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (imageView.getTag() != null && imageView.getTag().equals(filePath)) {
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    });
                }
            }
        });
    }

    private Bitmap renderFirstPage(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
            
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (pfd != null) {
                PdfRenderer renderer = new PdfRenderer(pfd);
                if (renderer.getPageCount() > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);

                    // Keep thumbnail dimensions small but sharp (e.g. 150x200 dp ratio)
                    int width = 120;
                    int height = 160;
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    
                    // Fill background with white since PDF page might be transparent
                    bitmap.eraseColor(android.graphics.Color.WHITE);

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    renderer.close();
                    pfd.close();
                    return bitmap;
                }
                renderer.close();
                pfd.close();
            }
        } catch (Throwable e) {
            // Silently fail if PDF is encrypted or corrupted
        }
        return null;
    }
}
