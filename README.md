# ecole2recte

`ecole2recte` est une refonte native, moderne et personnalisable de l'expérience
ÉcoleDirecte. L'ambition est de proposer une application réellement agréable à utiliser au
quotidien : une information claire, des actions rapides, une interface cohérente et une
reconnexion silencieuse quand le service distant expire la session.

Le projet ne cherche pas à reproduire chaque écran à l'identique. Il reprend les données
utiles d'ÉcoleDirecte pour construire une expérience plus lisible, plus personnelle et plus
fiable.

## Ce que fait l'application

- **Accueil composable** — six widgets (prochain cours, à ne pas oublier, journée, devoirs,
  notes, messages) que l'on réordonne et masque depuis un éditeur dédié ; l'ordre est
  conservé sur l'appareil.
- **Emploi du temps** — navigation par semaine et par jour, cours annulés barrés, cours en
  cours mis en avant, trous de plus de trente minutes rendus visibles.
- **Devoirs** — regroupés par échéance, filtrables (à faire, évaluations, faits, tout),
  cherchables, cochables d'un geste ; l'état « fait » est local d'abord, poussé au serveur
  ensuite, donc l'application répond même hors ligne. Le contenu détaillé d'un devoir est
  chargé à l'ouverture de sa fiche.
- **Notes** — sélection de période, moyenne générale de l'établissement quand elle existe
  (et moyenne calculée clairement annoncée comme telle sinon), moyennes par matière,
  détail dépliable de chaque note avec coefficient, moyenne de classe et appréciation.
- **Messages** — liste, recherche, filtre « non lus », lecture du corps complet avec pièces
  jointes listées.
- **Couleurs par matière** — une matière garde la même couleur partout ; sans choix
  explicite, elle est dérivée de son nom, donc stable d'un écran et d'un appareil à l'autre.
- **Thème clair, sombre ou système**, contrastes et libellés pensés pour les lecteurs
  d'écran (états annoncés, pastilles décoratives ignorées, boutons décrits).
- **Mode hors ligne** — la dernière synchronisation est relue au lancement, avant tout appel
  réseau ; une section qui échoue laisse les données précédentes en place et signale son
  erreur, et la date de fraîcheur est affichée en permanence.
- **Reconnexion silencieuse** — un jeton expiré déclenche une reconnexion et un seul
  réessai, sans repasser par l'écran de connexion.

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
│   ├── format/                dates et textes en français, HTML → texte
│   └── auth/                  rapprochement d'une proposition de sécurité
├── data/                      implémentations Android des contrats de `core`
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

Une cinquantaine de tests couvrent ce qui casse en silence :

- les encodages exigés par l'API (séparateurs de `json.dumps`, `ensure_ascii`, corps de
  formulaire, ordre des champs de connexion) ;
- le parsing de réponses enregistrées et anonymisées : comptes, emploi du temps, cahier de
  textes, notes, messagerie, y compris les réponses vides ou tronquées ;
- le protocole complet rejoué de bout en bout : connexion, code 250 et question de sécurité,
  réponse validée rejouée, identifiants refusés sans réessai, jeton expiré ;
- les règles de fusion (pas de doublons, contenus déjà chargés préservés) ;
- le dépôt : ouverture hors ligne sur le cache, échec d'une seule section, reconnexion
  silencieuse avec un unique réessai, déconnexion ;
- les règles d'accueil (prochain cours, devoir urgent, moyennes pondérées) et la
  personnalisation (ordre des widgets, couleurs, état local des devoirs).

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

## Limites connues

- L'API d'ÉcoleDirecte n'est pas publique ni documentée : un code HTTP `200` ne signifie pas
  que l'opération a réussi, et le protocole peut changer sans préavis. Les chemins, verbes et
  codes sont donc rassemblés dans un seul fichier, et couverts par des tests.
- Les pièces jointes sont listées mais pas encore téléchargeables.
- La messagerie est en lecture seule.
- Notifications et widgets d'écran d'accueil Android ne sont pas encore implémentés.

## Feuille de route

- téléchargement des pièces jointes et ouverture des documents ;
- notifications utiles (devoir urgent, nouvelle note, message non lu) et widgets Android ;
- tests d'interface sur les parcours principaux ;
- signature et distribution d'une version bêta ;
- extraction de `core` vers un module partagé pour une cible iOS.

## Lancer le projet

Ouvrir le dépôt dans Android Studio avec un SDK Android installé, puis lancer la
configuration `app`. Le projet cible Android API 35 et nécessite au minimum Android 8
(API 26).

La maquette web (`index.html`, `styles.css`, `app.js`) reste disponible comme référence
UX/UI ; elle ne remplace pas l'application native.

## Build automatique

Le workflow `.github/workflows/android.yml` exécute les tests unitaires puis compile l'APK
debug à chaque push et pull request. L'APK est publié dans les artefacts du workflow sous le
nom `ecole2recte-debug-apk`, et le rapport de tests est joint en cas d'échec.

Le build utilise Gradle 8.11.1, Java 17 et le SDK Android API 35.
