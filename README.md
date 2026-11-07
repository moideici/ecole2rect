# ecole2recte

Client Android **non officiel** pour ÉcoleDirecte : emploi du temps, devoirs, notes et
messages dans une application lisible, personnalisable et utilisable hors ligne.

> **Non officielle, non affiliée.** ecole2recte n'est ni éditée, ni approuvée par
> ÉcoleDirecte ni par son éditeur. Elle utilise l'interface privée du service, qui n'est pas
> documentée et peut changer sans préavis. En cas de doute sur une information, le site
> officiel fait foi.

## Ce que fait l'application

- **Accueil** : prochain cours relié au reste de la journée, devoir à ne pas oublier,
  « à savoir » classé par urgence, dernières notes.
- **Agenda** : emploi du temps en vue jour ou en vue semaine (colonnes sur une frise commune), avec heures,
  pauses et cours doubles, zoom au pincement ou par boutons, cours annulés barrés, cours déplacés ou modifiés
  montrés avec l'ancienne valeur barrée ; compte à rebours avant le prochain contrôle (aussi à l'accueil) ;
  travail à faire (filtres À faire / Faits / Contrôles / Tous), cases « fait » gardées sur le téléphone.
- **Exports** : emploi du temps en PDF (A4 paysage) ou en calendrier ICS, avec les semaines 1 / 2 si le tien
  alterne ; « Ajouter au calendrier » pour un cours ou un contrôle, sans permission. Voir [Exports](docs/EXPORTS.md).
- **Notes** : moyennes par matière et par période, appréciations, valeurs textuelles
  (Abs, Disp, NE), moyenne de la classe et extrêmes (avec l'écart de chaque note à la classe),
  simulateur d'objectif, courbes d'évolution, pastille « Nouveau » pendant trois jours. Une moyenne est
  toujours étiquetée **officielle** (fournie par ÉcoleDirecte) ou **calculée par l'app** (estimation, souvent
  un peu plus basse) : voir [Comment la moyenne est calculée](docs/MOYENNES.md).
- **Messages** : boîte de réception, pièces jointes ouvertes seulement après confirmation,
  « lu / non lu » modifiable **sur le téléphone seulement** (rien n'est écrit chez ÉcoleDirecte).
- **Recherche** : une barre à l'accueil et dans Plus cherche dans les devoirs, messages (expéditeur, objet),
  notes et cours déjà enregistrés sur le téléphone ; elle n'envoie aucune requête.
- **Comptes** : plusieurs comptes, élèves d'un compte famille, vue d'ensemble de la famille.
- **Notifications** (désactivées par défaut) : cours annulé, modifié ou déplacé (« 10:00 → 14:00 »), nouvelle note,
  message, rappel du soir des devoirs pour demain (à partir de l'heure choisie, 16 à 20 h) ;
  réglables une à une, discrètes sur l'écran verrouillé. Raccourcis d'application : Agenda, Devoirs,
  Messages, Rechercher.
- **Widgets** : six widgets, aux couleurs du thème choisi (y compris « Noir » OLED), qui s'agrandissent à volonté :
  - *Semaine complète* — petit (2×2, 3×2) : un jour par ligne avec nombre de cours et première/dernière heure ;
    moyen (4×2, 3×3) : cinq colonnes de pastilles colorées avec l'initiale de la matière ; grand (4×3, 4×4) :
    grille jours × heures, cours colorés avec leur nom, jour courant et cours en cours en évidence, annulés barrés ;
    très grand (5×4 et plus) : nom complet, salle, horaire et professeur.
  - *Journée complète* — petit : le cours en cours (ou le prochain « dans 25 min ») en grand ; moyen : la liste de
    ce qu'il reste, pauses nommées, barre d'avancement de la journée ; grand : frise horaire de toute la journée avec
    salles et professeurs. Une fois la journée finie, il montre le prochain jour de cours.
  - *Prochain cours*, *Devoirs*, *Notes*, *Messages* (2×2 à 4×4). Un toucher ouvre l'onglet correspondant ; le widget
    Prochain cours a en plus un bouton « Voir l'agenda du jour ».
  - Les widgets ne font aucune requête : ils lisent le dernier instantané publié par la synchronisation ou l'ouverture
    de l'app (mise à jour toutes les 30 min au plus). Changer de thème ou activer le mode « montrer » les redessine aussitôt.
- **Hors ligne et fiabilité** : la dernière réponse valide s'affiche sous un bandeau d'ancienneté
  (« Hors ligne · Données du 5 nov. à 18 h (il y a 3 h) ») avec la vraie raison du repli (pas de réseau, compte
  verrouillé, version d'API refusée…) et l'action utile ; tirer vers le bas rafraîchit chaque écran, sans requête
  si la dernière lecture a moins de 90 s ; les erreurs disent le cas (mot de passe refusé, compte verrouillé,
  ÉcoleDirecte indisponible, vérification de sécurité requise…).
- **Apparence et confort** : clair, sombre ou noir pur (OLED), cinq thèmes de couleurs (Indigo, Océan, Forêt,
  Coucher de soleil, Ardoise) aux contrastes vérifiés, taille du texte réglable, dates toujours en français,
  verrouillage biométrique avec délai (immédiat, 1, 5 ou 15 minutes), mode « montrer à quelqu'un » qui masque
  notes (et messages en option) par •••, présentation de trois pages au premier lancement.

## Tes données

- L'identifiant et le mot de passe ne servent **qu'à te connecter à ÉcoleDirecte**. Ils sont
  chiffrés sur le téléphone (clé du Keystore Android, jamais liée à la biométrie) et ne partent
  nulle part ailleurs : aucun serveur intermédiaire, aucune télémétrie.
- Le cache des réponses est chiffré avec la même clé ; les pièces jointes ouvertes sont
  effacées à la fermeture.
- Le diagnostic (Plus > Diagnostic) décrit la forme des réponses, **jamais une valeur** : on peut
  le copier dans un ticket sans exposer un nom, une note ou un message. Le journal des synchronisations
  (30 derniers passages : heure, durée, résultat, nombre de nouveautés) n'en contient pas non plus.
- Une option masque l'aperçu de l'application dans la liste des applications récentes.

## Garde-fous envers le service

L'application ne contourne aucune protection et ne devine aucune réponse de sécurité.
Elle lit, avec une seule empreinte réseau, au plus deux requêtes à la fois ; elle rejoue la
session gardée plutôt que de se reconnecter, ne retente jamais une connexion refusée,
s'arrête après une vérification à faire à la main ou trois échecs de suite, et n'interroge
pas un module que l'établissement n'ouvre pas. La synchronisation en arrière-plan ne descend
pas sous quatre heures et part avec un décalage aléatoire.

Les écritures vers ÉcoleDirecte (marquer un devoir fait, envoyer un message) n'existent que
derrière des réglages désactivés par défaut, avec une confirmation.

## Limites connues

- L'API est privée : un changement côté ÉcoleDirecte peut casser un écran du jour au lendemain.
  Le code 517 (version refusée) est reconnu et la version envoyée est modifiable.
- Les formes de réponse viennent de la documentation de la communauté et de captures
  documentées, pas toutes vérifiées sur chaque établissement (modules, appréciations,
  extrêmes de classe, pièces jointes).
- Les comptes professeur ne sont pas pris en charge.
- ÉcoleDirecte n'indique pas les semaines 1 / 2 (A / B) : c'est un réglage à faire soi-même, et seules les deux
  semaines enregistrées (en cours, suivante) servent à l'export. Voir [Exports](docs/EXPORTS.md).
- Les pièces jointes sont limitées à 25 Mo ; les programmes, scripts et pages web ne sont jamais ouverts.
- La durée de vie du jeton n'est pas documentée : le téléphone la mesure et adapte le rejeu.
- Les widgets, les notifications de fond et le verrouillage biométrique ne sont vérifiables que
  sur un vrai téléphone.
- Le mode « montrer à quelqu'un » masque les notes des widgets mais pas (encore) les notifications ; la taille du
  texte « très grand » peut faire déborder certains écrans.

## Développer

Ouvrir le dépôt dans Android Studio (SDK 35, Java 17), ou en ligne de commande :

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

La version de test s'installe **à côté** de la version publiée (`fr.ecole2recte.app.dev`,
libellé « ecole2recte (dev) »). Aucun test ne contacte ÉcoleDirecte : tout passe par un faux
serveur local.

Le workflow `.github/workflows/android.yml` lance les tests, le lint et la construction à
chaque push, et garde les rapports en artefact. Un APK signé n'est produit que si les secrets
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` et
`ANDROID_KEY_PASSWORD` existent.

Plans de travail : [`PLAN-REFONTE.md`](PLAN-REFONTE.md), [`PLAN-PATCH.md`](PLAN-PATCH.md),
[`RAPPORT-CRITIQUE.md`](RAPPORT-CRITIQUE.md).

## Maquette web

`index.html`, `styles.css` et `app.js` forment la maquette statique de l'accueil. **Ce n'est
qu'une maquette** : les données sont écrites en dur et rien n'est envoyé nulle part. Elle reste
à la racine parce que GitHub Pages sert ces fichiers depuis la racine du dépôt. Ouvrir
`index.html` dans un navigateur, ou `python -m http.server 8080`.
