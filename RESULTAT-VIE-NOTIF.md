# Résultat — VOIE 4 : vie scolaire, cours en cours et raccourcis

## Fait

### V1 — Cours en cours

- Option locale désactivée par défaut dans Plus > Notifications, indépendante de la synchronisation réseau.
- Planificateur pur : cours annulés ou malformés ignorés, intervalles début inclus / fin exclue, progression, prochain cours et prochaine borne.
- Notification persistante uniquement pendant un cours, matière, salle et « Ensuite ». Version publique générique sur l'écran verrouillé ; contenu « ••• » en mode montrer.
- WorkManager sans contrainte réseau ni service au premier plan : changements aux bornes et progression locale au plus tôt chaque minute pendant un cours. Ces exécutions sont inexactes. Expiration Android à la fin du cours, même si la tâche suivante est retardée.
- Style natif de progression essayé uniquement avec `SDK_INT >= 36`, par réflexion sur les API publiques, car le projet compile avec SDK 35. Repli classique si indisponible ; à vérifier sur appareil.
- Tuile « Prochain cours » : données locales, libellé discret lorsque verrouillé ou en mode montrer, ouverture de l'Agenda après déverrouillage.

### V2 — Vie scolaire

- Carte de Plus ouvrant un écran dédié en lecture seule : absences, retards, sanctions, encouragements, regroupement par mois et filtres défilants.
- Détail : motif/commentaire, statut et durée tels que fournis. Le parseur privilégie `duree` sur `displayDate`.
- Compteurs d'absences non justifiées et de durée connue ; nombre de durées manquantes explicite. Aucune durée n'est déduite de l'écart entre deux horaires.
- Compteurs sur une, deux ou trois colonnes selon largeur, hauteur, orientation et police ; contenu défilant et aucun gabarit fixe de contenu.
- Widget Glance 1×1 autonome, paliers 60×60, 180×110 et 180×200 dp, redimensionnable, couleurs du thème, compteur masqué en mode montrer, légende accessible au lecteur d'écran. Les détails sont réservés aux tailles suffisantes. Taille du texte de l'application prise en compte.
- Rappel facultatif après 2, 5 ou 7 jours (désactivé par défaut), dédupliqué par événement et profil, fondé sur l'instantané local, discret sur l'écran verrouillé et suspendu en mode montrer.
- Instantané minimal sans motifs/commentaires, publié à l'ouverture de Vie scolaire et à la synchronisation habituelle si le widget est posé ou le rappel activé. Pas de nouvelle cadence réseau. Le module fermé masque l'entrée et interdit la lecture de cette ressource.

### V3 — Raccourcis

- Consultations stockées par compte/élève ; au plus quatre matières, fréquence décroissante puis récence, limitées aux matières du dernier instantané Notes.
- Publication après synchronisation et consultation ; lien vers la matière, recherche par identité, puis code, puis nom normalisé non ambigu.
- Raccourci « Prochain cours » épinglable depuis Plus > Notifications.
- Liens profonds `ecole2recte://notes?matiere=…`, `ecole2recte://agenda`, `ecole2recte://devoirs`, `ecole2recte://messages` analysés défensivement et gérés au démarrage comme par nouvel intent.
- Déclaration App Actions `actions.intent.OPEN_APP_FEATURE` et inventaire Agenda, Devoirs, Notes, Messages, avec variantes debug/release. Les anciens quatre raccourcis statiques ont été remplacés pour libérer les places des raccourcis dynamiques.
- Mode montrer : suppression des raccourcis dynamiques, anonymisation et désactivation des raccourcis épinglés. Nettoyage au retrait du compte ; changement de profil invalidant les anciens instantanés et empêchant une publication tardive du profil précédent.

### V4 — Documentation

- README mis à jour ; présent bilan et inventaire fichier par fichier ci-dessous.
- Aucun commit, push, merge, changement d'ApiSession, d'intervalle réseau ou de garde-fou d'écriture. Aucune requête vers ÉcoleDirecte exécutée pendant ce travail.

## Pas fait / limites

- **Compilation, tests Kotlin, lint et APK non vérifiés** : Bash ne démarre pas dans ce bac à sable. La référence de 557 tests fournie par le hub n'a pas été reproduite ici. Aucun résultat vert annoncé.
- Le cycle rouge/vert n'a pas pu être observé : les tests initiaux ont été écrits avant les implémentations, mais chaque tentative de lancement a échoué avant Gradle.
- Aucun test sur téléphone, tablette, pliable, lanceur ni Assistant exécuté. Les tests Glance sont ajoutés, non exécutés.
- Aucun lien de justification d'établissement inventé : les modèles/fixtures existants ne fournissent ni indication de justification en ligne ni URL d'établissement associée. L'écran reste en lecture seule ; cette branche conditionnelle devra être ajoutée si une donnée documentée est fournie.
- Les rappels reflètent le dernier état lu : une justification réalisée sur le site n'est connue qu'après actualisation. Les tâches locales peuvent être retardées par Android ; aucune heure exacte garantie.
- Android ne permet pas à l'application de retirer physiquement les icônes déjà épinglées par l'utilisateur : elles sont anonymisées puis désactivées, et le lanceur contrôle leur présentation.
- **Une réponse vocale telle que « quelle est ma moyenne en maths ? » n'est pas réalisable avec les App Actions publiques pour cette application. Seule l'ouverture directe des écrans est proposée.** La déclaration de capacité ne garantit pas sa disponibilité dans chaque version/langue de l'Assistant.

## Tests ajoutés et adaptés

**21 nouveaux tests unitaires déclarés, non exécutés :**

- `CurrentCoursePlannerTest.kt` : 8 tests (désactivation, annulations/données malformées, progression et rafraîchissement local, cours enchaînés, pause, fin de journée/week-end, changement d'horaire, confidentialité).
- `SchoolLifeLogicTest.kt` : 6 tests (fixture du parseur, durée institutionnelle, compteurs/durées absentes, mois/filtres, seuils et déduplication du rappel, variantes d'écran et widget, police et confidentialité).
- `ShortcutLogicTest.kt` : 5 tests (liens valides, inconnus/malformés, classement, résolution d'une matière et App Actions, stockage par profil/nettoyage).
- `SyncServiceTest.kt` : 2 tests supplémentaires (publication Vie scolaire demandée et absence d'appel pour module fermé) ; test initial enrichi pour le propriétaire de l'instantané et l'absence d'appel inutile.

**Tests instrumentés :** 3 tests Glance ajoutés dans `SchoolLifeWidgetTest.kt` (paliers, confidentialité, grande police). `ManifestOnDeviceTest.kt` adapté au septième widget et à la libération des places de raccourcis. Non exécutés.

`BuildVariantsTest.kt` et `NavigationTargetsTest.kt` adaptés au nouveau contrat App Actions, aux cibles debug/release et à leurs destinations.

## Vérifications réellement exécutées

Commandes tentées avant implémentation de V1, V2 et V3 :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh
```

Commande de clôture tentée :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest lintDebug assembleDebug
```

Même résultat dans tous les cas : code de sortie 1, arrêt au lancement de Bash, avant Gradle. Sortie représentative :

```text
0 [main] bash (28040) C:\Program Files\Git\usr\bin\bash.exe: *** fatal error - CreateFileMapping S-1-5-21-2892306765-1606266371-1694119132-1001.1, Win32 error 5. Terminating.
```

Contrôles locaux réussis, sans compilation :

- `git diff --check` : code 0, seulement avertissements de conversion LF/CRLF.
- Python/XML ElementTree : cinq XML modifiés/ajoutés bien formés ; fixture JSON valide ; fichiers de raccourcis identiques hormis le paquet debug/release. Cela ne valide pas leur sémantique Android.
- Décompte statique : 21 nouveaux tests unitaires déclarés. Ce n'est pas un décompte de tests exécutés.

## À vérifier sur le téléphone

- Installer après `testDebugUnitTest lintDebug assembleDebug` sur le hub ; compiler aussi les tests instrumentés et les exécuter.
- Cours en cours : permission refusée/accordée, progression, enchaînement, pause, annulation/changement d'horaire, fin de journée, week-end, redémarrage et économie de batterie.
- Android 26–35 : barre classique ; Android 36+ : style natif ou repli ; expiration réelle à la fin du cours. Aucun service au premier plan ni trafic réseau issu des tâches locales.
- Écran verrouillé et mode montrer pendant qu'une notification est déjà visible ; tuile sans divulgation sur écran verrouillé.
- Vie scolaire : portrait/paysage, téléphone/tablette/pliable, largeur 600/840 dp et police maximale ; filtres, retour, détails, compteurs et TalkBack.
- Widget 1×1 et paliers agrandis, police système et réglage Très grand, thèmes clair/sombre/noir, absence de données et mode montrer.
- Rappel 2/5/7 jours, absence justifiée entre deux lectures, pas de doublon après changement d'élève ; suppression de compte et module fermé.
- Raccourcis : classement après synchronisation, ouverture de la bonne matière, épinglage, anonymisation/désactivation selon le lanceur, restauration en sortant du mode montrer, déconnexion et plusieurs enfants.
- Liens valides/malformés à froid et à chaud, Notes/Agenda fermés par l'établissement, retour d'Agenda depuis Devoirs ; App Actions via les outils de test de l'Assistant, pour les deux variantes d'application.

## Inventaire fichier par fichier

Préfixe Kotlin principal : `app/src/main/java/fr/ecole2recte/app/`.

| Fichier | Modification |
| --- | --- |
| `sync/CurrentCoursePlanner.kt` | Nouveau planificateur pur et contenu discret. |
| `sync/CurrentCourseWorker.kt` | Travail local, notification, progression et expiration. |
| `sync/LocalFeaturePreferences.kt` | Options locales et propriétaire actif. |
| `sync/NextClassTileService.kt` | Tuile Prochain cours. |
| `domain/Models.kt` | Identité, date et durée des événements scolaires. |
| `domain/SchoolLifeLogic.kt` | Durées explicites, filtres, mois, rappel, décisions responsive. |
| `data/api/Parsers.kt` | Priorité à la durée institutionnelle explicite. |
| `data/EcoleDirecteRepository.kt` | Transmission des métadonnées scolaires au domaine. |
| `ui/more/SchoolLifeCard.kt` | Accès et écran dédié en lecture seule. |
| `ui/more/MoreScreen.kt` | Accès Vie scolaire soumis au module. |
| `widget/SchoolLifeWidget.kt` | Nouveau widget et instantané minimal, suivi des rappels. |
| `sync/AbsenceReminderWorker.kt` | Rappel local, confidentialité et déduplication. |
| `domain/DeepLinks.kt` | Analyse des liens et destinations App Actions. |
| `domain/SubjectVisits.kt` | Consultations par profil, classement et résolution d'identité. |
| `sync/AppShortcuts.kt` | Publication, épinglage, anonymisation et nettoyage. |
| `MainActivity.kt` | Intent initial et nouveaux intents. |
| `AppContainer.kt` | Cible de matière et cycle de vie des données locales. |
| `ui/AppRoot.kt` | Nettoyage à la déconnexion/changement de profil. |
| `ui/navigation/MainScaffold.kt` | Destination Vie scolaire et consommation des demandes. |
| `ui/grades/GradesView.kt` | Matière ciblée et enregistrement des consultations. |
| `ui/home/HomeScreen.kt` | Propriétaire et matières dans l'instantané. |
| `sync/SyncStorage.kt` | Sérialisation rétrocompatible des nouvelles métadonnées. |
| `sync/SyncService.kt` | Publication conditionnelle Vie scolaire et métadonnées des raccourcis. |
| `widget/Widgets.kt` | Actualisation des nouvelles fonctions après publication. |
| `settings/AppSettings.kt` | Réaction immédiate au mode montrer et à la taille du texte. |
| `ui/more/NotificationsSection.kt` | Options locales, permission et épinglage. |

Autres fichiers :

| Fichier | Modification |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Liens profonds, tuile et récepteur du widget. |
| `app/src/main/res/xml/school_life_widget_info.xml` | Fournisseur 1×1 redimensionnable et aperçu. |
| `app/src/main/res/xml/shortcuts.xml` | Capacité et inventaire App Actions release. |
| `app/src/debug/res/xml/shortcuts.xml` | Capacité et inventaire App Actions debug. |
| `app/src/main/res/values/action_features.xml` | Libellés et synonymes français. |
| `app/src/test/java/fr/ecole2recte/app/sync/CurrentCoursePlannerTest.kt` | Nouveaux tests V1. |
| `app/src/test/java/fr/ecole2recte/app/domain/SchoolLifeLogicTest.kt` | Nouveaux tests V2 et responsive. |
| `app/src/test/resources/captures/schoollife_duration.json` | Fixture sans données réelles. |
| `app/src/test/java/fr/ecole2recte/app/domain/ShortcutLogicTest.kt` | Nouveaux tests V3. |
| `app/src/test/java/fr/ecole2recte/app/sync/SyncServiceTest.kt` | Publication et respect du module scolaire. |
| `app/src/test/java/fr/ecole2recte/app/BuildVariantsTest.kt` | Contrat des paquets App Actions. |
| `app/src/test/java/fr/ecole2recte/app/ui/NavigationTargetsTest.kt` | Contrat des destinations App Actions. |
| `app/src/androidTest/java/fr/ecole2recte/app/SchoolLifeWidgetTest.kt` | Nouveaux tests Glance. |
| `app/src/androidTest/java/fr/ecole2recte/app/ManifestOnDeviceTest.kt` | Septième widget et raccourcis dynamiques. |
| `README.md` | Fonctions, confidentialité et limites actualisées. |
| `RESULTAT-VIE-NOTIF.md` | Bilan, commandes réelles et vérifications restantes. |

## Correction après validation du hub — tentative 1

- Le hub a constaté un échec de `compileDebugKotlin` dans `AppShortcuts.kt` : `ShortcutManagerCompat.enableShortcuts` attend une liste de `ShortcutInfoCompat`, mais recevait une liste d'identifiants `String`.
- L'appel reçoit désormais les objets des raccourcis de matières et, lorsque permis, celui de « Prochain cours ». La construction de ce dernier est partagée avec l'épinglage, avec la même destination Agenda.
- Aucun test modifié ni ajouté : la compilation existante détecte cette incompatibilité de types.
- Commande exacte du hub tentée avant et après le correctif : `bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh .`. Bash échoue toujours avant Gradle (`CreateFileMapping`, `Win32 error 5`, code de sortie 1). La compilation et les tests restent donc à relancer par le hub ; aucun succès annoncé.
- À vérifier sur téléphone : réactivation des raccourcis de matières et de « Prochain cours » en quittant le mode montrer.

Résumé : V1 à V3 implémentés, V4 documenté. Validation Gradle et validation sur téléphone restent à faire par le hub ; aucun test Kotlin n'a tourné dans ce bac à sable.
