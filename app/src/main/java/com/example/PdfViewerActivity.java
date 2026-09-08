package com.example;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;

public class PdfViewerActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loader;
    private String pdfFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge full bleed
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Hide standard bars
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Allow drawing under display cutout (notch area) on Android P and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Hide navigation bars as well for absolute full screen immersion
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_pdf_viewer);

        pdfFilePath = getIntent().getStringExtra("FILE_PATH");
        if (pdfFilePath == null || pdfFilePath.isEmpty()) {
            Toast.makeText(this, "Error: PDF file path is empty", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        webView = findViewById(R.id.pdf_webview);
        loader = findViewById(R.id.pdf_loader);

        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDomStorageEnabled(true);
        
        // Enable built-in zoom controls
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // Hide the ugly onscreen zoom buttons
        
        // Allow cross-origin requests for file:// scheme
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loader.setVisibility(View.GONE);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                UriMatch: {
                    android.net.Uri uri = request.getUrl();
                    if (uri != null && "localpdf".equals(uri.getHost())) {
                        try {
                            File file = new File(pdfFilePath);
                            if (file.exists()) {
                                FileInputStream fis = new FileInputStream(file);
                                return new WebResourceResponse("application/pdf", "UTF-8", fis);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        // Load PDF.js viewer with our mocked URL pointing to the local file
        // This completely bypasses file-system CORS restrictions!
        String viewerUrl = "file:///android_asset/pdfjs/web/viewer.html?file=" + 
                android.net.Uri.encode("https://localpdf/file.pdf");
        webView.loadUrl(viewerUrl);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
