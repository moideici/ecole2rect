# Rapport critique — ecole2recte (Android)

Relecture indépendante, sans modification du code ni commande git. Copie lue : `scratchpad/critique` (~9 600 lignes, dont ~2 000 de tests). Aucun build, aucun test exécuté, aucun appel réseau vers ÉcoleDirecte.

Légende de fiabilité de chaque constat :
- **[V]** vérifié en lisant le code (fichier:ligne cités).
- **[V+doc]** vérifié dans le code ET confronté à la documentation communautaire de l'API (EduWireApps/ecoledirecte-api-docs).
- **[S]** supposé : déduit, plausible, mais non démontré sans exécution ou sans réponse réelle.

Limites de cette revue : je n'ai pas lu les CGU d'ÉcoleDirecte (non trouvées/vérifiées), je n'ai pas vu `PLAN-PATCH.md`, et aucune réponse réelle de l'API n'était disponible. Les tests du dépôt n'en contiennent pas non plus (voir C02).

---

## 0. Verdict en dix lignes

1. L'architecture est **propre et sensée** pour un projet de cette taille (couches data/domain/ui, `ApiSession` unique, garde-fous anti-blocage réfléchis, cache « dernière réponse valide », planificateur de notifications pur et testé). C'est nettement au-dessus de la moyenne des clients non officiels.
2. Mais **le produit n'a jamais vu une vraie réponse d'API** : tous les jeux de test sont inventés par l'auteur, donc les tests ne prouvent que la cohérence interne. Le premier essai réel a logiquement révélé des bugs de parseur.
3. **Le bug des « notes par matière vides » a une cause identifiée** (C01) : la moyenne générale est lue au mauvais niveau du JSON et la « période courante » repose sur un champ `isCurrent` qui n'existe pas. L'app tombe alors sur la *dernière* période (souvent vide).
4. Les widgets et la synchro sont **branchés de façon à ne rien montrer par défaut** (C04) et l'app n'a **aucune icône** (C05).
5. Le plus gros risque réel n'est pas le code, c'est **la fréquence des connexions** (C06, C13) et le **statut vis-à-vis des CGU** (C27) : une connexion complète à chaque lancement + une toutes les 4 h en arrière-plan, avec un User-Agent de navigateur imité.
6. Face à l'app officielle et à Pronote, l'app est **en retard sur la couverture fonctionnelle** (vie scolaire, pièces jointes, écriture de messages, synchronisation des devoirs faits, notifications en temps réel) et **meilleure ou égale seulement sur l'ergonomie** (mode sombre, multi-compte, simulateur de moyenne, personnalisation).

Totaux : **35 constats — 4 bloquants, 23 importants, 8 de confort.**

---

## 1. Comparaison avec les applications du même genre

Sources consultées (recherche web, juin–nov. 2026) :
- Documentation communautaire de l'API : <https://github.com/EduWireApps/ecoledirecte-api-docs/blob/main/README.md> (sections Élève : timeline, sondages, formulaires, visios, emploi du temps, cahier de texte, vie scolaire, carnet de correspondance, documents administratifs, notes, espaces de travail, manuels numériques, QCM, commandes ; Classe ; Cloud). Elle précise que le cookie GTK est obligatoire avant le login depuis le 24/03/2025, recommande un User-Agent de navigateur, et que le jeton doit être réutilisé avec le même User-Agent que celui de sa génération. Elle ne mentionne ni CGU ni limites de débit.
- Bibliothèque PapillonApp/Papillon-ED-Core : <https://github.com/PapillonApp/Papillon-ED-Core> (notes, emploi du temps, devoirs avec « marquer comme fait » et contenu de cours, messages reçus/envoyés/envoi, vie scolaire, documents, manuels, vie de classe, QCM, formulaires, sondages, carnet de correspondance, espaces de travail, cloud, téléchargements).
- Papillon (client open source Pronote/ÉcoleDirecte/Skolengo, GPL-3.0) : <https://www.mintlify.com/PapillonApp/Papillon/integrations/ecoledirecte>. Pronote : les fonctions sont activées automatiquement selon ce que l'établissement a ouvert.
- Application officielle « Mon EcoleDirecte » : <https://apps.apple.com/FR/app/id1296851886> — extension mobile officielle du site, notifications en temps réel (note, absence, message) selon <https://www.presse-citron.net/telecharger/loisirs/education_et_scolarite/mon-ecoledirecte.html> ; note ~1,9/5 sur ~20 000 avis (App Store) : l'app officielle est **mal aimée**, ce qui laisse une vraie place à un client plus fluide.
- Pronote mobile : <https://www.futura-sciences.com/tech/definitions/logiciels-pronote-27459/>, guides d'établissement (notes, cahier de textes, emploi du temps avec cours annulés, documents, messagerie, absences/retards, alertes push configurables).
- Autre client ÉcoleDirecte : Ecole Directe Plus (web/PWA, MIT) — <https://webcatalog.io/en/apps/ecole-directe-plus>. Je n'ai trouvé **aucun client Android natif open source** pour ÉcoleDirecte : le créneau « natif, soigné, hors ligne » est libre.

Les colonnes « officielle » et « Pronote » viennent de ces sources et de mes connaissances générales ; ce n'est pas un test comparatif en conditions réelles.

| Fonction | App officielle ÉcoleDirecte | Pronote mobile | Papillon (tiers) | ecole2recte | Verdict |
|---|---|---|---|---|---|
| Connexion + double authentification | Oui (la session reste ouverte) | Oui | Oui | Oui, une saisie, réponse auto par date de naissance mémorisée. Mais reconnexion complète à chaque lancement (C06) | **Égal en confort, en retard en sobriété** |
| Multi-compte / famille (plusieurs enfants) | Oui (sélecteur d'élève) | Oui | Oui | Oui (comptes + élèves), mais notifications/synchro limitées à l'élève actif (C13) | **Égal** (UI) / en retard (synchro) |
| Emploi du temps | Jour/semaine, annulés, salle, détail séance | Idem, modifications quasi temps réel | Oui | Jour (pager) + semaine en grille ; 2 semaines seulement ; pas de navigation libre ; cours superposés mal rendus (C29) ; heures/pauses à corriger (C03) | **En retard** |
| Cahier de textes / devoirs | Oui, contenu de séance, « fait » synchronisé, pièces jointes | Oui | « Fait » + contenu | Devoirs futurs, détail, regroupement par échéance ; « fait » **local seulement** (C16) ; pièces jointes listées sans téléchargement (C19) ; pas d'historique | **En retard** |
| Notes | Par période, moyennes, stats, appréciations, bulletins | Idem + détails de calcul | Par période, moyennes, stats | Moyenne + courbe + par matière + simulateur ; **cassé par C01** ; pas de sélecteur de période, pas d'appréciations, rang, bulletin PDF (C17) | **Égal sur l'idée (simulateur = plus), en retard sur le fond** |
| Messagerie | Lecture, écriture, dossiers, pièces jointes | Idem | Lecture + envoi | Boîte de réception seule, recherche locale, pagination ; ouvrir un message le marque lu côté serveur | **En retard** (C18) |
| Vie scolaire (absences, retards, sanctions) | Oui | Oui | Oui | **Absent** | **Absent** (C20) — le premier besoin d'un parent |
| Documents / cloud / carnet de correspondance | Oui | Documents | Oui | **Absent** | **Absent** |
| Notifications | Push en temps réel | Push configurables (parfois en retard selon avis) | Locales/limitées | Sondage toutes les 4 h par WorkManager, 4 canaux, baseline initiale anti-spam (bon) ; latence 4 h ; contenu visible écran verrouillé (C23) | **En retard en immédiateté, égal en hygiène** |
| Widgets | Non (à ma connaissance) | Non | Non (non trouvé) | 2 widgets Glance (prochain cours, devoirs) — **non fonctionnels par défaut** (C04) | **Potentiellement meilleur, aujourd'hui inutilisable** |
| Hors ligne | Limité | Cache partiel (non confirmé officiellement) | Partiel | Cache « dernière réponse valide » + bandeau « données périmées » | **Meilleur** (bien conçu) |
| Personnalisation (thème, couleurs, accueil) | Faible | Faible | Forte | Thème clair/sombre/système, couleurs par matière, blocs d'accueil réordonnables | **Meilleur/égal Papillon** |
| Activation selon ce que l'établissement ouvre (`modules`) | Natif | Natif | Oui | **Ignoré** (C21) | **En retard** |
| Verrou biométrique | Non | Non | Non | Oui (mais décoratif pour les données, voir C24) | **Meilleur sur le papier** |
| Qualité/stabilité perçue | Très critiquée | Variable | Bonne | Inconnue : jamais éprouvé avec de vraies données | — |

Lecture honnête : le positionnement « client ÉcoleDirecte propre, hors ligne, sans friction » est bon, mais aujourd'hui l'app offre **un sous-ensemble en lecture seule** de l'officielle (≈ 4 modules sur ~12), avec un meilleur look. Ce n'est pas encore un remplaçant ; c'est un complément. Il faut le dire à l'utilisateur.

---

## 2. Points forts à préserver (pour ne pas les casser en corrigeant)

- Garde-fous d'authentification cohérents : aucune nouvelle tentative après 505/516/535 (`SessionViewModel.restore`, `SyncService.run`), mémoire des identifiants refusés (`rejected`), une seule tentative par question de sécurité, ré-authentification limitée à 1/min (`ApiSession.reauthenticate`), `SyncWorker` ne renvoie jamais `Result.retry()`.
- Séparation correcte : protocole (`EcoleDirecteClient`) / chemins (`EcoleDirecteApi`) / lecture défensive (`Parsers`) / modèles stables (`domain`).
- Cache = réponse brute, ré-interprétable après correction d'un parseur (`ResponseCache.kt`), avec indicateur de péremption honnête (`Freshness`).
- Planificateur de notifications pur, avec « premier passage = référence, rien notifié » et mémoire des identifiants déjà notifiés.
- Parseurs tolérants (nombres « 12,5 », dates ISO/FR, base64 ou clair, plusieurs noms de champs).
- Aucun appel `Log.*` dans tout le code (vérifié par recherche) ; `allowBackup=false` ; HTTPS uniquement ; diagnostic réseau expurgé (en partie, voir C26).

---

## 3. Constats détaillés

Format : **ID — titre** · fichier:ligne · gravité · effort · fiabilité. Puis explication et recommandation.

### 3.1 Bugs qui expliquent le premier essai réel

**C01 — Moyennes lues au mauvais endroit ; la « période courante » ne peut jamais être trouvée** · `data/api/Parsers.kt:195-199`, `data/EcoleDirecteRepository.kt:447-457` · **bloquant** · petit · **[V+doc]**
- Le code lit `moyenneGenerale` et `moyenneClasse` à la racine de chaque période (`raw.num("moyenneGenerale")`). D'après la documentation communautaire, ces champs sont **dans `periodes[].ensembleMatieres`** (avec `moyenneMin/Max`, `disciplines`, `dateCalcul`, `nomPP`). Résultat : `average` vaut toujours `null`.
- `current = !raw.flag("cloture") && raw.flag("isCurrent", "courante")` : `isCurrent`/`courante` ne figurent pas dans la liste documentée des champs de période (`idPeriode, codePeriode, periode, annuel, dateDebut, dateFin, cloture, dateConseil, ensembleMatieres`). `current` est donc toujours faux.
- `overview()` : `current = periods.firstOrNull{ it.current && it.average != null } ?: periods.lastOrNull{ it.average != null } ?: periods.lastOrNull()`. Avec les deux bugs ci-dessus, on prend `periods.lastOrNull()` : la **dernière période** de la liste (3e trimestre futur, ou période « annuelle » `annuel=true`). Les notes sont ensuite filtrées sur son `codePeriode` → liste vide → « notes par matière vides », moyenne « – », pas de tendance. C'est très probablement exactement ce que l'utilisateur a observé.
- Les tests ne l'ont pas vu car leurs fixtures (voir C02) mettent `moyenneGenerale` et `cloture` à la racine et `isCurrent` dans `SyncServiceTest.kt:111`.
- **Reco** : (1) lire `ensembleMatieres.moyenneGenerale/moyenneClasse` (et garder le repli racine) ; (2) déterminer la période courante par **dates** (`dateDebut ≤ aujourd'hui ≤ dateFin`), sinon la dernière période ayant au moins une note, en **écartant `annuel`** ; (3) ne jamais retomber sur `lastOrNull()` brut ; (4) ajouter un sélecteur de période (C17) ; (5) test avec une capture réelle anonymisée.

**C02 — Aucun test ne repose sur une vraie réponse ; les fixtures reflètent les hypothèses de l'auteur** · `EcoleDirecteRepositoryTest.kt:31-58`, `SyncServiceTest.kt:106-115`, `EcoleDirecteClientTest.kt` · important · moyen · **[V]**
- Le plan (lot 7) exigeait des « réponses enregistrées et anonymisées ». Il n'y en a aucune : tous les JSON sont écrits à la main, avec les clés qu'attend le parseur. Les 60+ tests passent mais prouvent surtout que le code est d'accord avec lui-même.
- **Reco** : créer `app/src/test/resources/captures/` avec des réponses **réelles anonymisées** (élève collège, élève lycée, compte famille 2 enfants, enseignant refusé) pour : login 200/250, emploi du temps, notes (avec périodes annuelles, notes « Abs/Disp/NE », matières à sous-matières), cahier de textes (résumé + jour), messages, détail message. Ajouter un **outil de capture interne** (voir « Actions » n°2) qui produit un JSON où les valeurs textuelles sont remplacées par des marqueurs mais les clés, types et formes sont conservés : l'utilisateur peut ainsi alimenter les tests sans exposer de données d'élève.

**C03 — Emploi du temps « sans heures ni pauses » : cause non établie par la lecture** · `Parsers.kt:302-317`, `ScheduleView.kt:226-268`, `EcoleDirecteRepository.kt:250-275` · **bloquant** · moyen · **[V] sur ce qui est exclu, [S] sur le reste**
- Vérifié : le format documenté `"AAAA-MM-JJ HH:MM"` est correctement lu (`isoDateTime`, `raw.drop(10)` puis regex d'heure) ; `ScheduleEntry.start/end` sont bien affichés dans `LessonRow` (colonne de 48 dp, 13 sp). Je ne vois donc **pas** de défaut de parsing de date qui explique l'absence d'heures. Hypothèses [S], à départager avec une capture : (a) la réponse réelle arrive sous une forme que le test n'a jamais couverte (clés `start_date` absentes → entrées ignorées par `?: return@mapNotNull null`, silencieusement) ; (b) la colonne d'heures de 48 dp à 13 sp déborde/coupe avec une taille de police système > 100 % ; (c) la vue jour est une **simple liste de cartes** : pas d'axe horaire, pas de blocs « pause / récréation / déjeuner / heure libre » — ce n'est pas un bug de données, c'est une conception qui ne montre jamais les trous (`avecTrous=false` est demandé à l'API, et le commentaire « les trous sont calculés côté application » (`EcoleDirecteApi.kt:22`) est **faux** : rien ne les calcule).
- Autres risques non traités : `typeCours` (COURS / PERMANENCE / autres) est ignoré — les permanences, congés ou dispenses (`dispense`) seront affichés comme des cours ; une entrée sans `start_date` disparaît sans trace.
- **Reco** : capturer une vraie réponse ; afficher les trous (heure libre, pause méridienne) en les calculant entre cours consécutifs d'un même jour ; filtrer/étiqueter `typeCours` ; journaliser (en mémoire, pour le diagnostic) le nombre d'entrées rejetées par le parseur et l'afficher dans l'écran « Diagnostic ».

**C04 — Widgets : conçus pour ne rien afficher tant que la synchro n'est pas activée, et pour mentir ensuite** · `widget/Widgets.kt:52-67, 80-93, 103-110`, `sync/SyncService.kt:241`, `sync/SyncStorage.kt:339`, `sync/SyncService.kt:288-291`, `res/xml/*_widget_info.xml` · **bloquant** · moyen · **[V] pour les causes fonctionnelles, [S] pour « ne s'affichent pas dans le sélecteur »**
- Le contenu vient uniquement de `WidgetSnapshotStore`, écrit **seulement** par `SyncService`, qui est **désactivé par défaut** (`preferences.getBoolean("enabled", false)`). Ajouter un widget sans avoir activé « Notifications et widgets » affiche « Active les notifications et widgets dans Plus. » — pas de données même si l'app a le cache plein.
- Le libellé du prochain cours est figé à l'instant de la synchro : `WidgetLesson(..., it.startLabel, ...)` stocke par exemple « 10:15 · dans 25 min » / « En cours · jusqu'à 11:00 ». Quatre heures plus tard, le widget affiche toujours « dans 25 min ». Il ne recalcule jamais « prochain cours » à l'affichage, et ne se met pas à jour quand l'app est ouverte (aucun appel `WidgetUpdater` hors synchro).
- `updateAll` est enveloppé dans `runCatching`, qui avale aussi `CancellationException` et masque toute erreur Glance.
- Le widget n'a ni `android:previewLayout`/`previewImage`, ni `targetCellWidth/Height`, ni `configure`. Dans le sélecteur de widgets de nombreux lanceurs, l'entrée apparaît avec l'icône de l'app — qui n'existe pas (C05) — et sans aperçu. [S] : c'est une cause probable de « les widgets ne s'affichent pas » dans la liste, avec la tentative de `initialLayout=@layout/glance_default_loading_layout` (fourni par la bibliothèque Glance, donc normalement valide).
- Les devoirs du widget sont stockés en clair dans `SharedPreferences` (voir C24).
- **Reco** : (1) écrire l'instantané **à chaque chargement réussi de l'accueil** (dans l'app) et pas seulement en synchro ; (2) stocker les **horaires absolus** (début/fin ISO) et calculer « dans X min » au rendu, avec rafraîchissement par `updateAll` à chaque changement de cours ; (3) gérer l'absence de compte/hors ligne par un texte explicite ; (4) ajouter `previewLayout` (Android 12+) et `previewImage`, `description` ; (5) n'attraper que les exceptions utiles (laisser passer `CancellationException`) ; (6) test instrumenté minimal (le widget se rend avec un instantané donné).

**C05 — L'application n'a pas d'icône** · `AndroidManifest.xml:6-10`, `res/` (aucun `mipmap-*`, aucun `ic_launcher`) · **bloquant** · petit · **[V]**
- Aucun `android:icon`/`android:roundIcon`, aucune ressource d'icône adaptative. Android affiche l'icône par défaut ; certains lanceurs/sélecteurs de widgets masquent ou mal présentent l'app. Aussi pas de thème d'icône monochrome (Android 13).
- **Reco** : icône adaptative (`mipmap-anydpi-v26/ic_launcher.xml` + premier plan vectoriel + fond) avec calque monochrome ; référencer dans le manifeste. Effort : une heure.

### 3.2 Robustesse, concurrence et performances

**C06 — Connexion complète à chaque lancement ; le jeton n'est jamais conservé (promesse du plan non tenue)** · `SessionViewModel.kt:129-199`, `AppContainer.kt` · important · moyen · **[V]**
- Le lot 2 du plan annonçait « conserver le jeton de session avec son horodatage… reconnexion silencieuse seulement si 520 ». Le code fait l'inverse : chaque démarrage de processus déclenche `GTK + login.awp` (+ doubleauth si besoin). Android tue l'app en arrière-plan souvent ; les ouvertures via notification/raccourci/widget après kill refont aussi un login.
- Chaque connexion crée une entrée dans l'historique/les logs d'ÉcoleDirecte et peut déclencher des contrôles (question de sécurité, e-mails d'alerte) [S].
- **Reco** : conserver `token` + cookies + User-Agent utilisés (chiffrés comme le mot de passe), les rejouer au démarrage ; ne se reconnecter que sur 520/525/526. Vérifier d'abord, sur un vrai compte, la durée de vie réelle du jeton (le plan lui-même la liste comme point à valider). La doc communautaire indique que le jeton doit être utilisé avec le **même** User-Agent : le stocker avec.

**C07 — Chaque écran crée son propre dépôt : la « déduplication des requêtes » ne fonctionne pas entre écrans** · `ui/common/AppViewModel.kt:40-44` + 7 appels (`GradesView.kt:65`, `HomeScreen.kt:67`, `HomeworkView.kt:59`, `MessagesView.kt:57`, `CustomizationCards.kt:69`, `MainScaffold.kt:153`, `ScheduleView.kt:80`) · important · petit · **[V]**
- `rememberSchoolRepository()` fait `remember(profile?.key) { container.schoolRepositoryFor(profile) }` à **chaque site d'appel** : il y a donc jusqu'à 7 instances de `EcoleDirecteRepository`, chacune avec sa propre mémoire de 90 s (`cache`, `locks`). Le commentaire de classe (« l'accueil, l'agenda et les pastilles lisent les mêmes cours… ÉcoleDirecte voit d'un mauvais œil les rafales ») décrit l'intention, pas la réalité. Au démarrage : pastilles (messages, devoirs, notes) + accueil (cours, devoirs, notes, messages) = au moins 7 requêtes pour 4 ressources, puis chaque onglet ouvert refait la sienne. Le test « une ressource demandée plusieurs fois n'est chargée qu'une fois » n'exerce qu'une seule instance.
- **Reco** : un seul dépôt par profil, tenu par `AppContainer` (map `profile.key → repository`) ou fourni via un `CompositionLocal` au niveau de `MainContent` ; ajouter un test qui instancie deux écrans.

**C08 — `BadgesViewModel` est lié à l'activité : il reste branché sur l'ancien compte et n'est jamais rafraîchi** · `MainScaffold.kt:401-404`, `AppViewModel.kt:48-51,53` · important · petit · **[V] code, comportement [S]**
- `appViewModel{ BadgesViewModel(repository) }` appelle `viewModel(factory)` sans clé dans `MainContent`, hors d'un `NavBackStackEntry` : le `ViewModelStoreOwner` est l'activité. Le ViewModel est créé une fois avec le dépôt du premier profil. Après changement de compte ou d'élève, il continue d'interroger l'ancien profil → **les pastilles d'un compte s'affichent sur l'autre** (fuite d'information entre frères/sœurs ou entre comptes), et n'est jamais relancé (aucun `refresh()` appelé : voir C09).
- **Reco** : clé de ViewModel = `profile.key`, ou état des pastilles porté par le dépôt partagé de C07 ; test de bascule de compte.

**C09 — Aucun rafraîchissement à la reprise ; les données restent figées jusqu'au « tirer pour rafraîchir »** · `ui/common/UiState.kt:145-147`, recherche `ON_RESUME/LifecycleEventEffect` : aucune occurrence · important · petit · **[V]**
- Les ViewModels chargent une fois à la création. Ouvrir l'app le soir après l'avoir ouverte le matin (processus vivant) montre les données du matin, sans indication. Les pastilles et le widget n'évoluent pas non plus.
- **Reco** : rafraîchir à `ON_START` si la dernière récupération a plus de N minutes (5–10) ; ne jamais le faire en boucle ; refléter la date de dernière synchro dans l'en-tête.

**C10 — `EcoleDirecteClient` n'est pas thread-safe alors qu'il est appelé en parallèle** · `EcoleDirecteClient.kt:26-33, 270-282, 312-326` · important · petit · **[V] code, effet [S]**
- `cookies` est un `LinkedHashMap` modifié par `captureCookies` ; `token`, `currentGtk` sont des champs non `@Volatile` ; aucune synchronisation. Les ViewModels de l'accueil, des pastilles, de l'agenda, etc. lancent leurs `withContext(Dispatchers.IO)` **en même temps** sur le même client (`ApiSession.Ready.client`). Une `ConcurrentModificationException` est possible lors de deux réponses simultanées avec `Set-Cookie` ; elle n'est pas une `IOException`, remonte dans le ViewModel et s'affiche comme message d'erreur brut.
- **Reco** : un `Mutex` (ou `synchronized`) autour de la lecture/écriture de l'état de session ; idéalement une file d'attente à concurrence 2 pour les appels de données (courtoisie envers le serveur).

**C11 — Une réponse « 200 » vide ou inattendue écrase le dernier bon cache** · `EcoleDirecteRepository.kt:143-154` · important · petit · **[V]**
- `resource()` fait `responseCache.put(owner, name, raw, …)` avant tout contrôle de forme. Si ÉcoleDirecte renvoie `{"code":200,"data":null}` ou `[]` (cas courant quand un module est fermé ou en maintenance), le cache valide est remplacé par du vide et l'utilisateur perd ses données hors ligne.
- De plus le repli sur le cache se déclenche aussi pour `Blocked` ou `InvalidCredentials` : l'écran affiche « données périmées » sans dire pourquoi.
- **Reco** : n'écrire au cache qu'après un parse « utile » (au moins un élément, ou liste vide légitime seulement si le cache précédent était déjà vide) ; conserver l'ancien fichier sinon ; transmettre la cause du repli à `Freshness`.

**C12 — Une seule date mal formée fait échouer tout un écran** · `EcoleDirecteRepository.kt:255, 375, 385, 348, 420` (`LocalDate.parse(...)` sans garde), `ScheduleView.kt:206` (`LocalTime.parse`) · important · petit · **[V]**
- Les parseurs sont défensifs, mais le dépôt refait ensuite des `LocalDate.parse` sur des chaînes extraites par `substring`. Une valeur inattendue lance `DateTimeParseException`, non rattrapée par `optional()` (qui n'attrape que `EcoleDirecteException`) ; `LoadingViewModel` l'affiche comme texte d'erreur technique. Un cours ou un devoir « bizarre » bloque l'accueil entier.
- **Reco** : valider les dates une fois dans `Parsers` (ou convertir en `LocalDateTime` typés dans `ApiLesson`/`ApiHomework`) et supprimer les `parse` répétés ; au pire, ignorer l'élément fautif et compter les rejets.

**C32 — Chargement séquentiel et redondant de l'accueil** · `EcoleDirecteRepository.kt:225-231` · confort · petit · **[V]**
- Quatre appels l'un après l'autre (cours, devoirs, notes, messages). Avec un RTT de 300–600 ms et le GTK imposé, l'accueil met plusieurs secondes à froid. Les paralléliser contredirait la politesse envers le serveur (C10, « à ne pas faire ») : préférer **afficher d'abord le cache** (disponible, voir C11) puis mettre à jour, c'est ce qu'annonce le plan (lot 8) et ce que le code ne fait pas : `resource()` essaie toujours le réseau d'abord.
- **Reco** : « cache d'abord, réseau ensuite » avec deux émissions (`Flow`), plutôt que d'attendre la fin de la reconnexion pour afficher quoi que ce soit.

### 3.3 Synchronisation en arrière-plan et stockage des identifiants

**C13 — Synchro : boucles évitables, un seul élève, pas de gigue** · `SyncService.kt:240-262, 273-276`, `SyncWorker.kt:133-146, 157` · important · petit · **[V]**
- `NeedsInteraction` (question de sécurité non résolue automatiquement) n'arrête pas la synchro : toutes les 4 h le worker se reconnecte et déclenche de nouveau la question de sécurité, indéfiniment. Idem `FailureKind.Other` (WAF qui bloque, version d'API périmée) : retenté perpétuellement.
- Seul l'élève actif du compte actif est synchronisé (`store.active()`, `toActiveProfile()`). Un parent de deux enfants ne reçoit rien pour l'autre ; les autres comptes enregistrés sont ignorés.
- Chaque passage fait login (2–3 requêtes) + 4 ressources, avec le même cadencement fixe pour tous les utilisateurs de l'app (pas de gigue) : trafic synchronisé si l'app se diffuse.
- **Reco** : après N échecs consécutifs ou `NeedsInteraction`, désactiver et expliquer (comme pour 505) ; ajouter `setInitialDelay` aléatoire / `flexInterval` ; réutiliser le jeton (C06) ; synchroniser tous les élèves du compte actif en une seule connexion ; ne PAS descendre sous 4 h (voir « À ne pas faire »).

**C14 — Magasin d'identifiants : conditions de course et perte silencieuse possible** · `data/SecureCredentialStore.kt:62-97, 114-116`, `SyncService.kt:264-266` · important · petit · **[V]**
- `load()` → modification → `store()` sans verrou. `SyncService` écrit `saved.copy(trustedAnswer = …)` depuis un instantané lu au début de la synchro ; si l'utilisateur a changé d'élève actif ou de mot de passe entre-temps, la synchro l'écrase.
- Si `decrypt` échoue (clé Keystore invalidée, restauration, bogue) le `runCatching` retombe sur l'ancienne clé `login`, absente → liste **vide** ; le prochain `save()` écrit alors un magasin ne contenant que le nouveau compte : **tous les comptes enregistrés sont perdus sans message**. Le mot de passe étant déjà chiffré avec une clé non exportable, cela arrive réellement après certaines réinitialisations/migrations d'appareil.
- **Reco** : `synchronized` (ou `Mutex`) sur toutes les opérations ; en cas d'échec de déchiffrement, **ne pas écrire** et signaler « comptes illisibles, reconnecte-toi » ; la synchro ne doit enregistrer que des champs ciblés (merge, pas remplacement).

**C24 — Données en clair sur le disque ; le verrou biométrique ne protège que l'écran** · `FileResponseCache.kt:531-543`, `NotificationState`/`WidgetSnapshotStore`/`PrefsHomeworkStatusStore` (SharedPreferences non chiffrées), `MainActivity.kt:36-55`, `SecureCredentialStore.kt:167-181` · important · moyen · **[V]**
- Mot de passe, réponse de confiance et date de naissance sont bien chiffrés AES-GCM par une clé Android Keystore non exportable (bon). Mais le cache contient en clair les **notes, le contenu des messages, noms d'élèves, devoirs** dans `filesDir/cache/*.json`, et les identifiants de notifications/devoirs/widgets dans des préférences non chiffrées. Protégé par le chiffrement de fichiers d'Android (appareil verrouillé) et `allowBackup=false`, mais lisible sur un appareil déverrouillé rooté/débogué, ou par n'importe quel outil d'extraction pendant que l'utilisateur est connecté.
- Le verrou (`AppLock`) est une barrière d'interface : la clé Keystore est créée avec `setUserAuthenticationRequired(false)`, aucune donnée n'est liée au verrou. Pas de `FLAG_SECURE` : les captures d'écran et l'aperçu des tâches récentes montrent notes et messages même quand l'app est verrouillée au retour.
- Les notifications et widgets montrent leur contenu sans passer par le verrou (C23).
- **Reco** : (1) chiffrer le cache avec la même clé Keystore (le surcoût est faible : fichiers petits) ; (2) `FLAG_SECURE` quand le verrou est actif (option) ; (3) documenter le modèle de menace dans le README : « protège contre la perte de l'appareil verrouillé, pas contre un appareil compromis » ; (4) **ne pas** lier la clé à l'authentification utilisateur (risque de perte de tous les comptes, voir « À ne pas faire »).

**C25 — La date de naissance est conservée** · `SecureCredentialStore.kt:14-17, 121-123` · confort · petit · **[V]**
- Stockée (chiffrée) pour répondre seule à la question de sécurité. C'est cohérent avec la promesse « une seule saisie », mais c'est une donnée sensible d'un mineur supplémentaire. Alternative plus sobre : ne mémoriser que `cn/cv` et redemander la date si la question revient (rare). À arbitrer, pas à imposer ; au minimum, donner à l'utilisateur la possibilité de l'oublier.

**C26 — Diagnostic réseau : expurgation incomplète** · `EcoleDirecteClient.kt:376-393, 455-456` · confort · petit · **[V]**
- `redactSecrets` masque motdepasse/password/token/cookie, mais pas `cn`, `cv` (jeton de confiance de l'appareil), ni e-mails, téléphones, noms, adresses des `accounts` qui se trouvent dans la réponse de login (jusqu'à 4 000 caractères copiables dans le presse-papiers puis collés dans un ticket). Le plan impose « aucun identifiant, mot de passe, cookie ni jeton » ; PII et `cn/cv` ne sont pas couverts.
- **Reco** : liste blanche plutôt que liste noire : n'afficher que `code`, `message`, la liste des clés, des longueurs et des types.

### 3.4 Réseau, conformité et risque de compte

**C27 — Imitation d'un navigateur et statut vis-à-vis des CGU** · `EcoleDirecteClient.kt:290-307` · important · n/a · **[V] sur les faits, [S] sur la portée juridique**
- Faits : User-Agent « Chrome/134 Windows » fixe, `Origin`/`Referer` = `https://www.ecoledirecte.com`, en-tête `X-Client: Android` (inconnu de la doc), version d'API `4.75.0` figée, et le commentaire du code dit « browser-like fingerprint required by the current API/WAF ». C'est la définition d'un client qui se fait passer pour le site officiel. ÉcoleDirecte a introduit le cookie GTK en mars 2025 : c'est un durcissement anti-automatisation, donc la tendance est à **moins** de tolérance.
- Je n'ai pas pu lire les CGU : **je ne peux pas affirmer** qu'elles interdisent les clients tiers, mais il est raisonnable de supposer que l'accès automatisé non autorisé y est restreint. Risques réalistes, du plus au moins probable : (1) l'app cesse de fonctionner sans préavis (changement de protocole) ; (2) blocage de l'adresse/du compte par le WAF en cas de trop nombreuses connexions (C06, C13) ; (3) suspension du compte par l'établissement ou l'éditeur en cas de signalement ; (4) mise en demeure si l'app est publiée avec le nom ou l'identité « EcoleDirecte ». Le risque (2) est le seul que le code peut réduire lui-même.
- Côté données personnelles, l'app n'a pas de serveur ni de télémétrie, ce qui est le bon choix ; mais l'utilisateur fournit son mot de passe (et souvent celui d'un enfant mineur) à un logiciel non officiel : il doit en être informé clairement.
- **Reco** : écran d'accueil/première connexion avec avertissement honnête (« application non officielle, non affiliée, utilisation à tes risques, on ne se connecte que pour toi, rarement ») ; ne pas publier sur le Play Store sous un nom confusable avec la marque ; ne pas rendre le User-Agent aléatoire (la doc dit que le jeton est lié à son User-Agent d'origine) ; documenter dans le README.

**C28 — Version d'API figée, sans repli** · `EcoleDirecteClient.kt:23-24, 196-199` · important · petit · **[V]**
- `apiVersion = "4.75.0"` est une constante compilée. Quand ÉcoleDirecte la refuse (code 517), l'app affiche une erreur et tous les utilisateurs sont en panne jusqu'à une nouvelle version de l'APK. Le login ne traite pas 517 explicitement (il tombe dans `else` → `Other`) : la synchro réessaie sans fin (C13).
- **Reco** : mettre la version dans un réglage local modifiable (écran Diagnostic) avec valeur par défaut ; traiter 517 comme « application à mettre à jour » côté login ET synchro (arrêt de la synchro) ; prévoir un fichier de configuration distant seulement si un service d'hébergement fiable existe — sinon sur-ingénierie.

**C35 — Taxonomie d'erreurs trop grossière** · `EcoleDirecteClient.kt:180-209` · confort · petit · **[V]**
- `403` est utilisé comme `ACCESS_DENIED` alors que c'est aussi le code HTTP d'un WAF : une page HTML 403 donne un corps non JSON → `JSONObject()` vide → `code=0` → « ÉcoleDirecte a répondu de façon inattendue ». Aucun traitement de 429, de maintenance (503), de réponse HTML. Pas de disjoncteur global : après un blocage WAF, chaque écran refait ses requêtes.
- **Reco** : distinguer HTML/non-JSON (blocage probable → disjoncteur de 10–30 min), 429/503 (attente), et 403 JSON (module non autorisé, voir C21).

### 3.5 Fonctions manquantes ou incomplètes (par ordre d'impact pour l'utilisateur)

**C20 — Vie scolaire absente** · pas de code · important · moyen · **[V]**
- Absences, retards, observations, punitions : c'est l'information qu'un parent veut voir **immédiatement**, et la plus émotionnelle (justifier une absence, voir un retard). Papillon-ED-Core et l'API documentent cette section. Lecture seule d'abord (liste, compteurs, « à justifier »), notification ensuite.

**C16 — « Devoir fait » purement local** · `EcoleDirecteRepository.kt:294-296`, `HomeworkStatusStore.kt` · important · moyen · **[V]**
- La coche n'est pas envoyée à ÉcoleDirecte (assumé dans le plan). Conséquences : état différent entre l'app et le site, entre deux appareils, entre élève et parent ; après réinstallation, tout est perdu. L'app officielle et Papillon synchronisent. Le plan prévoyait « après vérification sur un vrai compte » : à faire, mais **avec précaution** (une écriture erronée modifie les données de l'élève ; voir « À ne pas faire »).

**C17 — Notes : pas de période, pas d'appréciations, libellés figés** · `GradesView.kt:81`, `Repository.kt:447-483` · important · moyen · **[V]**
- Aucun choix de période (T1/T2/T3, année), titre « Ce trimestre » codé en dur alors que certains établissements sont en semestres. Pas d'appréciations (`appreciations` dans les disciplines), de rang, ni de moyenne min/max de la classe affichés, ni de bulletins. La courbe d'évolution a besoin de C01 pour exister. `GradePill` affiche « Abs » pour **toute** valeur non numérique (Disp, NE, Absent, Non noté), ce qui est faux pour les dispenses.
- **Reco** : chips de période ; afficher la valeur textuelle brute quand elle n'est pas numérique ; ajouter appréciations et min/max.

**C18 — Messagerie en lecture seule** · `EcoleDirecteApi.kt:44-66`, `MessagesView.kt` · important · grand pour l'écriture / petit pour le reste · **[V]**
- Pas de réponse, de nouveau message, de dossiers (envoyés, brouillons, archivés), ni de suppression. Ouvrir un message le **marque lu côté serveur** (assumé dans le code), ce qui peut surprendre un élève dont le parent consulte. Le téléchargement de pièces jointes manque (C19). Erreur d'ouverture : message générique « Impossible d'ouvrir… » sans raison. `hasMore && all.size >= 50` est un artifice (page de 50 supposée).
- **Reco** : d'abord pièces jointes + dossiers en lecture ; écriture bien plus tard, avec confirmation explicite.

**C19 — Pièces jointes listées mais inaccessibles** · `HomeworkView.kt:176-182`, `MessagesView.kt:212-217`, `Parsers.kt:349-358` · important · moyen · **[V]**
- Le nom est affiché avec une icône trombone, sans téléchargement ni ouverture, et l'API de téléchargement n'est pas implémentée. Un devoir « voir le PDF joint » est donc un cul-de-sac. Le plan (lots 11 et 13) le promettait.
- **Reco** : téléchargement en cache privé puis ouverture par `FileProvider`/`ACTION_VIEW` ; demander confirmation avant d'ouvrir un fichier (contenu non fiable).

**C21 — Les modules ouverts par l'établissement sont ignorés** · `EcoleDirecteClient.kt:343-368` (extraction de compte) · important · moyen · **[V]**
- La réponse de login décrit les `modules` activés pour le compte. L'app ne les lit pas : elle affiche toujours cinq onglets et tente des appels que l'établissement a désactivés (erreur « Ce compte n'a pas accès à cette ressource »). Pronote/Papillon masquent les fonctions non ouvertes.
- **Reco** : lire les modules, masquer/griser les onglets, éviter les appels voués à l'échec (ils comptent aussi pour le trafic).

**C22 — Les parents sont mal servis** · `GradesView.kt:94`, `MessagesView.kt:128`, `LoginScreen.kt:133`, `SyncService.kt` · important · moyen · **[V]**
- Textes adressés à l'élève (« Tes notes », « Ta boîte de réception »), identique pour un compte famille. Pas de vue « tous mes enfants » (une carte par enfant : prochain cours, devoir du soir, nouvelle note). La messagerie affichée est celle du parent (documenté dans le code) sans l'indiquer à l'écran. Notifications seulement pour l'élève sélectionné (C13).
- **Reco** : formulation neutre ou conditionnelle au type de compte ; accueil « famille » multi-enfants.

**C29 — Grille hebdomadaire : lisibilité sur téléphone et cours superposés** · `ScheduleView.kt:312-345` · important · moyen · **[V]**
- Cinq colonnes de ~60 dp avec du texte 12 sp coupé à deux lignes : les noms de matières sont tronqués. Les cours qui se chevauchent (groupes scindés, que `Parsers.timetable` documente comme normaux) sont dessinés à la même position, l'un sur l'autre, au lieu d'être partagés en sous-colonnes. Une plage trop courte (< 48 dp) masque la salle. Pas de bandeau « maintenant » dans la grille.
- **Reco** : algorithme classique de partition en voies pour les chevauchements ; abréviations ; ou paysage forcé/zoom ; ou renoncer à la grille sur téléphone au profit de la vue jour avec axe horaire (C03).

**C23 — Notifications : contenu sensible et détection de nouvelles notes incomplète** · `AndroidNotifier.kt:35-45`, `NotificationPlanner.kt:123-138`, `EcoleDirecteRepository.kt:479` · important · petit · **[V]**
- Titre et texte (« Nouvelle note en Maths — 7/20 ») sont visibles écran verrouillé : aucun `setVisibility(VISIBILITY_PRIVATE)` ni version publique. Rien n'indique **quel enfant** pour un compte famille. Tous les canaux sont `IMPORTANCE_DEFAULT` (son pour une note).
- La détection de nouvelle note s'appuie sur `grades.recent` = les **6 notes les plus récentes triées par date d'évaluation**, pas par date de saisie. Un enseignant qui saisit aujourd'hui une note datée d'il y a deux semaines : si six notes plus récentes existent, elle n'apparaît jamais dans l'instantané et n'est **jamais notifiée**. Même défaut pour la pastille « nouvelles notes » (`newGrades` basé sur `date >= il y a 7 jours`).
- **Reco** : utiliser toutes les notes (ou `dateSaisie`) pour la détection ; contenu masqué sur l'écran verrouillé par défaut ; nom de l'enfant en sous-titre ; importance basse pour les devoirs/cours, normale pour les messages.

**C31 — Couleur de matière dérivée du nom brut** · `EcoleDirecteRepository.kt:512`, `Theme.kt` · confort · petit · **[V] code, effet [S]**
- `abs(subject.trim().lowercase().hashCode()) % 10` : 10 couleurs, collisions fréquentes, et un même cours peut s'appeler « MATHEMATIQUES » dans l'emploi du temps et « Maths » dans les notes (libellés différents selon les modules) → deux couleurs pour une matière. `codeMatiere` existe dans les réponses (emploi du temps, notes) et serait une clé stable. Les personnalisations sont aussi indexées par le nom → perdues si l'établissement renomme.

### 3.6 Accessibilité, textes, cohérence

**C30 — Accessibilité partielle** · divers · confort · moyen · **[V]**
- Bon : contrastes revus (tokens), `rememberReduceMotion`, rôle `Checkbox` sur les devoirs, boutons de champs avec description, texte en `sp`. À corriger : liens et actions faits avec `Text(...).clickable` sans taille tactile de 48 dp (« Retour », « Revenir à… », « Réessayer maintenant », « Détails techniques ») ; état « note ≥ moyenne de la classe » exprimé par la seule couleur verte (`GradePill`) ; lignes de liste non regroupées pour TalkBack (`mergeDescendants`) ; pas de `heading()` sur les titres de section ; pas de gestion de l'autofill (le plan promettait « saisie automatique Android » : aucune occurrence de `contentType/autofill`) → le gestionnaire de mots de passe de l'utilisateur n'aidera pas ; `remember` conditionnel dans le pager (`ScheduleView.kt:192`) ; textes 12 sp nombreux (limite basse du plan).
- Aucune ressource `strings.xml` hormis les libellés de synchro : tout est codé en dur en français. Acceptable pour le marché visé, mais bloque toute traduction et les tests de libellés.

### 3.7 Qualité de la chaîne de build et de la documentation

**C15 — La CI ne lance pas les tests ; aucun artefact de publication** · `.github/workflows/android.yml:36-47`, `app/build.gradle.kts` · important · petit · **[V]**
- Le workflow exécute uniquement `gradle assembleDebug`. Les ~70 tests unitaires ne sont jamais exécutés par la CI (« le build debug reste vert » ≠ « les tests passent »). Pas de `lint`, pas de build release signé, `minifyEnabled` absent (donc aucune réduction de code, avec `material-icons-extended` qui alourdit beaucoup l'APK release), pas de `proguard-rules`, aucun test instrumenté.
- **Reco** : `gradle testDebugUnitTest lintDebug` dans la CI ; configurer `release` (R8, signature par secret), limiter `material-icons-extended` aux icônes utilisées.

**C33 — Documentation obsolète et plan partiellement abandonné** · `README.md:38-50`, `PLAN-REFONTE.md` · confort · petit · **[V]**
- Le README affirme « les écrans scolaires affichent encore principalement des données de démonstration », décrit une navigation différente de celle du code et annonce des fonctions de la feuille de route déjà faites. Écarts du code par rapport au plan : pas de Room (cache fichier, choix défendable), jeton non conservé (C06), pas de routes de détail (feuilles modales à la place), l'onglet « Plus » n'a ni vie scolaire ni documents, le panneau de diagnostic réseau est toujours sur l'écran de connexion alors que le plan le déplaçait dans les réglages. La maquette web (`index.html`, `styles.css`, `app.js`) reste à la racine, mélangée à l'application.
- **Reco** : une seule source de vérité (README court + « Limites connues ») ; déplacer la maquette dans `docs/maquette/`.

**C34 — Imitation du JSON de Python et client HTTP « maison »** · `EcoleDirecteClient.kt:90-144, 234-288, 458-479` · confort · petit · **[V]**
- `toPythonJson()` reproduit `json.dumps` (séparateurs « , » et « : », `ensure_ascii`), `formEncode` réimplémente l'encodage de formulaire, tout sur `HttpURLConnection`. L'ensemble est exact mais ressemble à du copier-adapter d'un script Python sans preuve que le serveur l'exige. Si c'est inutile, c'est de la complexité à maintenir ; si c'est nécessaire, c'est une dépendance cachée à un détail de réponse du WAF — à documenter par un test qui compare avec une capture. Sans OkHttp, pas de journalisation de requêtes maîtrisée, pas de disjoncteur ni de mise en pause centralisée (C35).
- **Reco** : ne pas réécrire maintenant ; ajouter un test et un commentaire expliquant pourquoi, et un point d'entrée unique pour limiter le débit (voir C10).

---

## 4. Ce qui manque pour un « vrai » produit (par ordre d'impact pour un élève et un parent)

1. **Des données justes sur de vrais comptes** : notes (C01), emploi du temps (C03), validé par captures (C02).
2. **Vie scolaire** : absences/retards/justificatifs (C20) — premier besoin parent.
3. **Notifications fiables et discrètes** : contenu masqué, enfant identifié, détection complète (C23), multi-enfants (C13/C22).
4. **Pièces jointes et documents** téléchargeables (C19).
5. **Devoir fait synchronisé** avec ÉcoleDirecte (C16).
6. **Écriture de messages** (réponse) avec confirmation (C18).
7. **Vue « famille »** multi-enfants (C22).
8. **Respect des modules de l'établissement** (C21).
9. **Sélecteur de période + appréciations + bulletins** (C17).
10. **Fiabilité hors ligne et reprise**, rafraîchissement raisonnable (C09, C11).
11. **Confiance** : avertissement « non officiel », politique de confidentialité courte, page « Limites connues » (C27).
12. **Distribution** : icône, signature, release, canal de mise à jour (C05, C15). Un APK debug n'est pas une distribution.
13. Plus tard seulement : Pronote ou autre service (Papillon l'a fait, au prix d'une équipe), iOS, tablettes.

---

## 5. Liste PRIORISÉE (10 actions maximum)

1. **Corriger le parseur de notes et le choix de période (C01)** : `ensembleMatieres.moyenneGenerale`, période par dates, jamais `lastOrNull()` brut ; sélecteur de période. Effort : petit. C'est la cause directe d'un défaut observé.
2. **Ajouter un outil « capture anonymisée » (clés/types seulement, valeurs masquées) et construire de vraies fixtures (C02, C03)**, puis trancher le cas de l'emploi du temps (heures, pauses, `typeCours`, chevauchements). Effort : moyen.
3. **Un seul dépôt par profil, état de session thread-safe, ViewModel des pastilles lié au profil (C07, C08, C10)** + rafraîchissement à la reprise avec seuil de temps (C09). Effort : petit à moyen.
4. **Réduire les connexions : conserver et réutiliser le jeton (avec le même User-Agent), arrêter la synchro après `NeedsInteraction`/échecs répétés, gigue, un seul client (C06, C13, C28).** Effort : moyen. C'est la meilleure protection contre le blocage de compte.
5. **Icône d'application + widgets qui lisent le cache de l'app et calculent leurs horaires à l'affichage (C05, C04).** Effort : moyen.
6. **Protéger ce qui est déjà stocké : verrou sur le magasin de comptes, pas d'écrasement en cas d'échec de déchiffrement, écriture du cache seulement après parse utile, cache chiffré, `FLAG_SECURE` optionnel (C14, C11, C24).** Effort : moyen.
7. **CI : exécuter les tests, lint, build release signé (C15)** ; ajouter des tests de contrat avec les captures du point 2 et un test d'intégration multi-écrans du point 3. Effort : petit.
8. **Vie scolaire en lecture + respect des `modules` + pièces jointes téléchargeables (C20, C21, C19).** Effort : moyen. Plus gros gain pour les parents.
9. **Notifications : contenu privé par défaut, enfant nommé, détection basée sur toutes les notes, tous les enfants du compte (C23, C13, C22).** Effort : petit à moyen.
10. **Transparence et conformité : avertissement non officiel au premier lancement, README à jour, nom/branding non confusable, pas de publication sur le Play Store sous une identité ÉcoleDirecte (C27, C33).** Effort : petit.

(« Devoir fait synchronisé » C16 et « envoi de messages » C18 viennent après ces dix points : à ne tenter qu'avec des captures réelles et sur un compte de test.)

---

## 6. À ne surtout pas faire

**Pièges de blocage de compte et de bon sens**
- **Aucune boucle de reconnexion** après 505/516/535/517 ni après une réponse de sécurité refusée ; ne pas « réessayer jusqu'à ce que ça marche » dans la synchro ou au démarrage. Le code actuel le respecte presque partout : ne pas affaiblir `rejected`, ni les limites de `ApiSession.reauthenticate`.
- **Ne jamais deviner une réponse de sécurité** (ni tenter plusieurs propositions). Ne pas élargir `findBirthAnswer` à des correspondances approximatives.
- **Ne pas descendre sous 4 h de synchro**, ne pas lancer de synchro au démarrage de l'appareil, à chaque réveil du widget ou à chaque ouverture de l'écran. Ne pas paralléliser les requêtes pour « aller plus vite » : une rafale depuis une seule adresse est ce qui ressemble le plus à un abus.
- **Pas de préchargement massif** (`cahierdetexte/{date}` pour chaque jour, années précédentes `anneeScolaire`, tous les messages). N+1 requêtes = signal fort.
- **Ne pas randomiser le User-Agent**, ni imiter d'autres empreintes : le jeton est lié à celui qui l'a créé (doc), et l'imitation aléatoire aggrave le risque (C27).
- **Pas d'écriture vers ÉcoleDirecte** (marquer fait, répondre, justifier une absence) tant que l'appel exact, son effet de bord et sa réversibilité n'ont pas été vérifiés sur un compte de test, avec confirmation dans l'interface.
- **Ne pas lier la clé Keystore à l'authentification biométrique** pour « mieux protéger » : un changement d'empreinte invalide la clé et fait perdre tous les comptes enregistrés (aggravé par C14).
- **Ne pas épingler le certificat TLS** : l'API est derrière un CDN/WAF qui change ; l'épinglage ferait tomber toute l'app à la première rotation.
- **Pas de télémétrie, de crash-reporting avec contenu, ni de serveur intermédiaire** qui verrait des identifiants d'élèves (mineurs). Si un jour un serveur est ajouté pour les notifications push, c'est un autre produit, avec une autre exposition juridique.

**Sur-ingénierie à éviter**
- Pas de réécriture en Room/KMP/iOS, pas de Hilt/Koin, pas de couche « use case » supplémentaire avant que les données soient justes : le conteneur manuel et le cache fichier suffisent.
- Pas de nouvelles animations, de nouveaux thèmes ni de nouveaux réglages d'accueil tant que C01–C05 ne sont pas résolus : l'interface est déjà au-dessus de l'app officielle ; ce qui manque, ce sont des données et de la fiabilité.
- Pas de configuration distante de l'API tant qu'on n'a pas un moyen d'hébergement ni de signature de cette configuration.
- Pas de nouveau jeu de tests « maison » : chaque nouveau test de parseur doit partir d'une capture réelle anonymisée, sinon il ne protège de rien (C02).
- Pas de multi-service (Pronote, Skolengo) avant d'avoir stabilisé ÉcoleDirecte ; Papillon y consacre une équipe.

---

## 7. Récapitulatif chiffré

| Gravité | Nombre | Identifiants |
|---|---|---|
| Bloquant | 4 | C01, C03, C04, C05 |
| Important | 23 | C02, C06, C07, C08, C09, C10, C11, C12, C13, C14, C15, C16, C17, C18, C19, C20, C21, C22, C23, C24, C27, C28, C29 |
| Confort | 8 | C25, C26, C30, C31, C32, C33, C34, C35 |
| **Total** | **35** | |

Effort : petit (≈ 22 constats), moyen (≈ 11), grand (1 : écriture de messages).

Ce rapport ne remplace pas un essai sur appareil : trois constats ([S]) doivent être confirmés par exécution — C03 (cause réelle des heures manquantes), C04 (affichage dans le sélecteur de widgets), C08 (fuite des pastilles entre comptes).
