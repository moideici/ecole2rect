# Feuille de route

Ce fichier est la liste de travail du dépôt : ce qui manque, dans l'ordre où ça vaut la
peine d'être fait, et pourquoi. Une case cochée veut dire « implémenté et couvert par ce
qu'on peut tester sans compte réel », pas « parfait ».

L'ordre suit une règle simple : d'abord ce sans quoi l'application ment ou reste muette,
ensuite les modules absents, puis l'approfondissement, enfin la qualité et la distribution.

## P0 — sans quoi l'usage quotidien reste bancal

- [x] **Synchronisation en arrière-plan.** Les données ne bougeaient qu'à l'ouverture de
      l'application : les widgets d'écran d'accueil affichaient donc un cache figé. Un travail
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
- [x] **Widgets configurables** : choisir la matière à la pose — et, pour un compte parent,
      l'enfant que le widget suit.
- [x] **Widgets adaptatifs.** Redimensionnables dans les deux sens, avec un contenu qui suit
      la place accordée (tuile, ligne, liste, semaine) et un rendu par taille annoncée sur
      Android 12+. Aucun ne dit plus « rien » : la journée finie bascule sur le prochain jour
      d'école, et un cours en cours porte son avancement.
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
- [x] **Audit d'accessibilité** : libellés, tailles de police, contrastes, titres annoncés,
      région vivante sur l'état de synchronisation, courbe résumée en une phrase, widgets
      résumés en une phrase, et un équivalent aux flèches pour chaque geste de balayage.
- [x] **Rafraîchissement au déverrouillage** : les widgets se redessinent depuis le cache
      quand on déverrouille le téléphone, et déclenchent une synchronisation si les données
      ont vieilli.
- [x] **Chasse aux redondances.** Deux widgets d'écran d'accueil répétaient un autre widget
      ou parlaient de l'application plutôt que de l'école ; ils ont laissé la place à la
      prochaine évaluation et aux absences à justifier. Sur l'accueil de l'application, la
      journée et la liste des devoirs sautent désormais ce que le bloc voisin met déjà en
      avant.

## Ce que la dernière série de travaux a ajouté

Sept chantiers, puis une relecture complète du code livré.

- [x] **Emploi du temps semaine par semaine.** Trois semaines chargées d'avance à chaque
      synchronisation faisaient payer à tout le monde ce que presque personne ne regarde. Une
      seule semaine, les autres à la demande, et le retour ramène sur le jour consulté.
- [x] **Corbeille purement locale.** Rien ne quitte l'appareil : le service n'offre pas la
      suppression, et le dire franchement vaut mieux que de la simuler.
- [x] **« Ce qui a changé ».** Un cours annulé, une salle déplacée, une note tombée — par
      comparaison de deux instantanés, avec notification et widget. Trois garde-fous pour ne
      pas annoncer comme changement ce qui n'est qu'une première synchronisation.
- [x] **Neuf widgets au lieu de seize**, et les quatre widgets horaires dessinés à l'échelle
      du temps dès deux cases sur deux. « Si on donne de l'espace à un widget, c'est pour
      qu'il l'utilise, pas pour avoir plus de la moitié de vide. »
- [x] **Impact de chaque note et objectif de trimestre** : ce que ce 8 a coûté, et la note
      qui suffirait à atteindre la moyenne visée.
- [x] **« Demain / prépare ton sac »**, avec rappel du soir, désactivable.
- [x] **Heures creuses utilisées** : un plan de travail dans les trous de la journée, sans
      jamais prendre l'heure du déjeuner ni promettre de durées qu'on ne connaît pas.
- [x] **Fiche professeur** et **année en chiffres**, chaque carte annonçant sa portée réelle.
- [x] **Image de semaine partageable**, dessinée pour être lue par un tiers.
- [x] **Relecture de tout ce qui précède.** Quatre passes — défauts, performance, usage,
      écarts avec ce que le service offre — puis correction : des chiffres faux présentés à
      l'élève, une mémoire de notifications qui finissait par boucler, la messagerie paginée
      jetée à chaque synchronisation, le keep-alive TLS tué à chaque requête, le bouton retour
      qui ne rendait pas un cran, et les contrastes du thème clair sous le seuil lisible.

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
  classe étant acceptée aussi bien comme objet que comme simple libellé ;
- l'aller-retour de la seule écriture du client — cocher un devoir côté serveur. Le résultat
  est désormais lu et consigné en cas de refus, et un identifiant fabriqué localement n'est
  jamais envoyé, mais le verbe et la forme du corps restent invérifiables d'ici. Un compte de
  test d'établissement le dirait en une minute ; un vrai professeur n'est pas une cible
  d'essai ;
- le rendu réel des neuf gabarits de widgets. Ils n'ont jamais été inflatés : seul un
  appareil, ou `RemoteViews.apply` sous Robolectric, peut montrer qu'une vue refusée ne laisse
  pas une carte blanche et muette sur l'écran d'accueil. La suite de tests d'interface, elle,
  peut enfin s'exécuter : il lui manquait simplement un lanceur déclaré.
