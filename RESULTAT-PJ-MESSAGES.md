# Résultat — VOIE 2, lots 7 à 9

Travail hors service ÉcoleDirecte, sans identifiant réel et sans envoi réel. Changements laissés non commités. Le plan a été lu en entier ; lots traités dans l'ordre 7, 8, puis clôture 9.

## Fait : lot 7, pièces jointes

- Contrôle partagé extrait dans le domaine, appelé avant téléchargement par le dépôt, puis sur le nom et le MIME reçus, et avant ouverture/enregistrement. Une extension interdite reste interdite après nettoyage du nom (espaces, points finaux, contrôles, caractères supprimés). Les MIME actifs et les incompatibilités entre MIME précis et extension connue sont refusés.
- Noms sans chemin, longueur plafonnée, sous-dossiers privés distincts ; plafond existant de 25 × 1024 × 1024 octets maintenu et non relevable par le paramètre de téléchargement. Vérification sur la longueur annoncée et sur les octets réellement lus, y compris sans Content-Length.
- Documents texte acceptés ; réponses HTML/JSON d'erreur refusées. Messages utiles pour HTTP 401, 403, 404, 5xx et dépassement de taille.
- Action « Enregistrer dans Téléchargements » dans la confirmation, à côté de l'action d'ouverture : Storage Access Framework, emplacement initial Téléchargements, aucune permission de stockage. Annuler le sélecteur ne déclenche aucun téléchargement ; la copie choisie reste à sa destination.
- Taille et type affichés dans la liste et la confirmation ; taille absente explicitement « Taille inconnue ». Les champs optionnels `taille` (octets) et `mimeType` sont propagés depuis les lecteurs.
- Devoirs (`FICHIER_CDT`), messages (`PIECE_JOINTE`) et documents avec type annoncé passent par les mêmes contrôles. Le type métier annoncé reste distinct du MIME.
- Audit des mécanismes existants conservés : confirmation explicite avant ouverture, FileProvider privé limité au cache des pièces jointes, effacement au démarrage et dans `MainActivity.onDestroy` lorsque `isFinishing`.

## Fait : lot 8, messages

- Les trois verrous de `EcoleDirecteRepository.sendMessage` restent inchangés : confirmation, autorisation puis validité. Réglage `send_messages` toujours désactivé par défaut ; `NoWrites` reste la valeur par défaut du dépôt.
- Correction du chemin d'envoi : une expiration remontait auparavant au mécanisme commun de reconnexion/rejeu des lectures. L'API d'envoi transforme désormais cette expiration en erreur métier ; aucune reconnexion et aucun rejeu automatique du message.
- HTTP : streaming à longueur fixe uniquement pour les envois de messages, pour empêcher le rejeu implicite du POST tamponné après une coupure ; suivi automatique des redirections désactivé pour ces seuls envois. Les garde-fous d'autorisation, `ApiSession` et les intervalles de synchronisation ne sont pas modifiés.
- Confirmation existante conservée : tous les destinataires, objet et texte complet. Brouillon conservé dans la feuille après échec ; le texte erroné « Rien n'est parti » est remplacé par une invitation à vérifier les messages envoyés avant un nouvel essai manuel.
- Bouton « Répondre » lorsque l'envoi est autorisé et que l'expéditeur fournit un identifiant et un type/rôle ; destinataire et objet préremplis. Aucun rapprochement d'identité par le seul nom.
- Recherche locale de destinataires extraite et testée sur fixture, tolérante aux accents, à la casse et aux espaces multiples. Suppression d'une assertion préexistante qui comparait une constante à elle-même.

## Fichiers modifiés, un par un

Chemins Kotlin relatifs à `app/src/main/java/fr/ecole2recte/app/` :

| Fichier | Changement |
| --- | --- |
| `domain/AttachmentSafety.kt` (nouveau) | Contrôle commun des noms, extensions, MIME et présentation de la taille. |
| `domain/Models.kt` | Métadonnées optionnelles de pièce jointe, source Document et destinataire de réponse optionnel. |
| `data/api/Parsers.kt` | Lecture des métadonnées et de l'expéditeur identifié pour répondre. |
| `data/EcoleDirecteRepository.kt` | Propagation des métadonnées/réponse, contrôles des pièces avant et après téléchargement. Aucun changement des verrous d'envoi. |
| `data/EcoleDirecteClient.kt` | Limite de taille, textes d'erreur, fichiers texte, interdiction des rejeux HTTP implicites pour les envois. |
| `data/api/EcoleDirecteApi.kt` | Expiration de session d'envoi rendue non rejouable. |
| `data/api/Recipients.kt` | Recherche locale normalisée. |
| `domain/MessageDraft.kt` | Préparation d'une réponse et texte d'échec honnête. |
| `ui/common/Attachments.kt` | Réutilisation du contrôle du domaine, métadonnées affichées, enregistrement SAF. |
| `ui/messages/ComposeSheet.kt` | Brouillon initial de réponse, recherche partagée et texte d'échec. |
| `ui/messages/MessagesView.kt` | Action Répondre et ouverture du brouillon prérempli. |

Autres fichiers :

| Fichier | Changement |
| --- | --- |
| `app/src/test/java/fr/ecole2recte/app/data/AttachmentsTest.kt` | 9 nouveaux tests et extension du test des sources au Document. |
| `app/src/test/java/fr/ecole2recte/app/data/MessageSendingTest.kt` | 7 nouveaux tests ; refus des brouillons trop longs vérifié avant toute requête ; assertion existante corrigée. |
| `app/src/test/resources/captures/attachments_metadata.json` (nouveau) | Métadonnées fictives présentes, absentes et taille négative. |
| `app/src/test/resources/captures/recipients.json` (nouveau) | Contacts fictifs, accents, doublon et entrées incomplètes. |
| `app/src/test/resources/captures/message_reply.json` (nouveau) | Message fictif avec expéditeur identifié et rôle. |
| `app/src/test/resources/captures/message_send_request.form` (nouveau) | Instantané figé des octets form-urlencoded de l'envoi fictif, clés/formes et base64 inclus. |
| `RESULTAT-PJ-MESSAGES.md` (nouveau) | Présent rapport. |

## Tests ajoutés

16 nouvelles méthodes `@Test`, écrites avant leurs correctifs correspondants :

- Pièces jointes (9) : extensions après nettoyage ; confinement et noms vides/longs ; MIME dangereux/incompatible ; présentation française ; lecture de fixture ; texte téléchargeable ; erreurs 401/404 ; dépassement réel de 25 Mo sans longueur annoncée ; refus avant requête et contrôle des réponses sur chaque source.
- Messages (7) : instantané brut des octets ; expiration sans reconnexion/rejeu ; coupure après réception sans deuxième envoi ; redirection sans deuxième adresse ; recherche sur fixture ; réponse identifiée ; texte d'échec sans promesse de non-réception.
- Tests existants renforcés : destinataire/objet/corps absents ou trop longs → zéro requête ; source Document ; assertion HTML vide. Tests existants d'autorisation désactivée, absence de confirmation, conservation des octets, cache privé et nettoyage maintenus.
- Tous les tests réseau visent uniquement `MockWebServer` local. Aucun appel vers ÉcoleDirecte n'a été exécuté.

## Vérifications réelles et lot 9

Référence communiquée par le hub : **530 tests, 0 échec**. Ce nombre n'a pas été revérifié dans ce bac à sable.

Commandes effectivement tentées depuis la racine du worktree :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest lintDebug assembleDebug
```

Chaque invocation sort avec le **code 1 avant Gradle**. Sortie représentative réelle de la dernière tentative :

```text
0 [main] bash (26696) C:\Program Files\Git\usr\bin\bash.exe: *** fatal error - CreateFileMapping S-1-5-21-2892306765-1606266371-1694119132-1001.1, Win32 error 5. Terminating.
```

Par conséquent **compilation, tests unitaires, lint et APK non validés** ; aucune phase rouge/verte observée. Le hub doit exécuter ces tâches hors de cette restriction. L'instantané brut doit notamment être confirmé par l'exécution JUnit avec la version de `org.json` du projet.

Vérifications disponibles réussies :

- `git diff --check` : code 0, aucune sortie.
- Script Python local : syntaxe des trois fixtures JSON, décodage de l'instantané form-urlencoded, clés exactes, destinataires, fichiers vides et contenu base64 vérifiés ; sortie « Fixtures JSON et instantane form : syntaxe, schema et contenu fictif verifies. »
- Comptage du diff : 16 nouvelles annotations `@Test`.

## Pas fait / limites

- Aucun envoi réel, aucune consultation du service, aucun essai sur téléphone, aucun commit/push.
- Les métadonnées `taille` en octets et `mimeType`, les contacts, le type Document et la forme d'envoi restent des hypothèses de fixtures, pas des formes nouvellement confirmées sur un établissement réel. Les métadonnées absentes ne sont pas inventées.
- La source Document est couverte au niveau API/dépôt avec un type explicitement annoncé ; aucun nouvel écran de documents ni nouvel endpoint n'est ajouté.
- Les MIME précis incompatibles sont refusés par prudence ; certains serveurs annonçant un type atypique peuvent nécessiter une adaptation documentée. Pas d'analyse antivirus ou de détection du contenu binaire.
- Le brouillon reste conservé après échec tant que la feuille reste ouverte ; pas de nouvelle persistance après fermeture/processus tué. Le sélecteur SAF et la conservation du brouillon n'ont pas été testés sur Android.
- Un processus tué sans `onDestroy` peut laisser le cache privé jusqu'au prochain démarrage, où le nettoyage existant s'exécute. Le cache n'est pas effacé lors du simple passage vers une application de lecture, pour qu'elle puisse ouvrir le fichier.

## À vérifier sur le téléphone par le propriétaire

1. Pièces de devoir et de message : métadonnées, confirmation, ouverture PDF/image/texte, messages 401/fichier absent/trop gros, refus des extensions et MIME actifs.
2. Enregistrer : sélecteur initial dans Téléchargements, annulation sans transfert, copie lisible conservée après fermeture ; refus/absence d'espace ; retour du sélecteur après rotation ou activité recréée.
3. Nettoyage : fermeture normale puis relancement, et relancement après arrêt forcé ; aucune suppression des copies enregistrées par SAF.
4. Répondre : expéditeur/objet préremplis, recherche avec et sans accents, confirmation complète, protection lorsque l'autorisation est éteinte.
5. Brouillon après échec et absence de renvoi automatique. Toute vérification réelle d'envoi reste réservée au propriétaire sur un compte de test ; aucun envoi de ce type n'a été fait pendant cette mission.
