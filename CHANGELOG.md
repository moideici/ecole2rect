# Changelog

Toutes les évolutions notables de ce projet. Les versions suivent [SemVer](https://semver.org/lang/fr/).

## [0.2.0] : première version publique

Instantané du dépôt au moment de son ouverture au public. Détail de l'historique ci-dessous, par mois.

### 2026-11
- Interface sobre : messages de connexion, widgets et notifications plus courts, aide « ? » pour la fréquence
- Interface sobre : moins de texte dans Plus, écrans, présentation, avis et erreurs
- Plantage de l'interrupteur Notifications : Fragment à jour pour la demande de permission, test sur le téléphone
- Lot 4 : PLAN-PATCH et README (widgets Semaine/Journée complètes, thèmes, mode « montrer »)
- Widgets Semaine complète et Journée complète : paliers de taille, grille horaire, frise et aperçus
- Widgets : couleurs du thème choisi (dont Noir OLED), mode « montrer à quelqu'un » et redessin immédiat
- Lot 3 : PLAN-PATCH et README (hors ligne, fiabilité, apparence, vie privée, premier lancement)
- Présentation de trois pages au premier lancement, rejouable depuis Plus > À propos
- Mode « montrer à quelqu'un » : notes (et messages en option) masquées par ••• partout
- Verrouillage automatique après un délai réglable (immédiat, 1, 5 ou 15 minutes)
- Apparence : cinq thèmes de couleurs, noir pur OLED et taille du texte réglable
- Journal des synchronisations dans Plus > Diagnostic (30 derniers passages, sans donnée personnelle)
- Dates en français centralisées, bandeau d'ancienneté, erreurs précises, tirage sans requête inutile
- Lot 2 : PLAN-PATCH, README et docs/EXPORTS.md (formats, semaines A/B, limites)
- Écran d'export de l'emploi du temps (PDF ou ICS) depuis l'agenda, partage par fichier du cache
- Export PDF de l'emploi du temps : mise en page A4 paysage testée, dessin par PdfDocument
- Export de l'emploi du temps : semaines 1 / 2 (modèle pur) et fichier ICS
- Cours déplacé ou remplacé : notification avec l'ancien et le nouvel horaire, ancienne valeur barrée dans l'agenda
- Compte à rebours avant le prochain contrôle, à l'accueil et dans l'agenda
- Export d'un cours ou d'un contrôle vers le calendrier du téléphone, sans permission
- Agenda : vue semaine en colonnes sur frise commune, zoom au pincement et boutons +/−, réglage mémorisé
- Lot 1 : PLAN-PATCH et README à jour (recherche, lu/non lu local, filtres, rappel du soir, notes, docs/MOYENNES.md)
- Notes : moyenne officielle ou calculée dite clairement, document « Comment la moyenne est calculée », simulateur, courbes, écart à la classe, pastille « Nouveau »
- Recherche globale (devoirs, messages, notes, cours) dans les données déjà enregistrées, raccourci « Rechercher », ouverture des onglets vérifiée
- Devoirs : puces À faire / Faits / Contrôles / Tous, avec compteurs, mémorisées par profil
- … et 37 autres changements

### 2026-09
- Document ecole2recte ambitions and roadmap
- Auto-select birth date security answers
- Display the EcoleDirecte security challenge
- Reuse the complete EcoleDirecte session context
- Match Python authentication protocol exactly
- Handle automatic security reauthentication
- Match Python login request encoding
- Align Android login request with browser protocol
- Expose safe network diagnostics on login
- Fix EcoleDirecte authentication flow
- Build ecole2recte Android V1
- Initial commit
