package com.aet.monbudget;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AET MonBudget - Activité principale.
 * Charge l'application web (HTML/JS/Firebase) dans une WebView native,
 * sans aucune marque ou watermark tiers.
 */
public class MainActivity extends AppCompatActivity {

    // Adresse de l'app web hébergée (GitHub Pages).
    // Remplace cette URL par celle de ton site si elle change.
    private static final String APP_URL = "https://alban3886.github.io/togosheets-pro/";

    // API GitHub publique pour connaître la dernière version publiée (releases).
    private static final String UPDATE_CHECK_URL =
            "https://api.github.com/repos/ALBAN3886/aet-monbudget-android/releases/latest";
    private static final String UPDATE_APK_FILENAME = "togosheets-update.apk";
    private static final int REQUEST_INSTALL_PERMISSION_CODE = 2001;

    // Code utilisé pour identifier la réponse de la demande de permission caméra
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    // Mémorise la demande de permission caméra faite par la page web (WebView),
    // pour pouvoir y répondre une fois que l'utilisateur a répondu au popup Android.
    private PermissionRequest pendingWebPermissionRequest;
    private ValueCallback<Uri[]> pendingFileChooserCallback;
    private static final int REQUEST_GALLERY_CODE = 2003;
    private static final int REQUEST_CAMERA_CODE = 2004;
    private static final int REQUEST_GOOGLE_SIGNIN_CODE = 2005;

    // ⚠️ Remplace par le "Web client ID" du projet Firebase
    // (console Firebase → Authentication → Sign-in method → Google → Web SDK configuration).
    // Ce n'est PAS un secret : c'est un identifiant public, comme la clé Firebase.
    private static final String GOOGLE_WEB_CLIENT_ID = "1090226725870-9dcjtlqesaak57rpcs2sup6gcrun9ej6.apps.googleusercontent.com";

    private GoogleSignInClient googleSignInClient;
    // Chemin du fichier temporaire où la photo prise par l'appareil photo est enregistrée
    // (nécessaire car, contrairement à la galerie, l'appareil photo ne renvoie pas
    // l'image directement dans le résultat de l'activité — il faut lui donner un
    // emplacement à l'avance via FileProvider, puis aller la relire à cet emplacement).
    private String cameraPhotoPath;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout loadingScreen;
    private LinearLayout offlineScreen;

    // ── Mise à jour intégrée ──
    private LinearLayout updateBanner;
    private TextView updateText;
    private Button updateButton;
    private ImageButton updateDismiss;
    private String pendingApkUrl;
    private long updateDownloadId = -1;
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == updateDownloadId) {
                installDownloadedApk();
            }
        }
    };

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

        updateBanner = findViewById(R.id.updateBanner);
        updateText = findViewById(R.id.updateText);
        updateButton = findViewById(R.id.updateButton);
        updateDismiss = findViewById(R.id.updateDismiss);
        updateDismiss.setOnClickListener(v -> updateBanner.setVisibility(View.GONE));

        Button checkUpdateBtn = findViewById(R.id.checkUpdateBtn);
        checkUpdateBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Vérification en cours…", Toast.LENGTH_SHORT).show();
            checkForUpdate(true);
        });

        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, downloadFilter);
        }

        setupWebView();

        retryButton.setOnClickListener(v -> {
            offlineScreen.setVisibility(View.GONE);
            loadingScreen.setVisibility(View.VISIBLE);
            webView.loadUrl(APP_URL);
        });

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        webView.loadUrl(APP_URL);
        checkForUpdate(false);
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

        // Pont JavaScript <-> Java pour l'empreinte digitale native.
        // Accessible depuis la page web via window.AndroidBiometric.*
        webView.addJavascriptInterface(new BiometricBridge(), "AndroidBiometric");

        // Pont JavaScript <-> Java pour la connexion Google native.
        // Google bloque volontairement la connexion OAuth depuis une WebView (message
        // "This browser or app may not be secure") — impossible à contourner en JS pur.
        // On passe donc par le SDK Google Sign-In natif d'Android, qui renvoie ensuite
        // un idToken à la page web via window.onGoogleSignInResult(...).
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        webView.addJavascriptInterface(new GoogleAuthBridge(), "AndroidAuth");

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

            // Appelée quand la page web déclenche un <input type="file"> (ex: "Ajouter une photo").
            // Sans ceci, taper sur ce bouton ne fait RIEN dans la WebView Android — le sélecteur
            // de galerie ne s'ouvre jamais, même si tout fonctionne normalement sur le web.
            //
            // Galerie et appareil photo sont gérés comme deux chemins bien séparés (au lieu
            // d'un unique sélecteur système combiné) : certains téléphones renvoient mal le
            // résultat de la galerie quand elle est combinée avec un appareil photo dans le
            // même chooser. Séparer les deux est plus fiable sur l'ensemble des appareils.
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (pendingFileChooserCallback != null) {
                    pendingFileChooserCallback.onReceiveValue(null);
                    pendingFileChooserCallback = null;
                }
                pendingFileChooserCallback = filePathCallback;
                cameraPhotoPath = null;

                boolean hasCameraPermission = ContextCompat.checkSelfPermission(
                        MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

                if (!hasCameraPermission) {
                    openGalleryPicker();
                    return true;
                }

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Ajouter une photo")
                        .setItems(new String[]{ "Prendre une photo", "Choisir depuis la galerie" },
                                (dialog, which) -> {
                                    if (which == 0) openCameraCapture();
                                    else openGalleryPicker();
                                })
                        .setOnCancelListener(dialog -> {
                            if (pendingFileChooserCallback != null) {
                                pendingFileChooserCallback.onReceiveValue(null);
                                pendingFileChooserCallback = null;
                            }
                        })
                        .show();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                // Le site officiel reste dans la WebView de l'app.
                if (host != null && host.contains("alban3886.github.io")) {
                    return false;
                }
                // Tout le reste (WhatsApp, tel:, mailto:, sites de paiement, etc.)
                // s'ouvre avec l'application système appropriée, pas dans la WebView.
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // Aucune app ne peut ouvrir ce lien : on ne fait rien plutôt que de planter.
                }
                return true;
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
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (IllegalArgumentException ignored) {}
    }

    // ── Mise à jour intégrée : vérification, téléchargement, installation ──

    /** Vérifie en arrière-plan si une nouvelle version est publiée sur GitHub Releases. */
    /** Vérifie en arrière-plan si une nouvelle version est publiée sur GitHub Releases.
     *  @param showFeedback si true (vérification manuelle via le bouton), affiche un message
     *                      même quand il n'y a rien de nouveau ou en cas d'erreur réseau.
     *                      Si false (vérification automatique au démarrage), reste silencieux. */
    private void checkForUpdate(boolean showFeedback) {
        new Thread(() -> {
            try {
                int localVersion = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionCode;

                URL url = new URL(UPDATE_CHECK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    if (showFeedback) runOnUiThread(() ->
                        Toast.makeText(this, "Impossible de vérifier pour le moment.", Toast.LENGTH_SHORT).show());
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.optString("tag_name", "");

                // Le tag est de la forme "v1.0.<numéro de build>" : on compare ce numéro
                // au versionCode local (les deux viennent du même compteur CI).
                Matcher m = Pattern.compile("(\\d+)$").matcher(tagName);
                if (!m.find()) {
                    if (showFeedback) runOnUiThread(() ->
                        Toast.makeText(this, "Impossible de vérifier pour le moment.", Toast.LENGTH_SHORT).show());
                    return;
                }
                int remoteVersion = Integer.parseInt(m.group(1));

                if (remoteVersion <= localVersion) {
                    if (showFeedback) runOnUiThread(() ->
                        Toast.makeText(this, "Tu es déjà à jour ✓", Toast.LENGTH_SHORT).show());
                    return; // déjà à jour
                }

                // Trouver l'URL de téléchargement de l'APK dans les assets de la release
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (apkUrl == null) {
                    if (showFeedback) runOnUiThread(() ->
                        Toast.makeText(this, "Impossible de vérifier pour le moment.", Toast.LENGTH_SHORT).show());
                    return;
                }

                final String finalApkUrl = apkUrl;
                final String versionLabel = tagName;
                runOnUiThread(() -> showUpdateBanner(versionLabel, finalApkUrl));

            } catch (Exception e) {
                // Échec silencieux au démarrage ; message si vérification manuelle.
                if (showFeedback) runOnUiThread(() ->
                    Toast.makeText(this, "Impossible de vérifier pour le moment.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showUpdateBanner(String versionLabel, String apkUrl) {
        pendingApkUrl = apkUrl;
        updateText.setText("Nouvelle version disponible (" + versionLabel + ")");
        updateButton.setText("Mettre à jour");
        updateButton.setEnabled(true);
        updateButton.setOnClickListener(v -> startApkDownload());
        updateBanner.setVisibility(View.VISIBLE);
    }

    /** Télécharge l'APK directement dans l'app (aucune sortie vers un navigateur). */
    private void startApkDownload() {
        if (pendingApkUrl == null) return;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(pendingApkUrl));
            request.setTitle("Mise à jour TogoSheets");
            request.setDescription("Téléchargement en cours…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, UPDATE_APK_FILENAME);
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            // On efface un éventuel ancien téléchargement resté au même nom.
            File existing = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_APK_FILENAME);
            if (existing.exists()) existing.delete();

            updateDownloadId = dm.enqueue(request);
            updateButton.setEnabled(false);
            updateButton.setText("Téléchargement…");
        } catch (Exception e) {
            Toast.makeText(this, "Impossible de démarrer le téléchargement.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Appelé quand le téléchargement de l'APK est terminé : vérifie puis lance l'installation. */
    private void installDownloadedApk() {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(updateDownloadId);
        android.database.Cursor cursor = dm.query(query);
        boolean success = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                    success = true;
                }
            }
            cursor.close();
        }

        runOnUiThread(() -> {
            updateButton.setEnabled(true);
            updateButton.setText("Mettre à jour");
        });

        if (!success) {
            runOnUiThread(() -> Toast.makeText(this, "Échec du téléchargement. Réessaie.", Toast.LENGTH_SHORT).show());
            return;
        }

        // Android 8+ : l'utilisateur doit autoriser l'installation depuis cette app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Autorise l'installation pour continuer la mise à jour.", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settingsIntent, REQUEST_INSTALL_PERMISSION_CODE);
            });
            return;
        }

        launchApkInstall();
    }

    private void launchApkInstall() {
        File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_APK_FILENAME);
        if (!apkFile.exists()) return;
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(installIntent);
    }

    // Ouvre le sélecteur de galerie seul (une ou plusieurs photos).
    private void openGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_GALLERY_CODE);
        } catch (ActivityNotFoundException e) {
            if (pendingFileChooserCallback != null) {
                pendingFileChooserCallback.onReceiveValue(null);
                pendingFileChooserCallback = null;
            }
        }
    }

    // Ouvre l'appareil photo seul, avec un fichier de destination préparé à l'avance
    // (obligatoire : sans ça, la photo est bien prise mais ne revient jamais dans la WebView).
    private void openCameraCapture() {
        try {
            File photoFile = createTempImageFile();
            cameraPhotoPath = photoFile.getAbsolutePath();
            Uri photoUri = FileProvider.getUriForFile(
                    MainActivity.this, getPackageName() + ".fileprovider", photoFile);
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(cameraIntent, REQUEST_CAMERA_CODE);
        } catch (IOException | ActivityNotFoundException e) {
            cameraPhotoPath = null;
            if (pendingFileChooserCallback != null) {
                pendingFileChooserCallback.onReceiveValue(null);
                pendingFileChooserCallback = null;
            }
        }
    }

    // Crée un fichier vide dans le cache de l'app pour que l'appareil photo
    // sache où enregistrer la photo prise (obligatoire pour WebView + caméra).
    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = new File(getCacheDir(), "camera");
        if (!storageDir.exists()) storageDir.mkdirs();
        return File.createTempFile("PHOTO_" + timeStamp + "_", ".jpg", storageDir);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_PERMISSION_CODE) {
            // Que la permission ait été accordée ou non, on retente : si elle est accordée,
            // l'installation démarre ; sinon rien ne se passe (l'utilisateur pourra réessayer).
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) {
                launchApkInstall();
            }
        } else if (requestCode == REQUEST_GALLERY_CODE) {
            if (pendingFileChooserCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    // Plusieurs photos sélectionnées
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    // Une seule photo sélectionnée
                    results = new Uri[]{ data.getData() };
                }
            }
            pendingFileChooserCallback.onReceiveValue(results);
            pendingFileChooserCallback = null;
        } else if (requestCode == REQUEST_CAMERA_CODE) {
            if (pendingFileChooserCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && cameraPhotoPath != null) {
                File photoFile = new File(cameraPhotoPath);
                if (photoFile.exists() && photoFile.length() > 0) {
                    Uri photoUri = FileProvider.getUriForFile(
                            MainActivity.this, getPackageName() + ".fileprovider", photoFile);
                    results = new Uri[]{ photoUri };
                }
            }
            pendingFileChooserCallback.onReceiveValue(results);
            pendingFileChooserCallback = null;
            cameraPhotoPath = null;
        } else if (requestCode == REQUEST_GOOGLE_SIGNIN_CODE) {
            try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                        .getResult(ApiException.class);
                notifyGoogleSignIn(true, account.getIdToken());
            } catch (ApiException e) {
                notifyGoogleSignIn(false, "Connexion Google annulée ou échouée (code " + e.getStatusCode() + ")");
            }
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

    /**
     * Pont exposé au JavaScript sous le nom "AndroidBiometric".
     * Utilise androidx.biometric (BiometricPrompt) pour afficher le vrai
     * dialogue d'empreinte/visage natif d'Android, et renvoie le résultat
     * au JavaScript en appelant window.onBiometricResult(success, message)
     * dans la page web.
     *
     * Côté JS (index.html), utilisation typique :
     *   if (window.AndroidBiometric && window.AndroidBiometric.isAvailable()) {
     *     window.onBiometricResult = function(success, message) { ... };
     *     window.AndroidBiometric.authenticate('Déverrouiller AET MonBudget');
     *   }
     */
    private class BiometricBridge {

        @JavascriptInterface
        public boolean isAvailable() {
            BiometricManager manager = BiometricManager.from(MainActivity.this);
            int result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return result == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void authenticate(String promptTitle) {
            // BiometricPrompt doit être lancé sur le thread principal (UI thread).
            new Handler(Looper.getMainLooper()).post(() -> showBiometricPrompt(promptTitle));
        }
    }

    /**
     * Pont JavaScript <-> Java pour la connexion Google, appelable depuis la page web via :
     *   if (window.AndroidAuth) { window.AndroidAuth.googleSignIn(); }
     * Le résultat revient à la page web via window.onGoogleSignInResult(success, idTokenOuMessage).
     */
    private class GoogleAuthBridge {
        @JavascriptInterface
        public void googleSignIn() {
            runOnUiThread(() -> {
                // Se déconnecte d'abord de la session Google mise en cache, pour forcer
                // l'affichage du sélecteur de compte à chaque fois (sinon, avec un seul
                // compte sur l'appareil, Android saute directement dessus sans rien demander).
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    Intent signInIntent = googleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, REQUEST_GOOGLE_SIGNIN_CODE);
                });
            });
        }
    }

    private void notifyGoogleSignIn(boolean success, String idTokenOrMessage) {
        String safe = idTokenOrMessage == null ? "" : idTokenOrMessage.replace("'", "\\'");
        String js = "if (typeof window.onGoogleSignInResult === 'function') { "
                + "window.onGoogleSignInResult(" + success + ", '" + safe + "'); }";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void showBiometricPrompt(String promptTitle) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        notifyJs(true, "success");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        notifyJs(false, errString != null ? errString.toString() : "error");
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Empreinte non reconnue : on ne notifie pas tout de suite,
                        // BiometricPrompt laisse l'utilisateur réessayer automatiquement.
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle != null ? promptTitle : "Authentification")
                .setNegativeButtonText("Annuler")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // Transmet le résultat de l'authentification biométrique au JavaScript,
    // en appelant window.onBiometricResult(success, message) dans la page.
    private void notifyJs(boolean success, String message) {
        String safeMessage = message == null ? "" : message.replace("'", "\\'");
        String js = "if (typeof window.onBiometricResult === 'function') { "
                + "window.onBiometricResult(" + success + ", '" + safeMessage + "'); }";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }
}
