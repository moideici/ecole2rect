# Plan de patch — retours du premier test sur téléphone

Mis à jour après l'application des correctifs et la relecture critique (`RAPPORT-CRITIQUE.md`).
**130 tests unitaires et 12 tests sur le téléphone passent, `assembleDebug` OK.** Rien de ce qui suit n'a encore été vu sur le téléphone
(il a été débranché avant l'installation) : « fait » veut dire écrit, compilé et testé, pas essayé.

## 1. Tes retours

| # | Retour | État | Ce qui a été fait | À vérifier sur le téléphone |
|---|---|---|---|---|
| 1 | Emploi du temps sans heures, pauses ni doubles heures | **fait** | Vue jour en **frise horaire** : axe des heures, chaque cours à sa vraie hauteur, **doubles heures** fondues en un bloc (« Double heure · 1 h 50 »), **pauses nommées** (récréation, pause, pause déjeuner, heure libre) avec leur durée, cours simultanés côte à côte, trait « maintenant », cours passés atténués | la frise avec ton vrai emploi du temps |
| 2 | Notes : « par matière » vide, moyenne vide | **fait, cause identifiée** | La moyenne générale est lue dans `ensembleMatieres` ; la **période** est choisie par les dates (jamais « la dernière de la liste », jamais la période « Année ») ; sinon moyenne calculée à partir des matières, puis des notes. La relecture critique a trouvé la même cause de son côté (C01) | les notes avec ton vrai compte |
| 3 | Notifications personnalisables | **fait** | Par famille : emploi du temps (annulé, salle, professeur, horaire, rétabli), nouvelles notes, messages, devoirs pour demain ; **mode nuit** 22 h–7 h ; fréquence 2 / 4 / 8 h ; chaque changement n'est notifié qu'une fois | l'activation et la permission |
| 4 | Gestion des icônes | **fait** | **Icône d'application** (lapin, adaptative + version monochrome Android 13), **icône par matière** (maths, langues, histoire-géo, sciences, SVT, sport, arts…) dans la frise, les devoirs, les notes et la carte du prochain cours | le rendu de l'icône sur ton lanceur |
| 5 | Widgets : pas assez, moches, pas adaptés à la taille, à faire « sauter aux yeux » | **refait, 6 widgets** | **Prochain cours** et **Messages** en indigo plein avec de très gros caractères ; **Ma journée** (frise du jour, cours en cours en surbrillance, pauses), **Semaine**, **Devoirs** (l'urgent en rouge, évaluations signalées), **Notes** (moyenne en grand, chaque note dans sa pastille). **Adaptés à la taille** : 5 paliers, le contenu change (plus de lignes, de détails, « ensuite »). Clairs le jour, sombres la nuit. « dans 25 min » est calculé à l'affichage. Cause du widget qui ne s'affichait pas : contexte Compose au lieu du contexte Glance. Remplis à chaque ouverture de l'app | **les 6 se dessinent sur ton téléphone à 4 tailles, avec et sans données (test)** ; reste à les poser sur ton écran d'accueil et juger le visuel |
| 6 | Mode parent plus complet | **fait en partie** | **« Ma famille »** à l'accueil : une ligne par enfant (prochain cours, devoirs pour demain, nouvelles notes, moyenne), un toucher change d'enfant ; **Vie scolaire** (absences, retards, sanctions, encouragements, durée comptée par l'établissement) dans Plus | un compte parent réel |
| 7 | Traitement des liens | **fait** | Liens web, `www.`, courriels et numéros de téléphone cliquables dans les devoirs, le contenu de cours et les messages | un devoir ou un message avec un lien |
| 8 | Accueil : lien entre prochain cours et journée | **fait** | Le bloc « Aujourd'hui » est la **même frise que l'agenda**, resserrée ; le prochain cours ouvre l'agenda | l'accueil un jour de cours |
| 9 | Accueil : « À savoir » | **fait** | Ordre : cours annulé > cours modifié > évaluation > message > note. **Note : matière en haut à gauche, note avec coefficient en grand à droite**, détail en petit (l'icône dit déjà « note ») | — |

Pas fait, volontairement : téléchargement des pièces jointes (aucune ouverture possible pour l'instant),
envoi de messages, écriture « devoir fait » vers ÉcoleDirecte (voir §3).

## 1 bis. Tests sur le téléphone

`./gradlew connectedDebugAndroidTest` (téléphone branché). **12 tests, 0 échec, 4 ignorés** (ils attendent qu'un compte soit connecté) :
- l'écran de connexion et ses règles, la frise (doubles heures, pauses, « maintenant », annulé) ;
- l'icône, les 6 widgets déclarés avec aperçu, les 3 raccourcis ;
- **chaque widget dessiné à 4 tailles avec et sans données** (un plantage au dessin ferait échouer le test) ;
- 4 parcours **en lecture seule** sur le vrai compte : ouvrir les 5 onglets, l'agenda, les notes (moyenne et matières non vides), les réglages de Plus. Ils ne cochent aucun devoir, n'ouvrent aucun message (cela le marque lu), n'envoient rien. Le compte se connecte **à la main** sur le téléphone ; les tests n'écrivent jamais d'identifiant. Option `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` pour ne pas désinstaller l'app (et donc ne pas perdre la connexion) à la fin.

## 2. Ce que la critique a trouvé (35 constats : 4 bloquants, 23 importants, 8 de confort)

Déjà traités par le patch : **C01** (notes), **C03** en partie (frise, pauses, doubles heures, cours
simultanés ; la cause réelle d'un éventuel « sans heures » reste à confirmer avec une vraie réponse),
**C04** (widgets), **C05** (icône), **C20** (vie scolaire en lecture), **C22** en partie (vue famille).

Le rapport complet est dans `RAPPORT-CRITIQUE.md`. Son verdict : l'architecture est saine, mais
l'app n'a **jamais vu une vraie réponse de l'API** (tous les tests utilisent des données inventées),
et le vrai risque est la **fréquence des connexions** (une connexion complète à chaque lancement, une
toutes les 4 h en arrière-plan) plus le statut « client non officiel ».

## 3. À décider avec toi avant d'appliquer quoi que ce soit

Le sous-agent d'application n'est **pas** lancé : il ne fera que ce que tu auras retenu.
Pour chaque ligne : **oui / non / plus tard**.

| N° | Action proposée par la critique | Effort | Pourquoi | Risque ou réserve |
|---|---|---|---|---|
| A | **Outil de capture anonymisée** (clés et types, valeurs masquées) + vraies fixtures de test | moyen | C'est la seule façon d'arrêter de deviner les formats de l'API (C02, C03) | aucune donnée d'élève ne sort de l'appareil |
| B | **Un seul dépôt par profil**, état de session thread-safe, pastilles liées au profil, rafraîchissement à la reprise (C07, C08, C09, C10) | petit à moyen | aujourd'hui jusqu'à 7 dépôts, donc des requêtes en double ; pastilles qui peuvent rester sur l'ancien compte | — |
| C | **Conserver et réutiliser le jeton** ; arrêter la synchro après une question de sécurité non résolue ; ajouter de la gigue (C06, C13, C28) | moyen | meilleure protection contre le blocage du compte | la durée de vie réelle du jeton est à vérifier |
| D | **Protéger le stockage** : verrou sur le magasin de comptes, ne pas écraser en cas d'échec de déchiffrement, cache écrit seulement après lecture utile, cache chiffré, `FLAG_SECURE` en option (C14, C11, C24) | moyen | évite de perdre tous les comptes sans message | ne **pas** lier la clé à la biométrie (risque de tout perdre) |
| E | **CI** : lancer les tests et le lint, build release signé (C15) | petit | la CI ne lance aujourd'hui aucun test | demande une clé de signature en secret GitHub |
| F | **Respecter les modules ouverts par l'établissement** (C21) | moyen | évite des appels voués à l'échec et des onglets vides | — |
| G | **Pièces jointes téléchargeables** (C19) | moyen | un « voir le PDF joint » est aujourd'hui un cul-de-sac | ouvrir un fichier = confirmation à l'utilisateur |
| H | **Notifications privées** : contenu masqué sur l'écran verrouillé, enfant nommé, détection sur toutes les notes, tous les enfants (C23, C13) | petit à moyen | une note ne doit pas s'afficher écran verrouillé | — |
| I | **Sélecteur de période, appréciations, valeurs « Disp/NE »** dans les notes (C17) | moyen | aujourd'hui « Abs » s'affiche pour toute valeur non numérique | — |
| J | **Avertissement « application non officielle »** au premier lancement, README à jour (C27, C33) | petit | honnêteté envers l'utilisateur et ses enfants | — |
| K | **Devoir fait synchronisé** avec ÉcoleDirecte (C16) | moyen | l'état diffère entre l'app et le site | écriture réelle : seulement avec une capture et un compte de test |
| L | **Envoi de messages** (C18) | grand | fonction attendue | écriture réelle, à ne tenter qu'en toute fin |

### À ne surtout pas faire (rappel de la critique)
Aucune boucle de reconnexion après un refus ; ne jamais deviner une réponse de sécurité ; ne pas
descendre sous 4 h de synchro ni lancer de requêtes en rafale ; pas de préchargement massif ; pas de
User-Agent aléatoire ; pas d'écriture vers ÉcoleDirecte sans vérification ; pas de serveur
intermédiaire ni de télémétrie ; pas de nouvelles animations ou réglages tant que les données ne sont
pas justes.

## 3 bis. Cahier des charges du sous-agent d'application

Il ne traite que les lettres validées, **dans cet ordre**, un commit par lettre,
`testDebugUnitTest` + `assembleDebug` verts avant chaque commit, jamais de requête réelle dans les
tests, et il s'arrête dès qu'une lettre demande une écriture vers ÉcoleDirecte.

**A — Captures anonymisées et vraies fixtures** *(fichiers : nouveau `diagnostic/ShapeDescriber.kt`, `ui/more/` carte « Diagnostic des données », `app/src/test/resources/captures/`, `data/api/Parsers.kt`)*
1. Fonction pure `describeShape(json)` : garde les clés, les types, les tailles de tableau et les longueurs ; remplace toute valeur texte ou nombre par `<texte:12>` / `<nombre>`. Test : aucune valeur d'entrée ne ressort.
2. Dans Plus : « Diagnostic des données », qui affiche et copie la forme de chaque réponse en cache (emploi du temps, notes, devoirs, messages, vie scolaire, connexion) ; aucune valeur, aucun nom.
3. Transformer les formes que tu colles en fixtures sous `src/test/resources/captures/` et réécrire les tests de lecteurs pour qu'ils les chargent ; garder les tests actuels en plus.
4. Compter les entrées rejetées par chaque lecteur (aujourd'hui elles disparaissent en silence) et les afficher dans le diagnostic.
*Réussite* : un test par lecteur sur une forme réelle ; le diagnostic ne contient aucune donnée personnelle.

**B — Un seul dépôt par profil, état thread-safe, reprise** *(fichiers : `AppContainer.kt`, `ui/common/AppViewModel.kt`, `ui/navigation/MainScaffold.kt`, `data/EcoleDirecteClient.kt`, `ui/common/UiState.kt`)*
1. `AppContainer` garde une table `profile.key → EcoleDirecteRepository` ; `rememberSchoolRepository()` la lit au lieu de créer une instance par écran.
2. Clé du `BadgesViewModel` = `profile.key` (plus de pastilles de l'ancien compte).
3. `EcoleDirecteClient` : verrou autour des cookies, du jeton et du GTK ; au plus 2 appels de données en même temps.
4. Rafraîchir un écran quand l'app revient au premier plan **si la dernière lecture a plus de 10 minutes** ; jamais en boucle.
*Tests* : deux écrans demandent la même ressource → une seule requête ; changer de compte → pastilles du nouveau compte.

**C — Moins de connexions** *(fichiers : `session/SessionViewModel.kt`, `data/SecureCredentialStore.kt`, `sync/SyncService.kt`, `sync/SyncWorker.kt`, `data/EcoleDirecteClient.kt`)*
1. Garder le jeton, les cookies et le User-Agent (chiffrés comme le mot de passe), les rejouer au lancement ; ne se reconnecter que sur 520, 525 ou 526. **Avant de coder** : mesurer sur ton téléphone la durée de vie réelle du jeton ; si elle est de quelques minutes, ne pas aller plus loin et le dire.
2. Synchro : s'arrêter et expliquer après `NeedsInteraction`, après 3 échecs de suite, et sur le code 517 (version d'API refusée) ; ajouter une gigue aléatoire au premier passage.
3. Version d'API (`4.75.0`) lue dans un réglage avec valeur par défaut.
*Interdit* : descendre sous 4 h, relancer une connexion refusée, changer le User-Agent.

**D — Protéger ce qui est stocké** *(fichiers : `data/SecureCredentialStore.kt`, `data/cache/ResponseCache.kt`, `data/EcoleDirecteRepository.kt`, `MainActivity.kt`, `security/AppLock.kt`)*
1. Verrou (`synchronized`) sur toutes les opérations du magasin de comptes ; si le déchiffrement échoue, **ne rien écrire** et afficher « comptes illisibles, reconnecte-toi ».
2. La synchro ne remplace plus un compte entier : elle ne met à jour que la réponse de confiance.
3. Le cache ne remplace pas un bon fichier par une réponse vide ou inutilisable ; il garde la cause du repli (réseau, compte bloqué…) pour l'afficher.
4. Chiffrer les fichiers du cache avec la clé Keystore existante.
5. Option « masquer l'aperçu dans les applications récentes » (`FLAG_SECURE`) quand le verrou est actif.
*Interdit* : lier la clé à la biométrie (un changement d'empreinte ferait perdre tous les comptes).

**E — CI** *(fichier : `.github/workflows/android.yml`)* `gradle testDebugUnitTest lintDebug assembleDebug` ; build release signé seulement si les secrets GitHub existent ; rapport de tests en artefact.

**F — Modules de l'établissement** *(fichiers : `data/EcoleDirecteClient.kt` extraction des comptes, `data/SecureCredentialStore.kt`, `ui/navigation/MainScaffold.kt`, `data/EcoleDirecteRepository.kt`)* Lire `modules` à la connexion, les garder avec le profil ; masquer l'onglet ou la carte d'un module fermé ; ne pas appeler un module fermé (cela compte aussi comme trafic).

**G — Pièces jointes** *(fichiers : `data/api/EcoleDirecteApi.kt` téléchargement, `ui/homework/HomeworkView.kt`, `ui/messages/MessagesView.kt`, `AndroidManifest.xml` + `res/xml/file_paths.xml`)* Télécharger dans le cache privé, **demander confirmation** avant d'ouvrir, ouvrir avec `FileProvider` et `ACTION_VIEW`, effacer à la fermeture ; lecture seule, aucune requête d'envoi.

**H — Notifications privées** *(fichiers : `sync/AndroidNotifier.kt`, `sync/NotificationPlanner.kt`, `sync/SyncService.kt`)* Version publique masquée sur l'écran verrouillé (`VISIBILITY_PRIVATE`), nom de l'enfant en sous-titre, détection sur **toutes** les notes et non les 6 dernières, un passage par enfant du compte, importance basse pour devoirs et cours, normale pour messages.

**I — Notes complètes** *(fichiers : `data/api/Parsers.kt`, `data/EcoleDirecteRepository.kt`, `ui/grades/GradesView.kt`, `domain/Models.kt`)* Choix de la période (puces), appréciations, valeurs textuelles brutes (Disp, NE, Abs) au lieu de « Abs » partout, moyenne la plus basse et la plus haute de la classe.

**J — Transparence** *(fichiers : `ui/login/LoginScreen.kt`, `README.md`)* Écran du premier lancement : « application non officielle, non affiliée ; ton identifiant n'est utilisé que pour te connecter et reste chiffré sur ce téléphone », à accepter une fois ; README court avec « Limites connues » ; déplacer la maquette web dans `docs/maquette/`.

**K — Devoir fait synchronisé** *(fichiers : `data/api/EcoleDirecteApi.kt`, `data/EcoleDirecteRepository.kt`)* **Seulement après un essai à la main** : écrire l'appel `PUT cahierdetexte.awp` (`idDevoirsEffectues` / `idDevoirsNonEffectues`), l'activer derrière un réglage « synchroniser avec ÉcoleDirecte » désactivé par défaut, confirmation à la première utilisation, retour arrière possible. Le sous-agent écrit le code et les tests contre un faux serveur ; **il ne lance aucune écriture réelle**.

**L — Envoi de messages** *(grand, à ne pas commencer)* Écran de rédaction, confirmation avant envoi, aucun envoi sans toucher explicite ; même règle que K.

## 4. Suite
1. Rebrancher le téléphone : j'installe la copie de test et je regarde le journal.
2. Tu essaies : emploi du temps, notes, widgets, compte parent.
3. Tu me dis oui / non / plus tard pour A à L ; un sous-agent applique seulement les « oui ».

## 5. Lot 1 — recherche, messages, devoirs, rappel du soir, notes

Écrit, compilé et testé (343 tests unitaires dont 55 nouveaux, `assembleDebug` et `lintDebug` OK). **Rien de ce lot n'a été essayé
sur un téléphone** (non branché) : « fait » veut dire écrit et testé, pas vu à l'écran. Aucune nouvelle écriture vers ÉcoleDirecte,
aucune télémétrie, aucune requête de plus : la recherche ne lit que le cache.

| # | Demande | État | Ce qui a été fait | À vérifier sur le téléphone |
|---|---|---|---|---|
| 1 | Recherche globale | **fait** | `domain/Search.kt` (logique pure, casse et accents ignorés, tous les mots doivent se retrouver) ; `ui/search/SearchScreen.kt` ; barre « Rechercher » à l'accueil et dans Plus ; raccourci d'application « Rechercher » ; résultats groupés (devoirs, messages, notes, cours), un toucher ouvre l'onglet. Elle lit uniquement les réponses **déjà enregistrées** (`SchoolRepository.searchCorpus`), jamais le réseau | la barre, le clavier, un résultat qui ouvre le bon onglet |
| 2 | Lu / non lu en local | **fait** | Icône sur chaque ligne de message ; marque gardée par profil (`data/LocalMarks.kt`), jamais envoyée ; ouvrir un message (qui le marque lu chez ÉcoleDirecte, comportement inchangé) efface la marque. **Limite** : la pastille de l'onglet, les notifications et les widgets suivent toujours ÉcoleDirecte, pas la marque locale | l'icône et la persistance |
| 3 | Filtres des devoirs | **fait** | Puces « Tous / À faire / Faits / Contrôles » avec compteurs, mémorisées par profil (`ui/homework/HomeworkFilter.kt`). Défaut : « Tous » (comportement d'avant) | les puces |
| 4 | Rappel du soir | **fait** | `NotificationRules.eveningHour` (16 à 20 h, défaut 18 h), réglage dans Plus > notifications. `NotificationPlanner.plan(..., hour)` : avant l'heure, ni notification ni mémorisation ; au premier passage après l'heure, une notification, puis plus rien pour ce devoir. Aucun nouveau job ni réveil exact : l'heure n'est donc **pas garantie** (premier passage de la synchro, toutes les 4 h ou plus), et le mode nuit 22 h–7 h reste prioritaire | un rappel réel un soir |
| 5 | Raccourcis et ouverture depuis une notification | **vérifié, un ajout** | Agenda, Devoirs, Messages existaient déjà ; ajout de « Rechercher » (4 raccourcis, les deux `shortcuts.xml` et leur test restent d'accord). Les notifications ouvraient déjà le bon onglet (`EventKind.tab` → extra `tab` → `pendingTab`) : un test garantit que chaque cible existe | un raccourci et une notification |
| 6a | Simulateur | **fait** | Carte « Simulateur » dans Notes (matière au choix) et dans chaque matière dépliée : « pour atteindre X » (hors d'atteinte / déjà acquis dits clairement) et « si j'obtiens Y » ; logique pure `domain/GradeSimulation.kt` | le clavier décimal |
| 6b | « Comment la moyenne est calculée » | **fait** | Écran (lien dans Notes et dans Plus) **et** `docs/MOYENNES.md`, même texte, un test vérifie qu'ils ne divergent pas. L'écran dit « officielle » ou « calculée par l'app (peut différer) » pour la moyenne générale (`AverageSource`) et pour chaque matière | la lisibilité |
| 6c | Courbes | **fait** | `LineChart` en Canvas, sans bibliothèque : moyennes officielles par période, et par matière (moyenne cumulée + chaque note), valeurs écrites, carrés pour les notes, description pour lecteur d'écran, couleurs du thème clair/sombre | le rendu clair et sombre |
| 6d | Comparaison à la classe | **fait** | « ▲ +1,5 vs classe » / « ▼ −2,0 vs classe » sous chaque note quand la moyenne de la classe existe | — |
| 6e | Notes « Nouveau » | **fait** | Pastille pendant 3 jours après la première vue par l'app, par profil ; le **tout premier passage** d'un profil ne marque rien (sinon tout l'historique serait nouveau) ; horloge injectée dans les tests. La « première vue » est la première ouverture de l'écran Notes qui montre la note | la pastille après une vraie nouvelle note |
| 6f | Moyenne par période | **déjà là, complété** | Les puces de période et le choix par les dates existaient (lot I) et restent le comportement par défaut ; chaque puce affiche maintenant sa moyenne générale quand elle est connue | — |

Pas fait, volontairement : ouvrir directement le devoir ou le message trouvé par la recherche (on ouvre l'onglet), prise en compte des
marques « lu » locales dans la pastille de l'onglet, recherche dans le contenu des messages (seuls l'expéditeur et l'objet sont en cache),
simulateur de la moyenne générale (les coefficients de matière ne sont pas toujours connus).

## 6. Lot 2 — agenda, exports, contrôles, cours déplacés

Écrit, compilé et testé (428 tests unitaires dont 85 nouveaux, `assembleDebug` et `lintDebug` OK ; un test
instrumenté du dessin PDF ajouté, **non lancé** : téléphone non branché). **Rien de ce lot n'a été essayé sur un
téléphone** : « fait » veut dire écrit et testé, pas vu à l'écran (le pincement, la frise en semaine et le
dessin du PDF surtout). Aucune écriture vers ÉcoleDirecte, aucune télémétrie, aucune permission ajoutée,
aucune requête de plus.

| # | Demande | État | Ce qui a été fait | À vérifier sur le téléphone |
|---|---|---|---|---|
| 1 | Vue semaine avec zoom | **fait** | Une grille « semaine » existait déjà ; elle est refaite : `ui/schedule/WeekView.kt` + `WeekLayout.kt` (colonnes par jour, **une frise horaire commune** via `buildDayTimeline`, doubles heures fondues, cours simultanés côte à côte, coloré par matière, annulé barré et atténué, modifié encadré en rouge, trait « maintenant »). **Zoom** de 50 à 300 % : pincement à deux doigts (un doigt n'est jamais consommé) ou boutons loupe, **réglage mémorisé** avec le choix jour / semaine (`AppSettings`), appliqué aussi à la vue jour. Le texte d'un bloc suit sa taille (`blockContent`) | le pincement dans la semaine et dans le jour, la lisibilité sombre / clair à 50 % et 300 % |
| 2 | Export d'un cours / contrôle vers le calendrier | **fait** | `domain/CalendarExport.kt` (pur) : titre, début / fin au fuseau local, salle en lieu, professeur en description ; contrôle = événement sur la journée. `ACTION_INSERT` sur `CalendarContract.Events.CONTENT_URI` depuis la fiche d'un cours et celle d'un devoir, **sans permission** | l'écran Calendrier prérempli |
| 3 | Compte à rebours avant le prochain contrôle | **fait** | `domain/ExamCountdown.kt` : les contrôles sont les devoirs `Homework.exam` (champ `interrogation` du cahier de textes). Composant `ExamCountdownCard` : « Contrôle de Maths dans 3 jours » ; section d'accueil `HomeSection.Exams` (activable, déplaçable ; les réglages anciens la retrouvent à la fin) et bandeau d'une ligne dans l'agenda. **L'indicateur `isDevoir` de l'emploi du temps n'est pas utilisé** : son sens n'est pas établi | l'accueil avec un vrai contrôle |
| 4 | Cours déplacé / remplacé | **fait** | `domain/ScheduleChanges.kt` (pur) compare deux lectures : même créneau (salle, professeur, fin, rétabli), **déplacé** (« 10:00 → 14:00 »), **remplacé** (« Maths → Anglais »). `NotificationPlanner` s'en sert avec ce que `NotifiedIds.lessons` retient déjà (aucun nouveau format) ; un cours annulé puis reprogrammé le même jour n'est annoncé que comme déplacé. **Agenda** : le dépôt garde à part l'ancienne réponse quand une lecture neuve change des cours à venir (`lessons-before`) ; fiche, frise et semaine montrent l'ancienne valeur barrée et une étiquette Déplacé / Remplacé / Modifié | une vraie notification de déplacement |
| 5 | Export PDF et ICS, semaines 1 / 2 | **fait** | Voir `docs/EXPORTS.md`. `export/TypicalSchedule.kt` (alternance, classement toutes semaines / 1 / 2), `IcsWriter.kt` (RFC 5545, tests qui relisent le texte), `TimetableLayout.kt` (mise en page A4 paysage pure et testée) + `TimetablePdf.kt` (dessin `PdfDocument`), `ScheduleExport.kt` (avertissements, nom de fichier, dossier `exports/` du cache), `ui/schedule/ExportSheet.kt` (écran). `FileProvider` : `exports/` ajouté à `file_paths.xml`, vidé au lancement et à la fermeture | ouvrir le PDF et le .ics dans de vraies applications (Calendrier, lecteur PDF) |

### Ce que l'on sait des semaines A / B (et ce que l'on suppose)
- **Établi** : aucune réponse connue d'`emploidutemps.awp` (capture documentée, client web du même auteur) ne porte de
  champ de semaine A / B ni de cours « une semaine sur deux ». Champs vus : `typeCours` (COURS, PERMANENCE, CONGE),
  `groupe` / `groupeCode` (sous-groupe de classe), `isFlexible`, `isModifie`, `isAnnule`… (liste complète dans `docs/EXPORTS.md`).
- **Supposé, non vérifié** : un établissement qui alterne se voit seulement à ses effets (cours présents une semaine sur
  deux). On n'a **jamais vu** une réponse d'un tel établissement. L'alternance est donc un **réglage de l'utilisateur**
  (avec la semaine de référence), jamais une détection automatique ; l'application ne fait que suggérer quand deux semaines
  consécutives diffèrent d'au moins 30 %.
- **Limite structurelle** : seules deux semaines sont en cache (en cours, suivante). Une semaine vide ou jamais chargée est dite
  inconnue dans l'écran d'export et sur la page du PDF.

Pas fait, volontairement : déplacement d'un cours **d'un jour à l'autre** (non rapproché : rien ne prouve que c'est le même cours),
vacances et jours fériés dans l'ICS (inconnus), exceptions (cours ajouté / déplacé) dans la semaine type, réglage de la semaine 1
partagé entre profils, contrôles tirés de `isDevoir`.

## 7. Lot 3 — hors ligne, fiabilité, confort, sécurité, premier lancement

Écrit, compilé et testé (492 tests unitaires dont 64 nouveaux, `assembleDebug` et `lintDebug` OK). **Rien de ce lot n'a été
essayé sur un téléphone** (non branché, aucun test instrumenté lancé) : « fait » veut dire écrit et testé, pas vu à l'écran.
Aucune écriture vers ÉcoleDirecte, aucune télémétrie, aucune requête de plus : le tirage « trop rapide » en émet **moins**.

| # | Demande | État | Ce qui a été fait | À vérifier sur le téléphone |
|---|---|---|---|---|
| 1 | Bandeau d'ancienneté | **fait** | `FreshnessBanner` (composant partagé, haut de tous les écrans) + `domain/FreshnessLabel.kt` (pur) : « Hors ligne · Données du 5 nov. à 18 h (il y a 3 h) », « d'aujourd'hui à 15 h 30 », « d'hier ». S'affiche quand la lecture vient du cache (`Freshness.stale`). « Hors ligne » seulement si c'est vraiment le réseau ; sinon la cause (compte verrouillé, version refusée…) | le bandeau en mode avion |
| 2 | Tirer pour rafraîchir | **fait** | Existait déjà sur accueil, agenda (cours et devoirs), notes, messages. Nouveau : `RefreshGate` — moins de 90 s après la dernière lecture, **aucune requête** et un message doux (« Déjà à jour : lu il y a 20 s. Réessaie dans 70 s… ») | le message en bas d'écran |
| 3 | Erreurs précises | **fait** | `data/FailureAdvice.kt` : mot de passe refusé, compte verrouillé, ÉcoleDirecte indisponible, pas de réseau, version d'API refusée (517), vérification de sécurité requise, session expirée, réponse inexploitable ; chaque cas a un titre, une explication et une action (Réessayer, Reconnecter, Ouvrir Plus > Diagnostic, ou rien pour un compte verrouillé : on n'insiste pas). Utilisé par les écrans de données (`UiState.Error` enrichi), le bandeau et l'écran de connexion | un compte verrouillé / un mot de passe changé (réels) |
| 4 | Journal des synchronisations | **fait** | `sync/SyncJournal.kt` : 30 derniers passages, une ligne de nombres et d'un code (début, durée, résultat typé, nouveautés), jamais de donnée personnelle ; alimenté par `SyncService` (un passage désactivé ou sans compte n'écrit rien) ; carte « Journal des synchronisations » dans Plus > Diagnostic | un vrai passage en arrière-plan |
| 5 | Mode « montrer à quelqu'un » | **fait** | `domain/PrivacyMode.kt` (pur) transforme les modèles d'affichage : moyennes, notes, appréciations, courbes, simulateur caché, accueil (note mise en avant, moyenne des enfants), recherche ; option messages (expéditeur, objet masqués, ouverture bloquée). Œil en haut de l'écran + réglage dans Plus. Les données enregistrées ne sont pas modifiées | l'œil, le rendu des ••• |
| 6 | Taille du texte | **fait** | Petit (0,88) / Normal / Grand (1,15) / Très grand (1,3), multiplié à la taille système via `LocalDensity`, borné à 0,7–3,0 ; aperçu dans Plus > Apparence | les écrans à « Très grand » (débordements possibles) |
| 7 | Noir pur | **fait** | `ThemeMode.Black` : fond #000000, cartes presque noires | l'écran OLED |
| 8 | Dates en français | **fait** | `domain/FrenchFormat.kt` centralise dates, heures et nombres ; les usages dispersés (agenda, notes, exports, notifications, diagnostic, journal de plantage) l'utilisent ; test avec une langue arabe par défaut. **Non touché** : `widget/` (hors lot) garde ses `Locale.FRENCH` explicites et un `"%02d".format` | — |
| 9 | Verrou après un délai | **fait** | `security/LockTimer.kt` (horloge injectée, testé) : Immédiatement / 1 / 5 / 15 minutes (1 minute par défaut, avant : 30 s). L'écran du code de l'appareil qui passe devant n'est pas compté comme une absence (sinon boucle en mode immédiat) | « immédiat » avec empreinte, puis avec code |
| 10 | Cinq thèmes de couleurs | **fait** | `ui/theme/ThemePalette.kt` : Indigo (défaut), Océan, Forêt, Coucher de soleil, Ardoise, chacun en clair / sombre / noir ; aperçu et choix dans Plus > Apparence, mémorisé. **Contraste AA (≥ 4,5) vérifié par test sur les 15 combinaisons** (texte, accent, erreur, positif, boutons, avertissements). Couleurs de matière : la teinte reste, mais en texte elle est assombrie/éclairci juste assez (`subjectTextColor`, ≥ 4,5) ; en pastille ≥ 1,9 (clair) / 3 (sombre). L'accent clair d'Indigo passe de #5C62E8 à #5A60E6 (4,48 → 4,61 sur le fond) | le rendu des cinq thèmes |
| 11 | Présentation de 3 pages | **fait** | `ui/login/Onboarding.kt` : après l'avis légal, avant la connexion ; balayage, Passer, points décrits pour TalkBack, sans animation si « réduire les animations » ; rejouable (Plus > À propos > Revoir la présentation) ; mémorisée | le balayage |

### Pour la couche widget (non modifiée)
- Couleurs : `ThemeSelection.palette(context, systemDark)` (package `settings`) rend une `ThemePalette` (ARGB : `ink`, `muted`, `background`,
  `surface`, `accent`, `accentFill`, `onAccent`, `positive`, `error`, `inverseSurface`, `warning*`, `subject`) selon le thème et le mode choisis ;
  `ThemePalettes.of(theme, appearance)` pour une combinaison précise ; `Contrast.readableOn(couleur, fond)` pour un texte coloré.
- Vie privée : `PrivacySelection.mode(context)` donne le `PrivacyMode` ; `mode.grade("14/20")` rend « ••• ». **Les widgets affichent encore les notes
  quand le mode est actif.**
- Dates : `FrenchFormat` (domain) pour tout nouveau formatage.

Pas fait, volontairement : widgets et notifications dans le mode « montrer », rotation d'écran (qui reverrouille déjà, comportement d'avant),
rafraîchissement automatique au tirage pendant un échec réseau (le tirage reste une demande de l'utilisateur, jamais une boucle).
