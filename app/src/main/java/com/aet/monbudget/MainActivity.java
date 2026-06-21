package com.aet.monbudget;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * AET MonBudget - Activité principale.
 * Charge l'application web (HTML/JS/Firebase) dans une WebView native,
 * sans aucune marque ou watermark tiers.
 */
public class MainActivity extends AppCompatActivity {

    // Adresse de l'app web hébergée (GitHub Pages).
    // Remplace cette URL par celle de ton site si elle change.
    private static final String APP_URL = "https://alban3886.github.io/togosheets-pro/";

    // Code utilisé pour identifier la réponse de la demande de permission caméra
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    // Mémorise la demande de permission caméra faite par la page web (WebView),
    // pour pouvoir y répondre une fois que l'utilisateur a répondu au popup Android.
    private PermissionRequest pendingWebPermissionRequest;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout loadingScreen;
    private LinearLayout offlineScreen;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        loadingScreen = findViewById(R.id.loadingScreen);
        offlineScreen = findViewById(R.id.offlineScreen);
        Button retryButton = findViewById(R.id.retryButton);

        setupWebView();

        retryButton.setOnClickListener(v -> {
            offlineScreen.setVisibility(View.GONE);
            loadingScreen.setVisibility(View.VISIBLE);
            webView.loadUrl(APP_URL);
        });

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        webView.loadUrl(APP_URL);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            // Appelée quand la page web (getUserMedia) demande l'accès à la caméra/micro.
            // Sans ceci, la WebView refuse systématiquement, même si la permission
            // Android a été accordée dans les paramètres système.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    // On vérifie d'abord que l'app a bien la permission CAMERA Android.
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        // Permission déjà accordée : on l'accorde directement à la page web.
                        request.grant(request.getResources());
                    } else {
                        // Permission pas encore accordée : on garde la demande de côté,
                        // on affiche le popup système, et on répondra à la page web
                        // une fois la réponse de l'utilisateur connue (voir
                        // onRequestPermissionsResult ci-dessous).
                        pendingWebPermissionRequest = request;
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST_CODE
                        );
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                pendingWebPermissionRequest = null;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && host.contains("alban3886.github.io")) {
                    return false;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingScreen.setVisibility(View.GONE);
                offlineScreen.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    loadingScreen.setVisibility(View.GONE);
                    offlineScreen.setVisibility(View.VISIBLE);
                    swipeRefresh.setRefreshing(false);
                }
            }
        });
    }

    // Appelée après que l'utilisateur a répondu au popup Android "Autoriser la caméra ?".
    // On répond à la demande de la page web en conséquence.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && pendingWebPermissionRequest != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                pendingWebPermissionRequest.grant(pendingWebPermissionRequest.getResources());
            } else {
                pendingWebPermissionRequest.deny();
            }
            pendingWebPermissionRequest = null;
        }
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
