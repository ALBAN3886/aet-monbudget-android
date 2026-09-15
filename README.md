# aet-monbudget-android

App Android **coquille WebView** pour AET MonBudget.

⚠️ Ce dépôt ne contient **aucun code web** (plus d'`index.html` local depuis le
nettoyage du 2026) : `MainActivity.java` charge directement en ligne
`https://alban3886.github.io/togosheets-pro/` (voir `APP_URL` dans
`MainActivity.java`). Le vrai code source de l'application (HTML/JS/Firebase)
vit uniquement dans le dépôt **togosheets-pro**, fichier `index.html`.

Pour modifier l'app : éditer `togosheets-pro/index.html`, publier sur GitHub
Pages, et l'app Android la chargera automatiquement au prochain lancement —
aucun rebuild Android n'est nécessaire pour un changement web.

Ce dépôt ne sert qu'à générer l'APK natif (icône, permissions caméra/biométrie,
mécanisme de mise à jour intégré). Voir `.github/workflows/` pour le build CI.
