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
