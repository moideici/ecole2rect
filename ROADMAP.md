# Feuille de route

Ce fichier est la liste de travail du dépôt : ce qui manque, dans l'ordre où ça vaut la
peine d'être fait, et pourquoi. Une case cochée veut dire « implémenté et couvert par ce
qu'on peut tester sans compte réel », pas « parfait ».

L'ordre suit une règle simple : d'abord ce sans quoi l'application ment ou reste muette,
ensuite les modules absents, puis l'approfondissement, enfin la qualité et la distribution.

## P0 — sans quoi l'usage quotidien reste bancal

- [x] **Synchronisation en arrière-plan.** Les données ne bougeaient qu'à l'ouverture de
      l'application : les quinze widgets affichaient donc un cache figé. Un travail
      périodique `WorkManager` synchronise et repousse les widgets.
- [x] **Notifications.** Nouveau devoir, nouvelle note, message non lu, rappel la veille
      d'une évaluation. Avec canaux séparés, réglages par type, et rien qui se répète.
- [x] **Téléchargement des pièces jointes.** Elles étaient listées sans pouvoir être
      ouvertes. Endpoint `telechargement.awp`, écriture dans le cache privé, ouverture par
      une autre application via `FileProvider`.
- [x] **Lecture des messages alignée sur le serveur.** L'ouverture d'un message passe par
      l'appel de détail du client web — celui qui marque le message lu côté ÉcoleDirecte — et
      l'état du serveur reprend la main dès la synchronisation suivante. L'état local n'est
      plus qu'un miroir immédiat. Ce dernier point n'a pas pu être vérifié sans compte réel.
- [x] **Pagination de la messagerie.** Seuls les cent derniers messages reçus étaient
      chargés, sans moyen d'aller plus loin.

## P1 — modules ÉcoleDirecte absents

- [x] **Vie scolaire** : absences, retards, sanctions et encouragements.
- [x] **Contenu de séance** : ce qui a été fait en cours, indispensable pour rattraper.
- [x] **Documents et manuels** de l'espace numérique.
- [x] **Actualités de l'établissement** (`viedeletablissement.awp`), **manuels numériques**
      (`manuelsnumeriques.awp`) et **espace de stockage** (`cloud.awp`). Trois modules qu'un
      établissement peut ne pas ouvrir : ils se chargent à l'ouverture de leur écran, et un
      refus affiche ce qu'il en est plutôt qu'une erreur répétée à chaque synchronisation.
- [x] **Messagerie : les trois dossiers** (reçus, envoyés, archivés), destinataires des
      messages envoyés et classeurs créés sur le site.
- [ ] **Dépôt de copie en ligne** (`rendreEnLigne`). Volontairement laissé de côté : rendre
      un devoir est irréversible, et l'envoi de fichier ne peut pas être vérifié sans compte
      réel. À faire avec un compte de test, pas à l'aveugle.
- [ ] **Écriture d'un message.** Même raison : envoyer un message à un professeur depuis un
      protocole non documenté et non testé peut lui livrer un contenu tronqué. La lecture
      reste donc complète, l'écriture attend un compte de test.
- [x] **Comptes famille et fratrie** : un cache par élève au lieu d'un cache global effacé à
      chaque changement de profil.
- [x] **Connexion parent.** Une connexion parent ne renvoie pas un élève mais une famille et
      ses enfants : l'application donnait l'identifiant de la famille aux appels élève, qui
      répondaient tous « accès refusé ». Chaque enfant est désormais un profil complet, la
      messagerie reste celle de la famille, l'interface parle de l'enfant au lieu de tutoyer
      le parent, et une coche de devoir ne part pas au serveur depuis une vue parent.

- [x] **Réponse automatique à la question de sécurité.** Une session expirée en pleine
      synchronisation de fond ramenait à l'écran de connexion. L'application répond
      maintenant seule quand elle le peut : date de naissance saisie, ou réponse déjà
      validée — jamais devinée, jamais approchée, une seule tentative par connexion, et
      valable jusqu'au 1er septembre suivant pour tout ce qui dépend de la classe.

## P2 — approfondir ce qui existe

- [x] **Simulateur de moyenne** : « si j'ai 14 au prochain devoir, ma moyenne devient… ».
- [x] **Historique des notes** : l'évolution de la moyenne sur la période.
- [x] **Recherche globale** depuis l'accueil : matières, devoirs, notes, messages.
- [x] **Export ICS** de l'emploi du temps vers le calendrier du téléphone.
- [x] **Journal d'erreurs** consultable dans les réglages, sans secrets.
- [x] **Widgets configurables** : choisir la matière ou la période affichée à la pose.
- [x] **Balayage horizontal** entre les jours de l'emploi du temps.
- [x] **Tout ce que porte le bulletin** : compétences du socle évaluées avec la note, notes
      extrêmes de la classe, type de devoir, appréciation du conseil avec sa date et le
      professeur principal, appréciations multiples par matière, moyennes minimale et
      maximale, groupe et type d'un cours, durée d'une absence, devoir à rendre en ligne.

## P3 — qualité, robustesse, distribution

- [x] **Tests d'interface** sur les parcours principaux.
- [x] **Lint en intégration continue** (Android Lint, échec sur erreur).
- [x] **Réessai avec temporisation** sur les pannes réseau passagères.
- [x] **Build release signé** et publication de l'APK en artefact.
- [x] **Verrouillage biométrique** de l'application.
- [ ] **Extraction de `core` en module multiplateforme.** Le code est déjà sans dépendance
      Android ; le passage en module KMP demande une chaîne de build iOS qu'on ne peut pas
      valider ici. La frontière est prête, la migration ne l'est pas.

## P4 — finitions

- [x] **Couleurs dynamiques Material You** et icône monochrome.
- [x] **Raccourcis d'application** (appui long sur l'icône).
- [x] **Tirer pour rafraîchir** sur les écrans de liste.
- [x] **Audit d'accessibilité** : libellés, tailles de police, contrastes.

## Ce qui restera à faire avec un compte réel

Certaines choses ne peuvent pas être écrites honnêtement sans pouvoir les essayer :

- le dépôt de copie et l'envoi de messages (voir P1) ;
- la vérification des variantes de réponses de l'API selon les établissements — le parsing
  est défensif et testé sur des réponses enregistrées, mais chaque établissement configure
  ÉcoleDirecte à sa façon ;
- la double authentification sur des comptes qui posent d'autres questions que la date de
  naissance : la réponse est alors demandée une fois puis mémorisée jusqu'à la rentrée, ce
  qui couvre la question de la classe, mais une question changeant en cours d'année serait
  reposée à chaque expiration de jeton ;
- la forme des réponses des trois modules facultatifs — actualités, manuels numériques et
  espace de stockage : les clés lues sont celles décrites par la communauté, avec plusieurs
  variantes acceptées, mais aucune n'a pu être confrontée à un vrai compte ;
- la forme exacte de `profile.eleves` sur un compte parent : elle est lue défensivement, la
  classe étant acceptée aussi bien comme objet que comme simple libellé.
