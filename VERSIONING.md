# Gestion des versions

Même principe que TopDriver et Sudoku Rescue : un dépôt de sources (celui-ci) et un dépôt de publication qui contient l'APK et `version.json`.

## Numérotation
- `majeur.mineur.correctif`, par exemple `0.0.2`. Dans les tags et dans `version.json`, le numéro est précédé de « v » (`v0.0.2`).
- Une version = un commit + un tag annoté `vX.Y.Z` + une section datée dans `CHANGELOG.md`.
- Le numéro est affiché dans l'appli (pied de page de la page web ; écran principal de l'appli Android).

## Sortir une version
1. Mettre à jour le numéro dans le code (page web : constante `VERSION` ; appli Android : `versionName` et `versionCode` dans `app/build.gradle.kts`).
2. Ajouter la section de la version dans `CHANGELOG.md` (ajouté / modifié / limites connues).
3. `git add -A` puis `git commit -m "vX.Y.Z — résumé"`.
4. `git tag -a vX.Y.Z -m "vX.Y.Z — résumé"`.
5. `git push origin main --follow-tags`.
6. Appli Android uniquement (à partir de la v0.1.0) : publier l'APK, voir plus bas.

## Compiler et publier l'appli Android (à partir de la v0.1.0)
Sur le PC Windows, comme TopDriver et Sudoku Rescue :
```powershell
cd C:\Users\lperr\Documents\charge-trotinette\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test               # tests unitaires (décodage des trames, durées)
.\gradlew.bat assembleRelease    # APK : app\build\outputs\apk\release\app-release.apk
```
Pour publier : `.\release.ps1 -Version 0.1.0` (compile, copie l'APK et `version.json` dans le dépôt de publication, commit, tag, push).

Signature : l'APK de release est signé avec la clé de debug du PC (`%USERPROFILE%\.android\debug.keystore`), ce qui suffit pour un usage personnel. Conséquence : une mise à jour ne s'installe que si l'APK est compilé sur le même PC (ou avec la même clé). Ne jamais committer de clé.

## Publication de l'APK (à partir de la v0.1.0)
Dépôt de publication : `charge-trotinette-releases`, avec seulement deux fichiers :
- `ChargeTrotinette_latest.apk`
- `version.json`, au même format que TopDriver : `{ "latest": "vX.Y.Z" }`

Au lancement, l'appli lit `https://raw.githubusercontent.com/l0renz044/charge-trotinette-releases/main/version.json`. Si la version publiée est plus récente que la sienne, elle affiche « Mise à jour disponible » et propose de télécharger l'APK.

## Retour en arrière
- Voir une ancienne version : `git checkout vX.Y.Z` (puis `git checkout main` pour revenir).
- Liste des versions : `git tag -n`.
- Changements entre deux versions : `git diff v0.0.1 v0.0.2`.

## Règles
- Ne jamais committer la clé de signature (`*.jks`) ni ses mots de passe : le `.gitignore` les exclut.
- Ne pas committer d'APK dans ce dépôt : ils vont dans le dépôt de publication.
- Historique : les versions v0.0.1 et v0.0.2 ont été reconstituées après coup à partir des copies archivées ; elles ont donc la même date de commit.
