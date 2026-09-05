# ecole2recte

`ecole2recte` est une refonte native, moderne et personnalisable de l'expérience
ÉcoleDirecte. L'ambition est de proposer une application réellement agréable à
utiliser au quotidien : une information claire, des actions rapides, une interface
cohérente et une reconnexion silencieuse quand le service distant expire la session.

Le projet ne cherche pas à reproduire chaque écran à l'identique. Il veut reprendre les
données utiles d'ÉcoleDirecte pour construire une expérience plus lisible, plus
personnelle et plus fiable.

## Ambitions du dépôt

### Une expérience premium

- accueil entièrement personnalisable, composé de widgets réorganisables ;
- navigation directe vers des pages complètes pour le planning, les devoirs, les notes
  et les messages ;
- design épuré, lisible et cohérent plutôt qu'une accumulation de fonctionnalités ;
- couleurs personnalisables par matière, conservées dans l'accueil et l'emploi du temps ;
- informations importantes visibles immédiatement : prochain cours, devoir urgent,
  nouvelle note ou message non lu ;
- notifications utiles et widgets Android, sans spam ni bruit visuel ;
- accessibilité, états de chargement, erreurs et mode hors ligne traités comme des
  fonctionnalités de premier ordre.

### Une base technique durable

- Kotlin et Jetpack Compose pour l'application Android ;
- séparation progressive entre interface, état, client réseau et cache local ;
- données distantes converties vers des modèles stables propres à l'application ;
- stockage local chiffré pour les secrets nécessaires à la reconnexion ;
- diagnostics réseau utiles au développement, mais expurgés des mots de passe, jetons et
  cookies ;
- tests unitaires des encodeurs, parseurs et réponses enregistrées afin de ne pas
  dépendre d'un compte réel dans la CI ;
- préparation d'une évolution multiplateforme, notamment iOS, sans sacrifier
  l'expérience Android.

## État actuel

Le socle Android est fonctionnel dans `app/` :

- navigation Accueil, Emploi du temps, Devoirs, Notes et Messages ;
- écran d'accueil et cartes de démonstration issus de la maquette ;
- couleurs par matière et éditeur de personnalisation ;
- client Kotlin dédié au protocole privé ÉcoleDirecte ;
- flux GTK complet avec conservation du `X-GTK`, de tous les cookies de session et
  des jetons de double authentification ;
- question de sécurité affichée via l'appel `doubleauth` dédié ;
- choix de naissance jour/mois/année prérempli automatiquement quand une proposition
  correspond aux champs saisis ;
- reconnexion automatique et stockage chiffré Android Keystore ;
- diagnostic réseau copiable, sans secrets ;
- build debug automatisé par GitHub Actions.

Les écrans scolaires affichent encore principalement des données de démonstration. La
connexion réelle et le protocole d'authentification sont la priorité stabilisée ; la
prochaine étape est de brancher une synchronisation fiable et un cache local aux pages
complètes.

## Feuille de route

### Phase 1 — Authentification fiable

- stabiliser le login et la double authentification sur plusieurs comptes ;
- gérer explicitement expiration, renouvellement, déconnexion et compte bloqué ;
- conserver uniquement les données nécessaires à la reconnexion ;
- ajouter des tests hors ligne pour les variantes de réponses de l'API.

### Phase 2 — Données et mode hors ligne

- créer une couche repository et un cache local ;
- synchroniser planning, devoirs, notes et messages ;
- afficher la date de dernière synchronisation ;
- conserver la dernière donnée valide quand le réseau est indisponible ;
- éviter les doublons grâce à des identifiants stables et à une synchronisation
  idempotente.

### Phase 3 — Produit quotidien

- remplacer les données de démonstration par les données synchronisées ;
- permettre l'ouverture d'une fiche détaillée depuis chaque carte ;
- ajouter filtres, recherche, devoirs terminés et lecture des messages ;
- finaliser la personnalisation de l'accueil et des matières ;
- ajouter notifications et widgets Android.

### Phase 4 — Qualité et extension

- tests d'interface et tests de synchronisation ;
- amélioration de l'accessibilité et des performances ;
- signature et distribution d'une version bêta ;
- extraction des briques partageables pour une cible iOS ou multiplateforme ;
- documentation de migration lorsque l'API privée évolue.

## Limites et responsabilité

ÉcoleDirecte n'expose pas ici une API publique documentée. Le client repose donc sur un
protocole non officiel susceptible de changer sans préavis. Un code HTTP `200` ne
signifie pas forcément que l'opération a réussi : le code métier JSON doit être
inspecté.

Le projet ne doit jamais contourner une protection, deviner une réponse de sécurité ou
multiplier les tentatives. Les réponses de naissance sont utilisées uniquement quand
elles ont été saisies par l'utilisateur et qu'elles correspondent aux propositions
retournées par le serveur. Les identifiants, mots de passe, cookies et jetons ne doivent
pas être ajoutés aux commits, aux logs ou aux tickets.

## Lancer le projet

Ouvrir le dépôt dans Android Studio avec un SDK Android installé, puis lancer la
configuration `app`. Le projet cible Android API 35 et nécessite au minimum Android 8
(API 26).

La maquette web (`index.html`, `styles.css`, `app.js`) reste disponible comme référence
UX/UI. Elle peut être ouverte avec un serveur statique local ; elle ne remplace pas
l'application native.

## Build automatique

Le workflow `.github/workflows/android.yml` compile l'APK debug sur chaque push et
pull request. L'APK est publié dans les artefacts du workflow sous le nom
`ecole2recte-debug-apk`.

Le build utilise Gradle 8.11.1, Java 17 et le SDK Android API 35 sur GitHub Actions.
