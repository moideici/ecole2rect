# Résultat — VOIE 3 : widgets (W1 à W4)

Travail laissé **non committé** dans le worktree `agent/20261108-191434-voie3`.
Aucun push, merge, changement de branche, accès aux identifiants ou requête vers ÉcoleDirecte.

## Fait

### W1 — Moyenne

- Récepteur `MeanWidgetReceiver`, pose 1×1, agrandissement horizontal jusqu'au palier 2×1.
- Configuration à l'ajout et reconfiguration depuis la roue ou le menu du lanceur ; annulation par défaut,
  validation explicite par « Enregistrer », réglages indépendants par `appWidgetId`, nettoyage à la suppression.
- Choix du profil/enfant déjà publié, source générale / matière / classe / écart à un objectif,
  période courante / précédente, titre optionnel de huit caractères maximum, seuils et tendance.
- `MeanWidgetData.read(...)` est le point unique de lecture. Les instantanés conservent les valeurs non arrondies,
  l'origine et le bulletin provenant du dépôt qui utilise `AverageEngine` et `AverageReconciliation`.
  Le repli sur les moyennes de matières passe également par `AverageEngine` (coefficients zéro et sous-matières exclus).
- Formatage par `AveragePresentation` et `FrenchFormat`, décimales globales relues à chaque dessin.
  Tendance uniquement entre origines compatibles. Origine et bulletin sont disponibles dans la variante large.
- Absence « – », mode montrer « ••• » sans couleur ni tendance révélatrice, ancienneté en heures.
- Lecture compatible avec l'ancien instantané pour la générale ; aucune matière ni période précédente inventée.
- Toucher : Notes du profil choisi, défilement vers la matière et dépliage si elle existe.

### W2 — Pack 1×1

- Cinq récepteurs supplémentaires, tous avec configuration du profil, suppression des réglages par instance,
  tailles responsive et gabarit commun `OneByOne` :
  - Prochain cours : cours annulés ignorés, heure et délai calculé au dessin, salle en large.
  - Devoirs restants : compteur complet (plus de limitation à huit), alerte pour aujourd'hui/demain,
    première matière en large ; échéance inconnue comptée sans inventer d'urgence.
  - Dernière note : barème, matière, « Nouveau » pendant trois jours à partir de la première apparition locale,
    écart à la classe dans l'unité du devoir en large.
  - Messages non lus : compteur, « ••• » si le mode montrer masque les messages.
  - Prochain contrôle : `ExamCountdown`, J−N, matière et date ; un contrôle reste visible quand sa préparation est cochée.
- Les publications réutilisent les données déjà lues par l'application ou la synchronisation. Aucun appel réseau
  supplémentaire depuis un widget, une configuration ou la tuile. La synchro publie aussi les autres enfants déjà lus,
  tout en conservant la boîte de réception familiale lue une seule fois.

### W3 — Objectifs et tuile

- `GoalStore` local par profil et identifiant de matière ; cible sur 20, strictement positive, au plus 20,
  champ vide pour supprimer. Même éditeur dans la configuration Objectifs et la matière dépliée dans Notes.
- Widget Objectifs 2×2 minimum : moyenne actuelle et objectif écrits, jauge bornée 0–100 %, tri par écart
  croissant (objectif le plus éloigné d'abord), au plus quatre lignes au petit palier, jusqu'à douze en grand.
  L'objectif absent est indiqué ; une moyenne supérieure à l'objectif donne 100 %.
- Mode montrer : valeurs et jauges masquées, ordre des matières conservé pour ne pas révéler les écarts.
- Tuile Réglages rapides « Moyenne » : dernière générale publiée, décimales globales, toucher vers Notes.
  « Verrouillé » lorsque l'appareil est verrouillé **ou que le verrou de l'application est activé** ; le toucher
  conserve les protections Android et l'écran de verrouillage de MainActivity. Compatibilité du lancement API 34+ gardée.
- Retrait d'un compte : suppression de ses instantanés et objectifs. Les instances perdent leurs réglages personnels
  mais gardent la liaison au profil supprimé, afin d'afficher une absence plutôt que les données d'un autre compte.

### Taille et accessibilité

- Les nouveaux contenus utilisent l'espace disponible, sans largeur ou hauteur de contenu imposée.
  Les `DpSize` et dimensions XML définissent uniquement les paliers et bornes des widgets du lanceur.
- Décisions de disposition pures dans `SmallWidgetLayout` et `GoalWidgetData.capacity`, avec largeur, hauteur,
  orientation et échelle de police ; cas téléphone, paysage, tablette et grandes polices dans les tests écrits.
- La taille de texte de l'application déclenche un redessin et s'ajoute à celle du système, sans appliquer deux fois
  le facteur système. Les couleurs positives/orange/rouges proviennent des rôles contrastés du thème existant.
- Les noms longs et détails ont un nombre de lignes limité pour préserver les valeurs. Les descriptions accessibles
  contiennent les textes complets, après masquage. Un toucher ouvre le détail.
- Quand une police extrême ne permet plus deux nombres lisibles dans Objectifs, le widget montre le lien vers Notes.
  Pour les plus petites tailles 1×1, la valeur prime et les détails passent dans l'application et la description accessible.

### W4 — Documentation

- Section Widgets du README mise à jour : treize widgets, configuration, objectifs, confidentialité et tuile.
- Présent rapport avec modifications fichier par fichier, vérifications réellement effectuées et limites.

## Tests ajoutés

**31 tests unitaires déclarés dans cinq nouveaux fichiers**, non exécutés ici :

| Fichier | Nombre | Couverture |
| --- | ---: | --- |
| `app/src/test/java/fr/ecole2recte/app/widget/MeanWidgetTest.kt` | 12 | Sources, période précédente, origines/tendance, seuils inclusifs, décimales, confidentialité, absence, moteur, sérialisation, suppression, migration, disposition, ancienneté |
| `app/src/test/java/fr/ecole2recte/app/widget/SmallWidgetsTest.kt` | 9 | Cours annulés/délai, compteur complet/urgence, dernière note/barème/écart, trois jours, confidentialité, contrôles faits, dates invalides, absence, sérialisation |
| `app/src/test/java/fr/ecole2recte/app/widget/GoalsWidgetTest.kt` | 6 | Tri, deux nombres, jauge, objectif absent/dépassé, profils, validation/suppression, confidentialité, capacités, tuile verrouillée/décimales |
| `app/src/test/java/fr/ecole2recte/app/widget/WidgetNavigationTest.kt` | 2 | Extras grades → notes, profil/matière, valeurs invalides et sujet hors Notes |
| `app/src/test/java/fr/ecole2recte/app/data/WidgetCachedPeriodsTest.kt` | 2 | Bulletin en cache sans session ouverte ni client, isolation entre enfants |

- `WidgetThemeTest.kt` : assertions de contraste ajoutées pour les rôles positif et orange dans toutes les palettes.
- `WidgetRenderingTest.kt` : **trois nouveaux tests instrumentés** de composition Glance/RemoteViews, selon la technique
  existante `runComposition` : tous les paliers avec et sans données, thèmes et police maximale, masquage et décimales.
  Les instantanés par profil et réglages modifiés par ces tests sont restaurés.
- Les tests principaux de chaque lot ont été écrits avant son implémentation. **Aucune séquence rouge/vert n'a pu
  être observée**, car le lanceur Bash s'arrête avant toute compilation.

## Vérifications réelles

Commandes tentées au démarrage, après écriture des tests W1, W2, W3 et à la clôture :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest lintDebug assembleDebug
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh C:/Users/G/.agent-hub/worktrees/ecole2recte-00a448/20261108-191434-voie3 testDebugUnitTest lintDebug assembleDebug
```

Toutes terminent avec le **code 1**, avant Gradle. Dernière sortie :

```text
0 [main] bash (26788) C:\Program Files\Git\usr\bin\bash.exe: *** fatal error - CreateFileMapping S-1-5-21-2892306765-1606266371-1694119132-1001.1, Win32 error 5.  Terminating.
```

**Ni `testDebugUnitTest`, ni `lintDebug`, ni `assembleDebug` n'ont été exécutés.** Aucun nombre de tests passés,
aucun APK et aucune réussite de compilation ne sont annoncés. La référence de 557 tests / zéro échec est celle
communiquée par le hub ; elle n'a pas été reproduite ici. Le hub doit compiler et relancer ses vérifications.

Contrôles statiques locaux effectués avec succès :

- `git diff --check` : aucune sortie, code 0 lors du contrôle indépendant.
- Analyse XML Python : **31 fichiers XML** de ressources/manifest analysés sans erreur de syntaxe.
- Manifest : **13 récepteurs widgets**, chaque ressource provider existe et chaque nouveau provider configurable
  déclare `reconfigurable`.
- Contrôle lexical local des **37 fichiers Kotlin ajoutés/modifiés** : chaînes/commentaires et délimiteurs équilibrés.
  Ce contrôle ne résout pas les symboles, ne vérifie pas les types et **ne remplace pas le compilateur Kotlin**.
- Comptage statique des annotations `@Test` des cinq fichiers ci-dessus : 31. Il ne prouve aucune exécution.

## Pas fait / incertitudes

- Compilation Kotlin/Glance, lint, construction APK et exécution des tests : bloqués par le démarrage de Bash.
- Tests instrumentés : écrits, **non exécutés** ; aucun essai sur téléphone ni émulateur dans ce travail.
- Les tailles en cellules dépendent du lanceur Android ; lisibilité, ellipse et largeur utile restent à valider réellement.
- La tuile masque volontairement sa valeur tant que le réglage de verrouillage de l'application est activé,
  même après un déverrouillage récent dans l'app : aucun état « session déverrouillée » partagé avec la tuile n'a été introduit.
- Une ancienne publication ne contient pas les matières et périodes : elles apparaîtront après la prochaine publication
  normale de l'app/synchro. Un enfant jamais publié n'est pas chargé par le widget.
- Le délai et l'ancienneté sont recalculés au redessin ; aucun minuteur, intervalle ou mécanisme de synchro n'a été modifié.
- Le redessin du thème et de la confidentialité conserve le mécanisme existant de Glance ; son délai réel sur un lanceur
  et les performances des nouveaux instantanés devront être vérifiés.
- Aucun changement de `ApiSession`, des intervalles de synchronisation ou des garde-fous d'écriture.

## À vérifier sur le téléphone

1. Ajouter, annuler puis reconfigurer plusieurs instances Moyenne, avec quatre sources et deux périodes ; modifier
   une instance, vérifier que les autres restent identiques, supprimer puis réajouter une instance.
2. Comparer les valeurs et les origines avec Notes (bulletin à jour/en retard), basculer entre une et deux décimales,
   personnaliser les seuils aux bornes ; toucher une matière et vérifier profil, défilement et dépliage.
3. Compte famille : sélectionner chaque enfant, publier les données, configurer des widgets différents, changer le profil
   actif ; vérifier aussi le retrait d'un compte et l'absence de repli vers un autre enfant.
4. Pack 1×1 : cours annulé, début de cours, salle, devoirs de demain (dont plus de huit), note nouvelle puis trois jours
   après sa première apparition, écart au barème du devoir, contrôle dont la préparation est faite.
5. Objectifs : édition dans les deux endroits, suppression, tri, jauge dépassant l'objectif, absence de notes et d'objectif,
   quatre matières au petit palier puis davantage en grand.
6. Mode montrer avec et sans masquage des messages : aucune moyenne, note, jauge, couleur ou tendance dérivée ; désactiver
   le mode et vérifier que les valeurs reviennent sans requête.
7. Toutes tailles, portrait/paysage, tablette, pliable, très grande police de l'app et du système, lecteurs d'écran,
   thèmes clair/sombre/noir ; vérifier les libellés longs, nombres et descriptions accessibles.
8. Tuile : ajout aux Réglages rapides, moyenne et décimales, appareil verrouillé, verrou de l'app activé, toucher et
   déverrouillage, notamment sur Android 14+ pour le lancement avec PendingIntent.

## Modifications fichier par fichier

Chemins sous `app/src/main/java/fr/ecole2recte/app/`, sauf indication contraire.

| Fichier | Modification |
| --- | --- |
| `widget/MeanWidgetData.kt` | Modèles numériques et sérialisation des périodes/matières ; sources, formatage, tendance, seuils, absence, migration et confidentialité |
| `widget/WidgetConfigStore.kt` | Persistance/validation par instance, suppression, nettoyage de compte sans changement de profil implicite |
| `widget/SmallWidgetLayout.kt` | Variantes et budget de texte purs selon taille, orientation et police |
| `widget/OneByOne.kt` | Gabarit Glance commun, paliers, descriptions accessibles, intentions de navigation/configuration, échelle de texte |
| `widget/MeanWidget.kt` | Widget Moyenne, lecture centrale et cycle de suppression de configuration |
| `widget/WidgetConfigurationActivity.kt` | Configuration locale des sept nouveaux widgets, profil, options Moyenne, objectifs, verrouillage |
| `widget/SmallWidgetData.kt` | Logique pure des cinq petits widgets |
| `widget/SmallWidgets.kt` | Cinq widgets et leurs récepteurs Glance, redimensionnement et suppression de configuration |
| `widget/GoalStore.kt` | Objectifs locaux par profil/matière et nettoyage |
| `widget/GoalWidgetData.kt` | Tri, deux nombres, présentation compacte, progression et capacité purs |
| `widget/GoalsWidget.kt` | Widget Objectifs, lignes/jauges adaptatives, configuration et navigation |
| `widget/MeanTileService.kt` | Présentation pure de la tuile et TileService avec verrouillage/lancement API 34+ |
| `widget/WidgetDestination.kt` | Validation des extras de navigation et alias grades → notes |
| `widget/WidgetLogic.kt` | Publication des valeurs numériques, toutes tâches, contrôles, dates/écarts/identifiants des notes |
| `widget/WidgetPrivacy.kt` | Masquage des nouvelles données et indicateurs dérivés, compteur/messages et profils imbriqués |
| `widget/WidgetTheme.kt` | Rôles positif/orange calculés depuis les palettes contrastées |
| `widget/WidgetUi.kt` | Exposition des rôles positif/orange aux vues Glance |
| `widget/Widgets.kt` | Redessin des nouveaux widgets, première apparition des notes, affichage messages masqués |
| `data/EcoleDirecteRepository.kt` | Lecture des périodes exclusivement depuis le cache, réutilisation de `overview` et du moteur existant |
| `domain/SchoolRepository.kt` | Contrat `cachedGradePeriods` sans réseau |
| `sync/SyncStorage.kt` | Instantané enrichi et rétrocompatible, stockage séparé par profil, nettoyage de compte |
| `sync/SyncService.kt` | Publication des profils déjà parcourus et périodes en cache, sans changement des appels/pauses/intervalles |
| `ui/home/HomeScreen.kt` | Publication du profil et des périodes en cache lors de l'ouverture normale |
| `ui/grades/GoalEditor.kt` | Champ Objectif commun, validation, suppression et redessin local |
| `ui/grades/GradesView.kt` | Objectif dans la matière, lignes paresseuses identifiables et ouverture ciblée depuis le widget |
| `ui/navigation/MainScaffold.kt` | Sélection du compte/enfant demandé avant ouverture de l'onglet |
| `ui/AppRoot.kt` | Nettoyage objectifs/configurations/instantanés au retrait d'un compte |
| `AppContainer.kt` | Demandes de navigation widget en attente (profil et matière) |
| `MainActivity.kt` | Lecture/validation des extras widget et publication de la destination |
| `settings/AppSettings.kt` | Redessin des widgets lors du changement de taille du texte |
| `app/src/main/AndroidManifest.xml` | Sept nouveaux récepteurs, activité de configuration et service de tuile protégé |
| `app/src/main/res/xml/mean_widget_info.xml` | Provider Moyenne 1×1 → 2×1, configuration/reconfiguration |
| `app/src/main/res/xml/small_next_class_widget_info.xml` | Provider Prochain cours 1×1 configurable |
| `app/src/main/res/xml/remaining_homework_widget_info.xml` | Provider Devoirs restants 1×1 configurable |
| `app/src/main/res/xml/last_grade_widget_info.xml` | Provider Dernière note 1×1 configurable |
| `app/src/main/res/xml/unread_messages_widget_info.xml` | Provider Messages non lus 1×1 configurable |
| `app/src/main/res/xml/exam_countdown_widget_info.xml` | Provider Prochain contrôle 1×1 configurable |
| `app/src/main/res/xml/goals_widget_info.xml` | Provider Objectifs 2×2 minimum configurable |
| `app/src/main/res/values/widgets_small.xml` | Libellés/descriptions français des nouveaux widgets et de la tuile |
| `app/src/main/res/drawable/ic_tile_mean.xml` | Icône vectorielle de la tuile Moyenne |
| `app/src/test/java/fr/ecole2recte/app/widget/MeanWidgetTest.kt` | 12 tests unitaires W1 et régressions de migration/nettoyage/disposition |
| `app/src/test/java/fr/ecole2recte/app/widget/SmallWidgetsTest.kt` | 9 tests unitaires W2 |
| `app/src/test/java/fr/ecole2recte/app/widget/GoalsWidgetTest.kt` | 6 tests unitaires W3 |
| `app/src/test/java/fr/ecole2recte/app/widget/WidgetNavigationTest.kt` | 2 tests unitaires de destination |
| `app/src/test/java/fr/ecole2recte/app/data/WidgetCachedPeriodsTest.kt` | 2 tests unitaires de lecture locale/isolation |
| `app/src/test/java/fr/ecole2recte/app/widget/WidgetThemeTest.kt` | Assertions de contraste des deux rôles ajoutés |
| `app/src/androidTest/java/fr/ecole2recte/app/WidgetRenderingTest.kt` | Trois tests instrumentés Glance, paliers/thèmes/police/confidentialité, restauration des profils |
| `README.md` | Présentation des nouveaux widgets et de la tuile |
| `RESULTAT-WIDGETS.md` | Résultats, limites, vérifications réelles, tests et recette téléphone |
