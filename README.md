# ecole2recte

`ecole2recte` est une refonte native, moderne et personnalisable de l'expérience
ÉcoleDirecte. L'ambition est de proposer une application réellement agréable à utiliser au
quotidien : une information claire, des actions rapides, une interface cohérente et une
reconnexion silencieuse quand le service distant expire la session.

Le projet ne cherche pas à reproduire chaque écran à l'identique. Il reprend les données
utiles d'ÉcoleDirecte pour construire une expérience plus lisible, plus personnelle et plus
fiable.

## Ce que fait l'application

- **Accueil composable** — dix blocs (prochain cours, à ne pas oublier, journée, devoirs,
  évaluations, notes, moyennes par matière, messages, semaine, demain) que l'on réordonne et
  masque depuis un éditeur dédié ; l'ordre est conservé sur l'appareil.
- **Emploi du temps** — deux lectures : la journée heure par heure, avec son résumé
  (nombre de cours, durée, première et dernière heure, avancement), ses trous de plus de
  trente minutes, les cours annulés barrés, le cours en cours mis en avant et **les devoirs
  à rendre affichés dans la case du cours concerné** ; et la semaine, six journées résumées
  et cliquables. Chaque cours ouvre une fiche complète.
- **Devoirs** — regroupés par échéance, filtrables (à faire, évaluations, faits, tout),
  cherchables, cochables d'un geste ; l'état « fait » est local d'abord, poussé au serveur
  ensuite, donc l'application répond même hors ligne. Le contenu détaillé et les pièces
  jointes arrivent à l'ouverture de la fiche, et les pièces jointes s'ouvrent réellement.
- **Notes** — sélection de période, moyenne générale de l'établissement quand elle existe
  (et moyenne calculée clairement annoncée comme telle sinon), moyennes par matière,
  détail dépliable de chaque note avec coefficient, moyenne de classe et appréciation,
  courbe d'évolution, et un **simulateur** : « si je prends 14 au prochain devoir, ça
  donne quoi ? », qui sait aussi dire quelle note viser — ou qu'un objectif est hors
  d'atteinte, plutôt que d'annoncer 27/20.
- **Messages** — liste paginée (l'année entière, pas les cent derniers), recherche, filtre
  « non lus », lecture du corps complet et ouverture des pièces jointes.
- **Vie scolaire** — absences, retards, sanctions et encouragements, avec compteurs et
  état de justification.
- **Documents** — bulletins et pièces administratives publiés par l'établissement.
- **Recherche globale** — une seule question pour toutes les sections.
- **Couleurs par matière** — une matière garde la même couleur partout ; sans choix
  explicite, elle est dérivée de son nom, donc stable d'un écran et d'un appareil à l'autre.
- **Thème clair, sombre ou système**, contrastes et libellés pensés pour les lecteurs
  d'écran (états annoncés, pastilles décoratives ignorées, boutons décrits).
- **Quinze widgets d'écran d'accueil Android** (voir plus bas), dont un configurable à la pose.
- **Notifications** — nouvelle note, nouveau devoir, nouveau message, rappel la veille d'une
  évaluation, chaque type activable séparément et jamais deux fois la même chose.
- **Synchronisation en arrière-plan** toutes les heures, qui alimente aussi les widgets.
- **Verrouillage** par empreinte ou code de l'appareil, en option.
- **Export iCalendar** de l'emploi du temps vers l'agenda du téléphone.
- **Mode hors ligne** — la dernière synchronisation est relue au lancement, avant tout appel
  réseau ; une section qui échoue laisse les données précédentes en place et signale son
  erreur, et la date de fraîcheur est affichée en permanence.
- **Reconnexion silencieuse** — un jeton expiré déclenche une reconnexion et un seul
  réessai, sans repasser par l'écran de connexion.

## Widgets d'écran d'accueil

Quinze widgets natifs, dans la même direction visuelle que l'application, en quatre tailles :

| Taille | Widgets |
| --- | --- |
| 1×1 | moyenne générale · devoirs à faire · messages non lus · temps avant le prochain cours · salle du prochain cours · dernière synchronisation |
| 2×1 | prochain cours · devoir urgent · dernière note · dernier message |
| 2×2 | journée · prochains devoirs · dernières notes · moyennes par matière |
| 4×2 | semaine |

Trois choix de conception :

- **le contenu est calculé dans `core`**, pas dans le widget : les quinze payloads sont des
  fonctions pures, testées, et un widget ne peut donc pas afficher autre chose que
  l'application ;
- **aucun appel réseau depuis un widget** — ils relisent le cache sur disque, celui-là même
  qu'écrit la synchronisation, ce qui les rend instantanés et silencieux ;
- **la mise à jour est poussée par l'application** dès qu'une donnée change (fin de
  synchronisation, devoir coché, couleur de matière modifiée), sans attendre le réveil
  système, qui n'a lieu qu'une fois par demi-heure au mieux.

Toucher un widget ouvre directement l'écran correspondant, pas seulement l'application. Les
couleurs suivent le thème clair ou sombre du système, et chaque matière garde la sienne.

## Architecture

```
app/src/main/java/fr/ecole2recte/app/
├── core/                      cœur métier, sans aucune dépendance Android
│   ├── model/                 modèles stables de l'application
│   ├── protocol/              client ÉcoleDirecte, encodages, codes métier
│   ├── parse/                 réponses JSON → modèles, en lecture défensive
│   ├── repo/                  dépôt, fusion, règles produit (« quel est le prochain cours ? »)
│   ├── settings/              réglages, widgets d'accueil, palette des matières
│   ├── store/                 contrats de stockage et sérialisation
│   ├── widget/                contenu des widgets d'écran d'accueil
│   ├── notify/                ce qui mérite une notification, et à quelles conditions
│   ├── export/                export iCalendar
│   ├── diag/                  journal d'erreurs expurgé
│   ├── format/                dates et textes en français, HTML → texte
│   └── auth/                  rapprochement d'une proposition de sécurité
├── data/                      implémentations Android des contrats de `core`
├── widget/                    les quinze widgets d'écran d'accueil (RemoteViews)
├── sync/                      synchronisation de fond et notifications
├── ui/                        écrans Compose, composants et thème
├── MainViewModel.kt           état d'interface et actions
└── MainActivity.kt
```

Le point important est la frontière : **`core` ne connaît ni `android.*` ni Compose**. Il ne
dépend que de la bibliothèque standard, de `org.json` et des coroutines. C'est ce qui rend
le protocole testable sur la JVM, et c'est aussi la brique qu'une future cible iOS ou
multiplateforme pourrait reprendre sans réécriture.

Le réseau passe par une interface `HttpEngine` : l'application y branche
`HttpURLConnection`, les tests y branchent des réponses enregistrées.

Le cache est un instantané complet (`SchoolSnapshot`) écrit en JSON dans le stockage privé,
dans le format de l'application et non dans celui d'ÉcoleDirecte — une évolution du
protocole ne rend donc pas illisibles les données déjà enregistrées.

## Tests

```
gradle testDebugUnitTest
```

Une soixantaine de tests couvrent ce qui casse en silence :

- les encodages exigés par l'API (séparateurs de `json.dumps`, `ensure_ascii`, corps de
  formulaire, ordre des champs de connexion) ;
- le parsing de réponses enregistrées et anonymisées : comptes, emploi du temps, cahier de
  textes, notes, messagerie, y compris les réponses vides ou tronquées ;
- le protocole complet rejoué de bout en bout : connexion, code 250 et question de sécurité,
  réponse validée rejouée, identifiants refusés sans réessai, jeton expiré ;
- les règles de fusion (pas de doublons, contenus déjà chargés préservés) ;
- le dépôt : ouverture hors ligne sur le cache, échec d'une seule section, reconnexion
  silencieuse avec un unique réessai, déconnexion ;
- les règles d'accueil (prochain cours, devoir urgent, moyennes pondérées, résumé d'une
  journée, trous, croisement devoirs ↔ cours) et la personnalisation (ordre des blocs,
  couleurs, état local des devoirs) ;
- le contenu des quinze widgets d'écran d'accueil, y compris sans aucune donnée : un widget
  qui plante reste vide jusqu'au redémarrage du lanceur, c'est le pire des échecs silencieux.

Aucun test n'a besoin d'un compte réel ni d'un accès réseau : c'est la condition pour qu'ils
tournent en intégration continue sans risquer de bloquer un compte.

## Sécurité et vie privée

- Identifiants chiffrés en AES/GCM par une clé qui ne quitte pas le Keystore Android ; le
  mot de passe est conservé parce que l'API n'offre aucun jeton de rafraîchissement, et rien
  d'autre.
- Sauvegarde cloud et transfert d'appareil désactivés.
- Les diagnostics réseau sont expurgés des mots de passe, jetons et cookies avant d'être
  affichés ou copiés, et restent masqués tant qu'on ne les demande pas.
- Aucune réponse de sécurité n'est devinée : une proposition n'est pré-sélectionnée que si
  elle correspond exactement à une information saisie par l'utilisateur, qui garde le dernier
  mot. Aucune tentative n'est enchaînée automatiquement.

## Feuille de route

L'état d'avancement détaillé, et ce qui reste à faire, sont dans [ROADMAP.md](ROADMAP.md).

## Limites connues

- L'API d'ÉcoleDirecte n'est pas publique ni documentée : un code HTTP `200` ne signifie pas
  que l'opération a réussi, et le protocole peut changer sans préavis. Les chemins, verbes et
  codes sont donc rassemblés dans un seul fichier, et couverts par des tests.
- La messagerie est en lecture seule : envoyer un message depuis un protocole non documenté
  et non testé pourrait livrer un contenu tronqué à un professeur. De même, le dépôt de copie
  en ligne n'est pas implémenté — rendre un devoir est irréversible.
- Les widgets d'écran d'accueil se rafraîchissent d'eux-mêmes toutes les trente minutes au
  mieux — c'est la limite du système ; entre deux, la synchronisation de fond horaire et
  l'application les mettent à jour dès qu'il y a du nouveau.
- Le parsing est défensif et testé sur des réponses enregistrées, mais chaque établissement
  configure ÉcoleDirecte à sa façon : certaines variantes n'ont pas pu être vérifiées.

## Lancer le projet

Ouvrir le dépôt dans Android Studio avec un SDK Android installé, puis lancer la
configuration `app`. Le projet cible Android API 35 et nécessite au minimum Android 8
(API 26).

La maquette web (`index.html`, `styles.css`, `app.js`) reste disponible comme référence
UX/UI ; elle ne remplace pas l'application native.

## Build automatique

Le workflow `.github/workflows/android.yml` exécute, à chaque push et pull request : les
tests unitaires, le lint Android (bloquant), la compilation des tests d'interface, puis les
APK debug et release. Les deux APK sont publiés dans les artefacts du workflow, et les
rapports de tests ou de lint sont joints en cas d'échec.

Les tests d'interface eux-mêmes demandent un appareil ou un émulateur :
`gradle connectedDebugAndroidTest`.

Le build utilise Gradle 8.11.1, Java 17 et le SDK Android API 35.
