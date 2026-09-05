# ecole2recte

Application Android native en Kotlin/Jetpack Compose pour rendre ÉcoleDirecte plus
simple, lisible et personnalisable.

## État du projet

Le premier socle Android est en place dans `app/` :

- navigation complète entre Accueil, Emploi du temps, Devoirs, Notes et Messages ;
- accueil composé de cartes réutilisables ;
- emploi du temps et prochain cours avec couleur par matière ;
- dialogue de personnalisation des couleurs ;
- données de démonstration isolées dans les modèles Kotlin ;
- client Kotlin dédié pour le protocole de connexion ÉcoleDirecte ;
- gestion du flux GTK, reconnexion avec appareil approuvé et question de sécurité ;
- récupération de l'emploi du temps via l'endpoint ÉcoleDirecte ;
- stockage chiffré Android Keystore pour les identifiants et la preuve d'appareil ;
- écran de connexion natif avec reconnexion automatique au lancement ;
- architecture prête à remplacer les données locales par la synchronisation réelle.

La maquette web (`index.html`, `styles.css`, `app.js`) reste disponible comme référence
UX/UI pendant le développement natif.

## Lancer le projet

Ouvrir le dépôt dans Android Studio avec un SDK Android installé, puis lancer la
configuration `app`. Le projet cible Android API 35 et nécessite au minimum Android 8
(API 26).

Le client réseau est maintenant branché au parcours de connexion. Les écrans scolaires
utilisent encore des données de démonstration ; la prochaine étape fonctionnelle est de
remplacer ces données par un cache local alimenté par la synchronisation.

## Build automatique

Le workflow `.github/workflows/android.yml` compile l'APK debug sur chaque push et
pull request. L'APK est ensuite disponible dans les artefacts du workflow sous le nom
`ecole2recte-debug-apk`. Le build utilise Gradle 8.11.1, Java 17 et le SDK Android API
35 directement sur GitHub Actions.
