# Plan — suite : widgets, vie scolaire, cours en cours, raccourcis, simulateur, projection, classe, responsive

Complète `PLAN-MOYENNE.md` (voies 1 et 2, déjà en cours). Mêmes **règles communes** que celui-ci (hors réseau, test d'abord,
un commit par lot, jamais de push ni de merge, vérifier avant d'affirmer, interface en français, ne pas élargir, fichier `RESULTAT-<voie>.md`).
Commande de test : `bash C:/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh <dossier-du-worktree> [tâches]` (référence avant ces voies : 530 tests, 0 échec).

## Règle transversale — l'application s'adapte à la taille de l'écran
Valable pour **tout** ce qui est écrit dans ces voies, écran comme widget :
- aucune largeur ou hauteur fixe en dp pour un contenu ; mises en page par classes de taille (compact < 600 dp, moyen 600–840 dp, étendu > 840 dp) ;
- une décision de mise en page = une **fonction pure** (entrée : largeur/hauteur en dp, orientation ; sortie : variante) **testée à l'unitaire** ;
- texte : rester lisible à l'échelle de police maximale du réglage « Taille du texte » (pas de coupure silencieuse, `maxLines` + ellipse justifiés) ;
- portrait et paysage, téléphone, tablette, pliable ouvert/fermé ; barre système et encoches respectées ;
- widgets : chaque widget définit des **paliers de taille** (1×1, 2×1, 2×2, 4×2…) et reste lisible à chacun ; vérifier avec les outils `glance-appwidget-testing` déjà présents.
Les widgets sont en **Glance** (`androidx.glance:glance-appwidget:1.1.1`) : suivre la technique de `widget/Widgets.kt`, `WidgetUi.kt`, `WidgetTheme.kt`, `WidgetPrivacy.kt` (mode « montrer » = •••, couleurs du thème).

---

# VOIE 3 — `widgets` (choix 1, 2, 3)

## Lot W1 — Widget 1×1 « Moyenne » personnalisable (choix 1)
- Nouveau récepteur Glance `MeanWidgetReceiver`, pose en **1×1** (`targetCellWidth/Height = 1`), redimensionnable jusqu'à 2×1 (affiche alors en plus la tendance et la période).
- **Activité de configuration** à l'ajout (`android:configure`, reconfigurable depuis le widget), réglages **par instance** (`appWidgetId`), plusieurs instances possibles :
  source = moyenne générale / une matière / moyenne de la classe / écart à un objectif (saisi) ; période = courante / précédente ; titre court optionnel (≤ 8 caractères) ;
  seuils de couleur (défauts : vert ≥ 14, orange ≥ 10, rouge en dessous), tous modifiables ; flèche de tendance oui/non.
- Données : le dernier instantané publié par la synchro (voir comment `GradesWidget` le lit dans `SyncStorage`) ; **aucune requête réseau** depuis le widget. Isoler l'accès à la moyenne dans **une seule fonction** (`MeanWidgetData.read(...)`) : la voie moyenne la fera évoluer.
- Mode « montrer à quelqu'un » : •••. Données anciennes : pastille discrète « il y a X h ». Un toucher ouvre la matière concernée (extra `tab=grades&subject=...`).
- Tests unitaires : choix de la source, couleur par seuil (bornes incluses), formatage (virgule, décimales du réglage global), vie privée, donnée absente (« – »), configuration sérialisée par `appWidgetId` puis supprimée à la suppression du widget.

## Lot W2 — Pack de widgets 1×1 (choix 2)
Un squelette commun `OneByOne` (icône, valeur, légende) et quatre widgets 1×1 qui l'utilisent, chacun redimensionnable avec un contenu plus riche aux paliers supérieurs :
1. **Prochain cours** : matière + heure de début, compte à rebours « dans 25 min », salle au palier 2×1 ; cours annulés ignorés.
2. **Devoirs restants** : nombre à faire, rouge s'il y en a pour demain, au palier 2×1 la matière du premier.
3. **Dernière note** : note /barème + matière, pastille « Nouveau » pendant 3 jours, écart à la classe au palier 2×1.
4. **Messages non lus** : nombre, discret en mode « montrer ».
5. **Compte à rebours du prochain contrôle** : « J−3 » + matière (le calcul existe dans `domain/ExamCountdown.kt`).
Chacun : configuration minimale utile (profil/enfant en compte famille), mêmes règles de vie privée, tests de la logique pure.

## Lot W3 — Widget « Objectifs » + tuile (choix 3)
- Widget 2×2 minimum : une **jauge par matière** (moyenne actuelle vs objectif), tri par écart, au plus 4 matières au palier 2×2, davantage aux paliers supérieurs.
- Objectifs stockés dans un petit `GoalStore` (par profil et par matière), modifiables dans la configuration du widget et dans le détail d'une matière (champ « Objectif »).
- Le texte de la jauge dit toujours les deux nombres ; jamais la couleur seule.
- **Tuile Réglages rapides** (`TileService`) « Moyenne » : affiche la moyenne générale, un toucher ouvre Notes ; état « verrouillé » respecté.
- Tests : tri, jauge (0–100 %, objectif absent, moyenne > objectif), stockage.

## Lot W4 — Clôture
`testDebugUnitTest lintDebug assembleDebug`, `RESULTAT-WIDGETS.md`, mise à jour de la section Widgets du `README.md`.

---

# VOIE 4 — `vie-notif-raccourcis` (choix 10, 11, 14)

## Lot V1 — Notification « cours en cours » + tuile (choix 10)
- Option **désactivée par défaut** (Plus > Notifications) : une notification persistante pendant les heures de cours, avec **barre de progression** du cours, matière, salle, « ensuite : … à HH:MM ». Aucune notification hors cours.
- Pas de service au premier plan : mises à jour planifiées aux bornes des cours (alarme exacte non requise ; `WorkManager`/alarme inexacte acceptable), arrêt propre le soir et le week-end. Sur Android récent, utiliser le style de progression natif **uniquement si disponible** (garde `Build.VERSION.SDK_INT`), sinon barre classique.
- Contenu discret sur l'écran verrouillé (comme les autres notifications) ; respect du mode « montrer ».
- Tuile Réglages rapides « Prochain cours » (`TileService`) : libellé = prochain cours, toucher = Agenda.
- Logique de planification **pure** dans `sync/` sur le modèle de `NotificationPlanner` + tests : cours annulés, cours enchaînés, pause, fin de journée, changement d'horaire.

## Lot V2 — Vie scolaire (choix 11)
- Un `SchoolLifeCard` existe dans Plus : le promouvoir en **écran dédié** : absences, retards, sanctions, encouragements, par mois ; filtres ; compteurs (heures d'absence non justifiées) ; détail d'un événement (motif, durée telle que comptée par l'établissement, justifié ou non).
- Widget 1×1 « Vie scolaire » (non justifiées) au même gabarit que les autres (voir voie widgets : en attendant son squelette, rester autonome et simple).
- Rappel optionnel (désactivé par défaut) « justificatif attendu » quand une absence reste non justifiée depuis N jours (N choisi : 2, 5, 7).
- **Lecture seule.** Aucune justification en ligne (le service n'est pas documenté publiquement) ; si l'établissement indique qu'elle est possible, afficher seulement « À justifier sur le site de l'établissement » avec le lien existant.
- Respecter `Modules.kt` (module fermé par l'établissement → onglet absent). Tests : parseur sur fixture, compteurs, regroupement par mois, rappel.

## Lot V3 — Raccourcis (choix 14)
- **Raccourcis dynamiques** : les 4 matières les plus consultées/récentes → Notes de la matière ; raccourci épinglable « Prochain cours ». `ShortcutManagerCompat`, mis à jour après chaque synchronisation, supprimés à la déconnexion, masqués en mode « montrer ».
- **Liens profonds** `ecole2recte://notes?matiere=…`, `ecole2recte://agenda`, `ecole2recte://devoirs`, `ecole2recte://messages`, gérés par `MainActivity` (valeurs invalides → ignorées, jamais de plantage).
- **App Actions** : déclarer la capacité `actions.intent.OPEN_APP_FEATURE` (Agenda, Devoirs, Notes, Messages) dans `shortcuts.xml`.
- **Limite à écrire dans `RESULTAT`** : une réponse vocale du type « quelle est ma moyenne en maths ? » n'est pas réalisable avec les App Actions publiques pour une app de ce type ; on offre l'ouverture directe de l'écran voulu. Ne pas promettre davantage.
- Tests : analyse des liens profonds (valides, inconnus, malformés), sélection des matières, nettoyage.

## Lot V4 — Clôture
`testDebugUnitTest lintDebug assembleDebug`, `RESULTAT-VIE-NOTIF.md`, section du `README.md`.

---

# VOIE 5 — `simulateur-projection-classe` (choix 6, 7, 8) — **à lancer après la voie 1** (branche créée depuis `moyenne-fiable`)

## Lot S1 — Scénarios du simulateur (choix 6)
Sur le moteur `AverageEngine` de la voie 1 : plusieurs devoirs hypothétiques (chacun avec note, barème, coefficient), retirer ou modifier une note réelle pour voir l'effet, effet sur la matière **et** sur la générale ; **scénarios sauvegardés** (nom, liste de modifications) par profil et période ; interface en liste de cartes, clavier numérique, résultat toujours étiqueté « estimation ».
## Lot S2 — Moyenne annuelle et projection (choix 7)
Moyenne annuelle (moyenne des périodes selon leurs coefficients quand connus, sinon à plat, dit clairement) ; projection de fin d'année : **meilleur cas** (20 aux notes à venir), **pire cas** (0), **cas probable** (moyenne actuelle), pour un nombre d'évaluations restantes choisi ; jamais présenté comme une prédiction.
## Lot S3 — Position dans la classe (choix 8)
Rang et effectif (`rang`, `effectif`) quand l'établissement les fournit, sinon masqués ; histogramme minimum / classe / moi sur la matière et sur la générale ; médiane si présente. Accessibilité : phrase lue par lecteur d'écran (comme `GradeCurves.describe`).
Tests : logique pure de chaque lot ; `docs/MOYENNES.md` et `AverageMethod.kt` mis à jour **ensemble**.

---

# VOIE 6 — `responsive` (choix 15) — **à lancer en dernier**, après fusion des autres voies
- `WindowSizeClass` (dépendance `androidx.compose.material3:material3-window-size-class`, version alignée sur le BOM) ; `LayoutClass` pur et testé.
- Navigation : barre basse en compact, **rail** en moyen, **rail étendu** en grand ; **deux volets** (liste + détail) pour Notes, Messages, Devoirs à partir de 600 dp ; emploi du temps : plus de jours visibles en largeur ; pliables : posture à plat/table respectée (pas de contenu sous la charnière).
- Paysage téléphone : pas de barre qui mange la moitié de l'écran ; échelle de police maximale testée sans troncature.
- Couleurs et **icônes de matière personnalisables** (palette contrôlée, contrastes vérifiés comme les thèmes existants), gardées par profil.
- Tests de logique de mise en page + tests instrumentés si un appareil est branché (sinon dire qu'ils n'ont pas tourné).
