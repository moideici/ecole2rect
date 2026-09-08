# ecole2recte

`ecole2recte` est une refonte native, moderne et personnalisable de l'expérience
ÉcoleDirecte. L'ambition est de proposer une application réellement agréable à utiliser au
quotidien : une information claire, des actions rapides, une interface cohérente et une
reconnexion silencieuse quand le service distant expire la session.

Le projet ne cherche pas à reproduire chaque écran à l'identique. Il reprend les données
utiles d'ÉcoleDirecte pour construire une expérience plus lisible, plus personnelle et plus
fiable.

## Ce que fait l'application

- **Accueil composable** — dix blocs (prochain cours, à ne pas oublier, journée **en grille
  à l'échelle du temps**, devoirs,
  évaluations, notes, moyennes par matière, messages, semaine, demain) que l'on réordonne et
  masque depuis un éditeur dédié ; l'ordre est conservé sur l'appareil.
- **Emploi du temps** — une **grille à l'échelle du temps**, comme sur un emploi du temps
  papier : un cours de trois heures y est trois fois plus haut qu'un cours d'une heure, un
  trou est un vide qu'on voit sans le lire, et la pause méridienne saute aux yeux sans qu'on
  ait à la nommer. Les cours dédoublés sont côte à côte plutôt qu'empilés, une ligne rouge
  marque l'heure courante, les cours annulés restent visibles et barrés, et **un devoir à
  rendre se signale dans la case de son cours**. Deux lectures : la journée, et la semaine —
  six colonnes sur une même règle d'heures, ce qui permet de comparer deux journées d'un
  regard. Chaque cours ouvre une fiche complète.

  Les bornes de la grille viennent des cours eux-mêmes : commencer à 8 h et finir à 18 h
  laisserait un vide en haut pour qui commence à 10 h, et couperait le bas pour qui finit à
  19 h.
- **Devoirs** — regroupés par échéance, filtrables (à faire, évaluations, faits, tout),
  cherchables, cochables d'un geste ; l'état « fait » est local d'abord, poussé au serveur
  ensuite, donc l'application répond même hors ligne. Un devoir à déposer en ligne est
  signalé comme tel — cocher une case dans l'application ne rend pas une copie. Le contenu détaillé et les pièces
  jointes arrivent à l'ouverture de la fiche, et les pièces jointes s'ouvrent réellement.
- **Notes** — tout ce que porte le bulletin : appréciation du conseil de classe avec sa date
  et le professeur principal, appréciations par matière (toutes, quand plusieurs professeurs
  se la partagent), moyennes minimale et maximale de la classe, coefficients, type de chaque
  devoir et **compétences du socle** évaluées avec la note. Plus : sélection de période,
  moyenne générale de l'établissement quand elle existe
  (et moyenne calculée clairement annoncée comme telle sinon), moyennes par matière,
  détail dépliable de chaque note avec coefficient, moyenne de classe et appréciation,
  courbe d'évolution, et un **simulateur** : « si je prends 14 au prochain devoir, ça
  donne quoi ? », qui sait aussi dire quelle note viser — ou qu'un objectif est hors
  d'atteinte, plutôt que d'annoncer 27/20.
- **Messages** — les trois dossiers du service (reçus, envoyés, archivés), liste paginée
  (l'année entière, pas les cent derniers), recherche, filtre « non lus », destinataires des
  messages envoyés, lecture du corps complet et ouverture des pièces jointes.
- **Vie scolaire** — absences, retards, sanctions et encouragements, avec compteurs, durée
  telle que l'établissement la compte, motif et état de justification.
- **Documents** — bulletins et pièces administratives publiés par l'établissement.
- **Actualités de l'établissement** — ce que l'école adresse à tout le monde, pièces jointes
  comprises, avec un bloc sur l'accueil.
- **Manuels numériques** — la liste des manuels associés au compte, ouverts chez leur éditeur.
- **Espace de stockage** — les fichiers partagés, arborescence aplatie et téléchargeables.
- **Recherche globale** — une seule question pour toutes les sections.
- **Couleurs par matière** — une matière garde la même couleur partout ; sans choix
  explicite, elle est dérivée de son nom, donc stable d'un écran et d'un appareil à l'autre.
- **Thème clair, sombre ou système**, contrastes et libellés pensés pour les lecteurs
  d'écran : titres annoncés comme des titres, état de synchronisation annoncé de lui-même
  quand il change, courbe d'évolution résumée en une phrase, pastilles décoratives ignorées,
  boutons décrits, cibles tactiles à la taille minimale recommandée. Chaque widget d'écran
  d'accueil se résume aussi en une phrase, au lieu de laisser lire ses vues une par une.
- **Recherche accessible depuis toutes les pages**, et navigation jour par jour de l'emploi
  du temps aux flèches autant qu'au balayage — un geste horizontal n'a pas d'équivalent au
  lecteur d'écran.
- **Seize widgets d'écran d'accueil Android** (voir plus bas), dont un configurable à la pose.
- **Notifications** — nouvelle note, nouveau devoir, nouveau message, rappel la veille d'une
  évaluation, chaque type activable séparément et jamais deux fois la même chose.
- **Synchronisation en arrière-plan** toutes les heures, qui alimente aussi les widgets. Elle
  s'en tient aux six sections toujours présentes ; les trois modules facultatifs
  (actualités, manuels, stockage) se chargent à l'ouverture de leur écran ou lors d'une
  synchronisation demandée à la main, pour ne pas réclamer chaque heure ce qu'un
  établissement n'a pas ouvert.
- **Verrouillage** par empreinte ou code de l'appareil, en option.
- **Export iCalendar** de l'emploi du temps vers l'agenda du téléphone.
- **Mode hors ligne** — la dernière synchronisation est relue au lancement, avant tout appel
  réseau ; une section qui échoue laisse les données précédentes en place et signale son
  erreur, et la date de fraîcheur est affichée en permanence.
- **Reconnexion silencieuse** — un jeton expiré déclenche une reconnexion et un seul
  réessai, sans repasser par l'écran de connexion. Si ÉcoleDirecte repose sa question de
  sécurité, l'application sait y répondre seule (voir plus bas) : en usage normal, on ne se
  connecte qu'une fois.
- **Comptes parents** — une connexion parent donne une famille et ses enfants : chaque enfant
  est un profil complet, la messagerie reste celle de la famille, l'accueil annonce l'enfant
  au lieu de saluer quelqu'un d'autre, et une rangée de pastilles passe de l'un à l'autre
  sans nouvelle synchronisation. Un devoir coché depuis un compte parent reste local : le
  cahier de textes de l'élève n'est pas modifié à sa place.

## Se connecter une fois

ÉcoleDirecte expire les sessions et repose, de temps à autre, une question de sécurité — au
lancement, mais aussi au milieu d'une synchronisation de fond, quand personne ne regarde
l'écran. Une application qui renvoie alors vers un formulaire perd sa promesse.

L'application y répond donc seule, sous quatre garde-fous, qui sont la partie importante :

1. **rien n'est deviné** — la réponse vient d'une date de naissance saisie par l'utilisateur,
   ou d'une réponse qu'il a lui-même validée auparavant ;
2. **rien n'est approché** — elle doit correspondre *exactement* à l'une des propositions du
   serveur, sinon la question est posée ;
3. **rien n'est enchaîné** — une seule tentative automatique par connexion ; refusée, elle
   est oubliée et la main revient à l'utilisateur, avec la raison affichée ;
4. **rien n'est éternel** — une réponse mémorisée expire au 1er septembre suivant, parce
   qu'une classe change d'année en année et qu'une vieille réponse deviendrait fausse.

Sur un compte parent, la question porte sur la date de naissance de l'**enfant**, jamais sur
celle du parent : c'est pourquoi l'écran de connexion demande d'abord de qui on parle. Une
fratrie a une date par enfant, et la question nomme celui qu'elle concerne — le prénom est
cherché comme un mot entier, pour qu'« Anne » ne réponde pas à la place de « Marianne ».

Dates de naissance et réponses mémorisées vivent dans le trousseau chiffré, avec le mot de
passe. Elles se relisent, se corrigent et s'effacent dans **Réglages → Connexion automatique**.

## Widgets d'écran d'accueil

Seize widgets natifs, dans la même direction visuelle que l'application. Le tableau donne
leur taille *à l'installation* — pas leur taille de vie : ils sont tous redimensionnables
dans les deux sens, et leur contenu suit.

| À l'installation | Widgets |
| --- | --- |
| 1×1 | moyenne générale · devoirs à faire · messages non lus · avant le prochain cours · prochaine évaluation · absences à justifier |
| 2×1 | **horaires du jour** · prochain cours · devoir urgent · dernière note · dernier message |
| 2×2 | journée · prochains devoirs · dernières notes · moyennes par matière |
| 4×2 | semaine |

### Le contenu suit la taille

Un widget Android n'a pas de dimensions fixes : on l'étire, et deux lanceurs ne donnent pas
la même place pour « 2×2 ». Un widget dessiné pour une seule taille est donc vide sur un
grand écran et tronqué sur un petit. Ici, chaque widget sait se dire de cinq façons — une
tuile, une ligne, trois repères, une liste, une semaine — et c'est la place réellement
accordée qui choisit :

- « moyenne » montre un chiffre en une case, la moyenne avec sa tendance et la moyenne de
  classe sur deux cases de large, le détail matière par matière dès qu'il a deux cases de
  haut ;
- « semaine » exige de la largeur ; rétrécie, elle se replie en un jour par ligne ;
- une liste montre de une à huit lignes selon la hauteur, au lieu de quatre quoi qu'il arrive ;
- « horaires du jour » aligne trois chiffres — début, pause méridienne, fin — dans un 2×1 ;
  agrandi il devient la journée détaillée, réduit à une case le compte à rebours du prochain
  cours.

**Chaque ligne d'une liste commence par son heure**, comme un emploi du temps papier :
l'heure d'abord, la matière ensuite, la salle à droite. Mettre l'horaire à droite obligeait à
lui réserver de la place au milieu de la ligne, et c'est la matière qui se faisait couper —
« Mathémati… » n'apprend rien à personne.

**Un widget occupe la hauteur qu'on lui donne.** Le lanceur annonce une taille par
orientation ; retenir la plus petite revenait à dessiner pour le paysage et laissait la
moitié du widget vide en portrait, c'est-à-dire dans l'orientation où on le regarde. On
retient donc la largeur du plus étroit — aucune ligne coupée — et la hauteur du plus haut.

**La pause méridienne n'est écrite nulle part** dans la réponse d'ÉcoleDirecte : c'est un
trou parmi d'autres, qu'il faut reconnaître parmi les autres trous. Le plus long n'est pas le
bon critère, et c'est l'erreur qui saute aux yeux un jour de permanence : deux heures libres
de 10 h à 12 h sont plus longues que le déjeuner. Ce qui distingue le repas, ce n'est pas sa
durée, c'est **l'endroit de la journée qu'il occupe** — on garde donc le trou qui recouvre le
mieux le créneau du repas, et à recouvrement égal celui dont le milieu tombe le plus près de
la mi-journée. Une journée continue affiche un tiret plutôt qu'un midi inventé, et un trou
qui n'effleure ce créneau que de quelques minutes reste une permanence.

Agrandir un widget ajoute donc de l'information, jamais du vide. Sur Android 12 et suivants,
l'application fournit **un rendu par taille annoncée** : le lanceur passe de l'un à l'autre
sans réveiller l'application, et le redimensionnement est immédiat.

### Un widget ne dit jamais « rien »

Vendredi soir, « journée terminée » n'apprend rien ; ce qu'on veut savoir, c'est ce qui
attend lundi. La journée bascule donc sur le **prochain jour d'école**, avec son libellé
(« DEMAIN », « LUNDI »). Le prochain cours annonce le jour quand ce n'est pas aujourd'hui —
« dans 18 h » ne dit pas qu'il s'agit de demain matin. Un devoir urgent absent laisse la
place à la prochaine évaluation. Et pendant un cours, une barre d'avancement dit d'un regard
s'il reste dix minutes.

### Un widget par enfant

Quand plusieurs élèves sont en cache, la pose d'un widget demande lequel il doit suivre : un
parent installe la journée de l'aîné à côté de celle de la cadette, et changer de profil dans
l'application n'y touche pas. Quand il n'y a rien à demander — un seul élève, aucun réglage —
l'écran de configuration ne s'affiche pas.

### Trois choix de conception

- **le contenu est calculé dans `core`**, pas dans le widget : les payloads sont des
  fonctions pures, testées dans les quatre formes, et un widget ne peut donc pas afficher
  autre chose que l'application ;
- **aucun appel réseau depuis un widget** — ils relisent le cache sur disque, celui-là même
  qu'écrit la synchronisation, ce qui les rend instantanés et silencieux ;
- **la mise à jour est poussée par l'application** dès qu'une donnée change (fin de
  synchronisation, devoir coché, couleur de matière modifiée) et **au déverrouillage du
  téléphone**, sans attendre le réveil système, qui n'a lieu qu'une fois par demi-heure au
  mieux. Le redessin au déverrouillage ne coûte rien : il relit le cache, sans réseau ; une
  vraie synchronisation n'est demandée que si les données ont plus de vingt minutes.
  `ACTION_USER_PRESENT` ne faisant pas partie des diffusions qu'un receveur déclaré au
  manifeste peut recevoir depuis Android 8, l'écoute est enregistrée à l'exécution : elle
  vaut tant que le processus vit, et la synchronisation horaire reste le filet en dessous.

Un widget qui répète un autre widget ne mérite pas sa place : c'est ce qui a coûté leur
place à « salle du prochain cours », que « prochain cours » disait déjà, et à « dernière
synchronisation », qui renseignait sur l'application plutôt que sur l'école.

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
│   └── auth/                  réponse automatique à la question de sécurité, et ses limites
├── data/                      implémentations Android des contrats de `core`
├── widget/                    les seize widgets d'écran d'accueil (RemoteViews)
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

Une centaine de tests couvrent ce qui casse en silence :

- les encodages exigés par l'API (séparateurs de `json.dumps`, `ensure_ascii`, corps de
  formulaire, ordre des champs de connexion) ;
- le parsing de réponses enregistrées et anonymisées : comptes, emploi du temps, cahier de
  textes, notes, messagerie, y compris les réponses vides ou tronquées ;
- le protocole complet rejoué de bout en bout : connexion, code 250 et question de sécurité,
  réponse validée rejouée, identifiants refusés sans réessai, jeton expiré ;
- la réponse automatique et ses garde-fous : date reconnue quelle que soit son écriture,
  proposition absente qui ne déclenche rien, réponse mémorisée qui expire à la rentrée,
  réponse refusée aussitôt oubliée, une seule tentative par connexion, prénoms de fratrie
  qui se contiennent ;
- la connexion d'un compte parent : un profil par enfant, la messagerie de la famille, les
  données scolaires de l'enfant, et la coche de devoir qui ne part pas au serveur ;
- les règles de fusion (pas de doublons, contenus déjà chargés préservés) ;
- le dépôt : ouverture hors ligne sur le cache, échec d'une seule section, reconnexion
  silencieuse avec un unique réessai, déconnexion ;
- les règles d'accueil (prochain cours, devoir urgent, moyennes pondérées, résumé d'une
  journée, trous, croisement devoirs ↔ cours) et la personnalisation (ordre des blocs,
  couleurs, état local des devoirs) ;
- le contenu des seize widgets d'écran d'accueil, y compris sans aucune donnée : un widget
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
- Aucune réponse de sécurité n'est devinée : l'application ne rejoue qu'une information que
  l'utilisateur a lui-même donnée, et seulement si elle correspond exactement à une
  proposition du serveur. Une seule tentative par connexion, jamais d'essais en série ; une
  réponse refusée est effacée et la question revient à l'utilisateur.
- Les dates de naissance et les réponses mémorisées sont chiffrées comme le mot de passe,
  visibles et effaçables dans les réglages, et effacées à la déconnexion.

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
