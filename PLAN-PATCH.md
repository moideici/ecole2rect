# Plan de patch — retours du premier test sur téléphone

Mis à jour après l'application des correctifs et la relecture critique (`RAPPORT-CRITIQUE.md`).
**125 tests passent, `assembleDebug` OK.** Rien de ce qui suit n'a encore été vu sur le téléphone
(il a été débranché avant l'installation) : « fait » veut dire écrit, compilé et testé, pas essayé.

## 1. Tes retours

| # | Retour | État | Ce qui a été fait | À vérifier sur le téléphone |
|---|---|---|---|---|
| 1 | Emploi du temps sans heures, pauses ni doubles heures | **fait** | Vue jour en **frise horaire** : axe des heures, chaque cours à sa vraie hauteur, **doubles heures** fondues en un bloc (« Double heure · 1 h 50 »), **pauses nommées** (récréation, pause, pause déjeuner, heure libre) avec leur durée, cours simultanés côte à côte, trait « maintenant », cours passés atténués | la frise avec ton vrai emploi du temps |
| 2 | Notes : « par matière » vide, moyenne vide | **fait, cause identifiée** | La moyenne générale est lue dans `ensembleMatieres` ; la **période** est choisie par les dates (jamais « la dernière de la liste », jamais la période « Année ») ; sinon moyenne calculée à partir des matières, puis des notes. La relecture critique a trouvé la même cause de son côté (C01) | les notes avec ton vrai compte |
| 3 | Notifications personnalisables | **fait** | Par famille : emploi du temps (annulé, salle, professeur, horaire, rétabli), nouvelles notes, messages, devoirs pour demain ; **mode nuit** 22 h–7 h ; fréquence 2 / 4 / 8 h ; chaque changement n'est notifié qu'une fois | l'activation et la permission |
| 4 | Gestion des icônes | **fait** | **Icône d'application** (lapin, adaptative + version monochrome Android 13), **icône par matière** (maths, langues, histoire-géo, sciences, SVT, sport, arts…) dans la frise, les devoirs, les notes et la carte du prochain cours | le rendu de l'icône sur ton lanceur |
| 5 | Widgets qui ne s'affichent pas | **fait, cause probable corrigée** | Le widget utilisait le contexte de Compose au lieu de celui de Glance : il plantait au dessin. Corrigé ; coins arrondis, aperçu dans le sélecteur ; les widgets se remplissent **à chaque ouverture de l'app** (sans activer les notifications) | ajouter les deux widgets |
| 6 | Mode parent plus complet | **fait en partie** | **« Ma famille »** à l'accueil : une ligne par enfant (prochain cours, devoirs pour demain, nouvelles notes, moyenne), un toucher change d'enfant ; **Vie scolaire** (absences, retards, sanctions, encouragements, durée comptée par l'établissement) dans Plus | un compte parent réel |
| 7 | Traitement des liens | **fait** | Liens web, `www.`, courriels et numéros de téléphone cliquables dans les devoirs, le contenu de cours et les messages | un devoir ou un message avec un lien |
| 8 | Accueil : lien entre prochain cours et journée | **fait** | Le bloc « Aujourd'hui » est la **même frise que l'agenda**, resserrée ; le prochain cours ouvre l'agenda | l'accueil un jour de cours |
| 9 | Accueil : « À savoir » | **fait** | Ordre : cours annulé > cours modifié > évaluation > message > note. **Note : matière en haut à gauche, note avec coefficient en grand à droite**, détail en petit (l'icône dit déjà « note ») | — |

Pas fait, volontairement : téléchargement des pièces jointes (aucune ouverture possible pour l'instant),
envoi de messages, écriture « devoir fait » vers ÉcoleDirecte (voir §3).

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

## 4. Suite
1. Rebrancher le téléphone : j'installe la copie de test et je regarde le journal.
2. Tu essaies : emploi du temps, notes, widgets, compte parent.
3. Tu me dis oui / non / plus tard pour A à L ; un sous-agent applique seulement les « oui ».
