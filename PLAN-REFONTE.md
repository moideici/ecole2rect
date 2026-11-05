# Plan de refonte — ecole2recte (Android)

Plan en 15 lots pour rendre l'application plus visuelle, plus pratique et plus fiable.
Il part de l'état réel du code (3 fichiers Kotlin, ~1 170 lignes) et non de la maquette.

## Constat de départ

| Constat | Où | Conséquence |
|---|---|---|
| Toute l'UI tient dans un seul fichier de 647 lignes, sans ViewModel ni navigation | `MainActivity.kt` | Impossible d'ajouter des écrans de détail ou un changement de compte proprement |
| Les écrans (accueil, planning, devoirs, notes, messages) affichent des données en dur (« Bonjour Martin ») | `MainActivity.kt` | Rien n'est réel hors connexion et `getLessons` |
| Seul `getLessons` existe côté réseau : pas de notes, devoirs ni messages | `EcoleDirecteClient.kt` | Il faut écrire les appels et parseurs avant de refaire les écrans |
| La liste `accounts` renvoyée au login est ignorée (`LoginResult.Success(accounts)`) | `MainActivity.kt` | Aucun support des comptes famille / plusieurs élèves |
| Un seul identifiant stocké (clé `login`) | `SecureCredentialStore.kt` | Pas de multi-compte possible |
| L'écran de connexion demande identifiant, mot de passe **et** date de naissance d'un coup | `LoginScreen` | Trois saisies là où une seule suffit en général |
| **Bug** : après un login réussi avec la réponse de confiance déjà enregistrée, `store.save(... client.approvedAnswer ?: trustedAnswer)` écrase `cn/cv` par `null` (`approvedAnswer` n'est rempli que lors d'une nouvelle validation) | `MainActivity.kt:217` | La question de sécurité revient à la connexion suivante |
| Le jeton de session n'est pas conservé : chaque lancement refait GTK + login derrière un écran de chargement | `LaunchedEffect` d'`Ecole2recteApp` | Démarrage lent, deux requêtes inutiles, risque de blocage du compte |
| Boutons sans effet : « Voir le planning », « Tout voir », « Modifier » (couleurs) ; couleurs jamais enregistrées | `MainActivity.kt` | La personnalisation annoncée n'existe pas |
| Textes en 10–11 sp, gris `#7F8998` sur blanc, pas de mode sombre | thème | Lisibilité et accessibilité faibles |

Contrainte du README à garder : ne jamais multiplier les tentatives de connexion ni deviner une
réponse de sécurité. Chaque lot qui touche à l'authentification respecte cette règle.

---

## Phase A — Fondations (lots 1 à 3, 7, 8)

### Lot 1 — Architecture et navigation
- Découper en paquets : `data/` (réseau, cache), `domain/` (modèles stables), `ui/` (écrans, thème, composants), `session/` (comptes).
- Un `ViewModel` par écran, état exposé en `StateFlow`, un `UiState` (chargement / contenu / erreur / vide).
- Navigation Compose avec routes typées et écrans de détail (cours, devoir, note, message).
- Injection légère à la main (un `AppContainer`), pas de framework tant que ce n'est pas nécessaire.
- Fait quand : `MainActivity.kt` ne contient plus que le point d'entrée ; l'app se comporte comme avant.

### Lot 2 — Connexion parfaite : une seule saisie
Objectif : l'élève saisit son identifiant et son mot de passe **une seule fois, jamais plus**.
- Écran 1 : identifiant + mot de passe uniquement (afficher/masquer, saisie automatique Android, touche Entrée qui valide).
- Date de naissance **retirée** de l'écran de départ. Elle n'est demandée que si ÉcoleDirecte pose une question de sécurité, dans une étape dédiée, avec réponse sélectionnée automatiquement quand elle correspond aux propositions.
- Corriger l'écrasement de `cn/cv` (voir constat) et mettre à jour la réponse de confiance si le serveur la renouvelle.
- Conserver le jeton de session avec son horodatage : ouverture immédiate sur le contenu en cache, reconnexion silencieuse en arrière-plan seulement si le code `520` (jeton invalide) revient.
- États d'erreur clairs et distincts : réseau, identifiants invalides (`505`, **sans nouvelle tentative automatique**), compte bloqué (`516/535`), question de sécurité, service indisponible.
- Garde-fou anti-blocage : au plus une reconnexion automatique par session, délai croissant avant de réessayer, message explicite si ça échoue.
- Verrouillage optionnel de l'application par empreinte / code de l'appareil (BiometricPrompt) pour protéger le mot de passe chiffré.
- Le panneau « diagnostic réseau » passe dans les réglages (mode développeur), plus sur l'écran de connexion.
- Tests : réponses enregistrées pour chaque code (`200`, `250`, `505`, `516`, `520`, `535`), sans compte réel.

### Lot 3 — Multi-compte
- `SecureCredentialStore` devient un `AccountStore` : une liste de profils chiffrés (identifiant, mot de passe, réponse de confiance, jeton) + le profil actif.
- Deux niveaux à distinguer :
  - **plusieurs connexions** (ex. deux frères avec chacun leur identifiant) ;
  - **plusieurs élèves sous un compte famille** : exploiter la liste `accounts` du login, aujourd'hui ignorée. À valider sur une vraie réponse d'un compte parent avant de figer le modèle.
- Sélecteur de compte : pastille avec initiales/avatar en en-tête, feuille du bas listant les profils, « Ajouter un compte », bascule instantanée.
- Isolation stricte par compte : cache, couleurs de matières, réglages d'accueil, notifications.
- Déconnexion d'un compte ou de tous, avec effacement du cache correspondant.
- Ajout d'un compte = même parcours que le lot 2 (une seule saisie).

### Lot 7 — Vraies données (réseau et parseurs)
- Ajouter à `EcoleDirecteClient` : notes, devoirs (cahier de texte), messages, détails ; respecter le `code` JSON du corps, pas le statut HTTP.
- Modèles du domaine stables (`Lesson`, `Homework`, `Grade`, `Message`), indépendants du format du serveur.
- Parseurs testés sur des réponses enregistrées et anonymisées.
- Remplacer progressivement chaque écran de démonstration, sans casser l'app entre deux lots.

### Lot 8 — Cache local et mode hors ligne
- Base Room par compte ; lecture d'abord depuis le cache, rafraîchissement ensuite.
- Date de dernière synchronisation affichée, bandeau « hors ligne » discret, donnée conservée si le réseau tombe.
- Synchronisation périodique avec WorkManager, idempotente (identifiants stables, pas de doublons).
- Tirer pour rafraîchir sur chaque écran.

---

## Phase B — Onglets, visuel, animations (lots 4 à 6)

### Lot 4 — Refonte des onglets
Proposition (à valider) : passer de 5 onglets à 5 onglets mieux répartis, en regroupant ce qui va ensemble.

| Onglet | Contenu |
|---|---|
| Accueil | Maintenant / ensuite, urgent, nouveautés |
| Agenda | Sous-onglets **Emploi du temps** · **Travail à faire** (devoirs et contrôles) |
| Notes | Matières, évolution, détail, simulateur |
| Messages | Boîte de réception, non lus |
| Plus | Vie scolaire, documents, réglages, comptes |

- Pastilles de notification (non lus, nouvelle note, devoir urgent).
- Rappuyer sur l'onglet actif remonte en haut de la liste ; l'état de défilement est conservé par onglet.
- Glissement horizontal entre sous-onglets.
- Alternative si tu préfères garder la barre actuelle (Accueil, Planning, Devoirs, Notes, Messages) : on ajoute seulement les pastilles, le menu compte et un accès « Plus » depuis l'en-tête.

### Lot 5 — Refonte visuelle légère
Même identité (indigo, cartes arrondies) : on corrige sans tout changer.
- Jetons de design (couleurs, espacements, rayons, ombres) et `MaterialTheme` complet à la place des `Color(...)` éparpillés.
- Échelle typographique : plus de texte sous 12 sp ; contrastes revus (gris secondaire plus foncé) ; cible tactile de 48 dp minimum.
- Mode sombre, et suivi du thème système avec option manuelle.
- Composants communs : `AppCard`, `SectionHeader`, `SubjectDot`, `EmptyState`, `ErrorState`, remplaçant les cartes recopiées dans chaque écran.
- Icônes cohérentes, états vides illustrés, en-tête qui se réduit au défilement.

### Lot 6 — Animations
- Transitions entre onglets (fondu + léger glissement) et entre liste et détail (transformation de conteneur).
- Entrée en cascade des cartes, compteurs animés (moyenne, jours restants), anneau de progression vivant pour le prochain cours.
- Squelettes de chargement (shimmer) à la place des écrans vides.
- Retour tactile : appui, validation d'un devoir (coche animée), bascule de compte.
- Transition connexion → accueil.
- Respect du réglage système « supprimer les animations ».

---

## Phase C — Écrans (lots 9 à 14)

### Lot 9 — Accueil
- « En ce moment » / « Ensuite » calculé à l'heure, compte à rebours réel.
- Bloc urgent : devoir du lendemain, contrôle proche.
- Nouveautés depuis la dernière visite : nouvelle note, message non lu, cours modifié ou annulé.
- Salutation avec le vrai prénom du compte actif.

### Lot 10 — Emploi du temps
- Vue jour (glisser pour changer de jour) et vue semaine en grille.
- Couleur par matière, repère « maintenant », cours annulés / remplacés bien visibles.
- Détail d'un cours : salle, professeur, horaires, devoirs liés.

### Lot 11 — Devoirs
- Regroupés par échéance (aujourd'hui, demain, cette semaine), filtre par matière.
- Marquer comme fait (local d'abord ; l'écriture vers ÉcoleDirecte n'est ajoutée qu'après vérification sur un vrai compte).
- Détail avec contenu complet et pièces jointes.

### Lot 12 — Notes
- Moyenne générale et par matière, courbe d'évolution, nouvelle note mise en avant.
- Détail d'une note : coefficient, moyenne de classe, minimum et maximum quand fournis.
- Simulateur : « quelle note pour atteindre X de moyenne ? ».

### Lot 13 — Messagerie
- Boîte de réception paginée, non lus, recherche.
- Lecture d'un message avec pièces jointes.
- Réponse et envoi : ajoutés en dernier, avec confirmation explicite avant envoi.

### Lot 14 — Personnalisation
- Couleurs de matières réellement modifiables et **enregistrées par compte** (le bouton « Modifier » est aujourd'hui sans effet).
- Accueil en blocs réorganisables et masquables.
- Réglages : thème, animations, verrouillage, comptes, diagnostic.

---

## Phase D — Prolongement (lot 15)

### Lot 15 — Notifications et widgets
- Notifications utiles et regroupées : nouvelle note, devoir pour demain, cours annulé, message reçu ; canaux réglables, aucun spam.
- Widgets Android (Glance) : prochain cours, devoirs du jour.
- Raccourcis d'application (long appui sur l'icône).

---

## Ordre conseillé et dépendances

```
1 Fondations ─┬─ 2 Connexion ── 3 Multi-compte
              ├─ 5 Visuel ── 6 Animations ── 4 Onglets
              └─ 7 Données ── 8 Cache ── 9 à 13 Écrans ── 14 Personnalisation ── 15 Notifs/widgets
```

Premiers livrables, chacun testable seul : **1 → 2 → 3** (la connexion et les comptes sont la base de tout), puis **5 → 4 → 6** (le visuel sans toucher aux données), puis **7 → 8** puis les écrans.

## Règles pour chaque lot
- Tests unitaires sur les parseurs et sur la logique d'authentification, sans compte réel.
- Aucun identifiant, mot de passe, cookie ni jeton dans les logs, commits ou diagnostics.
- Accessibilité : descriptions de contenu, contrastes, tailles de police système.
- Le build debug de la CI doit rester vert à chaque lot.

## Points à valider sur une vraie réponse avant de coder
1. Forme exacte de `accounts` pour un compte famille (élèves multiples).
2. Durée de vie réelle du jeton (déclenchement du `520`) et renouvellement de `cn/cv`.
3. Format des notes, du cahier de texte et de la messagerie (le web `ecole2recte` les décrit déjà : le protocole y est documenté et peut servir de référence).
4. `android:allowBackup` dans le manifeste : s'assurer que les données chiffrées ne sont pas sauvegardées dans le cloud.
