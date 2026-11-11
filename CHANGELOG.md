# Charge trottinette — journal des versions

Trottinette iSinwheel D1 Plus (48 V / 15 Ah / 720 Wh), chargeur 2 A, prise Shelly Plug M Gen3.
Numérotation : `majeur.mineur.correctif`. Chaque version est un tag git (`vX.Y.Z`) ; voir `VERSIONING.md`.

## v0.1.0 — appli Android (en préparation, non compilée, 2026-11-11)

Ajouté
- Projet Android natif (Kotlin / Jetpack Compose) dans `android/` : lecture Bluetooth du niveau de batterie, calcul de la durée de charge, commande de la prise Shelly en HTTP local.
- Lecture Bluetooth (lecture seule) : service `0xFF60`, notifications de `0xFF61`. Trame de type `0x20` : octet 8 = pourcentage ; trame de type `0x21` : octets 7-8 = tension (centièmes de volt), octet 19 = température du moteur. Positions relevées sur la D1 Plus et comparées à l'appli iSinwheel (83 %, 49,8 V / 51,9 V, 25 / 30 °C).
- Saisie manuelle du niveau si le Bluetooth échoue.
- Dernière commande envoyée affichée à l'ouverture (type, heure, niveaux, durée, fin prévue, résultat) et comparée à l'état réel de la prise.
- Reprise des fonctions de la page web v0.0.2 : batterie chaude (délai de refroidissement), « prête pour une heure donnée », charge à 100 % ponctuelle, test de joignabilité, pastille d'état, test des 3 clics, planification enregistrée dans la prise.
- Planification calculée à partir de l'heure du téléphone, avec avertissement si l'heure de la prise diffère.
- Mise à jour dans l'appli (même principe que TopDriver) : lecture de `version.json` dans `charge-trotinette-releases`, bandeau « Mise à jour disponible ».
- Tests unitaires (décodage des trames, calcul des durées) et `android/release.ps1` (compilation, publication, tag).

Limites connues
- Le code n'a pas pu être compilé ni exécuté dans l'environnement de rédaction : une première série d'erreurs de compilation est possible.
- Décodage vérifié sur 3 trames seulement ; le pourcentage (octet 8 de la trame `0x20`) est confirmé par un seul relevé (83 %).
- Interprétation des autres octets inconnue (dont le kilométrage, volontairement non affiché).
- APK signé avec la clé de debug du PC de compilation : les mises à jour ne s'installent que si l'APK est compilé sur le même PC.
- Le dépôt `charge-trotinette-releases` n'existe pas encore : la vérification de mise à jour échoue en silence.

## v0.0.2 — fiabilité de la communication avec la prise (2026-11-09)

Ajouté
- Test de joignabilité (limite de 4 s) avant chaque lancement, planification ou coupure : si la prise ne répond pas, rien n'est envoyé et le message l'indique.
- Pastille d'état en haut de page (joignable / injoignable / IP à saisir), mise à jour à l'ouverture, au changement d'IP et à chaque action.
- Bouton « Tester la prise » (réglages) : trois impulsions de 1 s pour entendre le relais et voir le voyant. Refusé si la prise est allumée ; confirmation demandée si l'état est illisible.
- Vérification après un lancement immédiat : prise allumée, puissance supérieure à 5 W (alerte si le chargeur semble débranché de la trottinette).
- Vérification de la planification enregistrée dans la prise quand la lecture est possible.

Modifié
- Messages plus honnêtes : « commande envoyée, état non vérifiable » au lieu de « charge lancée » quand la lecture est bloquée ; distinction entre prise injoignable et lecture bloquée.
- « Annuler et couper » relit l'état de la prise après la coupure.

Limites connues
- Non testé sur la prise réelle. Le comportement du voyant de la Plug M Gen3 n'est pas vérifié (le clic du relais reste le repère le plus sûr).
- La lecture de l'état dépend toujours de l'autorisation CORS de la prise.

## v0.0.1 — première itération (2026-11-09)

Ajouté
- Calcul de la durée de charge jusqu'à l'objectif (80 % par défaut) à partir du niveau saisi, du chargeur (A), de la batterie (Ah) et d'un coefficient de correction.
- Lancement immédiat : la prise s'allume et se coupe toute seule (`Switch.Set` avec `toggle_after`).
- Case « Batterie chaude » : retarde le démarrage du délai de refroidissement réglé (30 min par défaut).
- Mode « Prête pour une heure donnée » : calcule l'heure de démarrage pour finir juste avant.
- Case « Charger à 100 % cette fois » (valable pour la charge en cours uniquement).
- Planification dans la prise (`Schedule.Create`), avec une seule planification à la fois (`Schedule.DeleteAll` avant chaque lancement).
- Bouton « Annuler et couper » ; état de la prise, puissance, énergie depuis le lancement et planifications visibles en touchant le cadre de message.
- Avertissement si l'heure de la prise diffère de celle du téléphone.
- Durée maximale (8 h par défaut) et numéro de version affiché en bas de page.

Limites connues
- La planification et sa vérification n'ont pas encore été testées sur la prise.
- La lecture de l'état dépend de l'autorisation CORS de la prise ; sinon la commande part quand même mais reste non vérifiable.
- Une planification créée reste enregistrée dans la prise après son exécution (elle est supprimée au lancement suivant).
- Le niveau de batterie est saisi à la main (lecture Bluetooth à étudier).

À venir
- Historique des charges, auto-calibration du coefficient, mode stockage (50 %), alertes, lecture Bluetooth.
