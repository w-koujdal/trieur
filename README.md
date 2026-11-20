# Trieur

Appli Android qui **retrouve n'importe quelle image par ce qu'elle contient** : captures d'écran,
téléchargements et galerie. Le texte est lu (OCR) et les photos reconnues directement sur le
téléphone. Gratuit, sans pub, sans compte, **sans accès à Internet**.

> Né d'un fil Reddit : « je trie encore à la main ma galerie, mes captures et mes téléchargements ».

## Ce que ça fait

| | |
|---|---|
| **Recherche plein texte** | Tape un mot : toutes les images qui le contiennent, mots surlignés. Insensible aux accents et aux majuscules, mots partiels acceptés. |
| **Recherche par contenu** | « chien », « plage », « voiture », « gâteau »… trouve les photos qui le montrent (étiquettes traduites en français). |
| **Tri automatique** | Achats, Voyages, Codes, Lieux, Documents, Conversations, Réseaux sociaux, Web, Personnes, Animaux, Nourriture, Nature, Photos. Modifiable à la main ; un choix manuel n'est jamais écrasé. |
| **Filtres** | Par source (Captures, Téléchargements, Galerie), par appli d'origine (WhatsApp, Chrome, TikTok…), par catégorie. Combinables. |
| **Actions** | Liens, e-mails, numéros et codes trouvés dans le texte deviennent des boutons (ouvrir, écrire, appeler, copier). Partager, mettre à la corbeille. |
| **Nettoyage guidé** | Doublons (le plus récent est gardé), codes de vérification périmés, vieilles captures par appli, captures sans texte. Un lot = une sélection prête à vérifier. Jamais de lot « vieilles photos » pour l'appareil photo. |
| **Sélection multiple** | Appui long, puis « Tout », partager ou corbeille en une confirmation. |
| **En arrière-plan** | Les nouvelles images sont indexées en ~10 s, appli fermée. |

## Confidentialité

- Tout est analysé sur le téléphone (ML Kit, modèles embarqués dans l'APK).
- **Aucune permission Internet** : ML Kit l'ajoute pour envoyer des statistiques à Google, elle est
  retirée explicitement (`tools:node="remove"` dans `AndroidManifest.xml`). Vérifiable avec
  `adb shell dumpsys package com.trieur.screenshots`.
- Les images ne sont jamais modifiées. La corbeille passe par la confirmation d'Android
  (`MediaStore.createTrashRequest`) et reste récupérable 30 jours.
- Permissions : lecture des images (accès partiel accepté) et redémarrage (pour la tâche périodique).

## Installer

Pas de Play Store (le compte développeur est payant). L'APK se compile gratuitement :

```powershell
# JDK : celui d'Android Studio suffit
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease          # app/build/outputs/apk/release/app-release.apk (~16 Mo)
adb install -r app\build\outputs\apk\release\app-release.apk
```

La version release est signée avec la clé de debug pour s'installer directement. Pour la
distribuer, créer une vraie clé (gratuit) : `keytool -genkeypair …` puis un `signingConfig`.

Android 10 minimum ; APK limité à `arm64-v8a` (tous les téléphones récents). Pour un émulateur
x86_64, élargir `abiFilters` dans `app/build.gradle.kts`.

## Tester

```powershell
.\gradlew.bat testDebugUnitTest
```

Les règles (classement, détection de liens/numéros/codes, doublons, nettoyage, étiquettes,
sections de dates, sources) sont en Kotlin pur et testées sans téléphone.

## Architecture

```
app/src/main/java/com/trieur/screenshots/
├── TrieurApp.kt              dépendances, sources activées, reclassement quand les règles changent
├── data/
│   ├── Screenshot.kt         entité Room + index plein texte FTS4 (texte et étiquettes)
│   ├── ScreenshotDao.kt      requêtes (filtres facultatifs), requête FTS sûre
│   ├── AppDatabase.kt        base v5 et migrations 1→5
│   ├── Classifier.kt         catégorie (règles sur le texte, appli d'origine, étiquettes)
│   ├── Labels.kt             étiquettes ML Kit → français + catégorie, priorité sujet > décor
│   ├── Detected.kt           liens, e-mails, numéros FR, codes
│   ├── Duplicates.kt         dHash 64 bits + similarité de texte
│   ├── Cleanup.kt            lots de nettoyage proposés
│   └── Source.kt, Category.kt, Highlight.kt
├── scan/
│   ├── ScreenshotScanner.kt  MediaStore → OCR + étiquetage sur image réduite → base
│   ├── IndexJobService.kt    JobScheduler : nouvelle image (déclencheur de contenu) + toutes les 12 h
│   ├── ImageHash.kt          empreinte visuelle (barres système ignorées)
│   └── MediaAccess.kt        permissions selon la version d'Android
└── ui/                       Compose : écran principal, filtres, grille par date, panneaux
```

Choix notables :

- **JobScheduler plutôt que WorkManager** : sur Galaxy S24 (Android 16), WorkManager annulait la
  tâche déclenchée par une nouvelle image quand l'appli était fermée.
- **OCR sur image réduite à 2 560 px** (ImageDecoder, orientation EXIF respectée) : une photo de
  50 Mpx décodée en entier ferait ~200 Mo en mémoire. Mesuré : 687 photos en ~7 min, pic ~320 Mo.
- **Doublon = image proche ET texte proche** ; sans texte, seuil d'image plus strict.
- **Code natif compressé** dans l'APK : 28,7 → 16,1 Mo.

## Limites connues

- Les PDF ne sont pas lus (la permission « photos » d'Android ne les donne pas).
- L'OCR est optimisé pour l'alphabet latin.
- Si l'appli est « forcée à l'arrêt » ou mise en veille profonde par Samsung, l'indexation en
  arrière-plan s'arrête jusqu'à la prochaine ouverture.
