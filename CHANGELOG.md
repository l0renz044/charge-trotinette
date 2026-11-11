# Charge trottinette — journal des versions

Trottinette iSinwheel D1 Plus (48 V / 15 Ah / 720 Wh), chargeur 2 A, prise Shelly Plug M Gen3.
Numérotation : `majeur.mineur.correctif`. Chaque version est archivée dans `versions/`.

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
