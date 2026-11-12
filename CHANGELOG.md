# Charge trottinette — journal des versions

Trottinette iSinwheel D1 Plus (48 V / 15 Ah / 720 Wh), chargeur 2 A, prise Shelly Plug M Gen3.
Numérotation : `majeur.mineur.correctif`. Chaque version est un tag git (`vX.Y.Z`) ; voir `VERSIONING.md`.

## v0.1.3 — activation du Bluetooth (2026-11-12)

Ajouté
- Si le Bluetooth du téléphone est éteint, « Lire (Bluetooth) » affiche la boîte de dialogue Android « Autoriser l'appli à activer le Bluetooth ? » ; après acceptation, la lecture démarre toute seule. En cas de refus ou de téléphone sans Bluetooth, un message l'indique.

Limites connues
- Android n'autorise pas l'activation silencieuse : la confirmation est obligatoire.
- Non compilé dans l'environnement de rédaction.

## v0.1.2 — dépôt unique pour les mises à jour (2026-11-12)

Modifié
- Comme TopDriver, un seul dépôt (`charge-trotinette`, public) contient les sources, `version.json` et `ChargeTrotinette_latest.apk`. L'appli lit ces deux fichiers à la racine du dépôt.
- `release.ps1` : compile, copie l'APK et écrit `version.json` à la racine, puis commit, tag et push. Plus de dépôt `charge-trotinette-releases`.
- `.gitignore` : l'APK `ChargeTrotinette_latest.apk` est le seul APK suivi.

## v0.1.1 — test du réseau Wi-Fi (2026-11-12)

Ajouté
- Réglage « Wi-Fi de la prise » (nom du réseau), saisi à la main, repris du téléphone (« Réseau actuel ») ou lu dans la prise (« Lire depuis la prise »).
- Ligne d'état sous l'en-tête : le téléphone est-il sur le Wi-Fi de la prise ? (OK, réseau différent, pas de Wi-Fi, localisation à autoriser ou activer). Touche pour retester.
- Bouton « Tester le réseau Wi-Fi » dans les Réglages.
- Si la prise ne répond pas lors d'un lancement, d'une coupure ou du test, le message ajoute le diagnostic du réseau.
- Permission de localisation (exigée par Android pour lire le nom du Wi-Fi ; la position n'est pas utilisée), plus ACCESS_WIFI_STATE et ACCESS_NETWORK_STATE.

Corrigé
- Lecture de l'heure de la prise : une valeur JSON nulle n'est plus lue comme le texte « null ».

Limites connues
- Pas de liste déroulante des réseaux : Android limite fortement les scans Wi-Fi et ne liste que les réseaux visibles, pas celui de la prise.
- Le test du nom de réseau est un diagnostic ; la pastille « prise joignable » reste le vrai test.
- Non compilé dans l'environnement de rédaction.

## v0.1.0 — appli Android (testée sur téléphone, 2026-11-12)

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
- Compilée et testée sur le téléphone de l'utilisateur le 2026-11-12 : fonctionne (prise, Bluetooth, charge).
- Décodage vérifié sur 3 trames seulement ; le pourcentage (octet 8 de la trame `0x20`) est confirmé par un seul relevé (83 %).
- Interprétation des autres octets inconnue (dont le kilométrage, volontairement non affiché).
- APK signé avec la clé de debug du PC de compilation : les mises à jour ne s'installent que si l'APK est compilé sur le même PC.
- Les adresses de mise à jour pointaient vers un dépôt `charge-trotinette-releases` abandonné à partir de la v0.1.2.

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
