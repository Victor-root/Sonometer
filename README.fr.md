# Sonomètre

[English](README.md)

Sonomètre Android développé avec Jetpack Compose. Le moteur de mesure suit la norme IEC 61672-1, celle des sonomètres physiques.

## Fonctionnalités

- **Pondérations fréquentielles A, C et Z**, tolérances de classe 1 à 44,1 et 48 kHz
- **Pondérations temporelles Fast, Slow et Impulse**, appliquées à chaque échantillon
- **LEQ, MIN, MAX et CRÊTE** : niveau équivalent en énergie, extrêmes pondérés dans le temps et niveau instantané le plus élevé
- Toutes les pondérations sont mesurées en même temps : en changer conserve les statistiques
- **Voyant de saturation**, allumé pendant la saturation puis grisé une fois qu'elle a eu lieu
- **Capture micro brute** : source `UNPROCESSED` si disponible, sinon `VOICE_RECOGNITION`, avec le contrôle automatique du gain et la réduction de bruit désactivés
- **Calibration** par rapport à un sonomètre de référence, mémorisée par source de micro
- Le démarrage et le micro pris par une autre app sont exclus des statistiques
- Graphique en direct et rapport PDF
- Anglais et français

## Précision de mesure

La conversion en dB SPL utilise la sensibilité de micro imposée par le document de compatibilité Android : 94 dB SPL donnent -36 dBFS en `UNPROCESSED`, 90 dB SPL donnent -22,35 dBFS en `VOICE_RECOGNITION`. Les téléphones réels varient de quelques dB et perdent en sensibilité sous 30 dB environ et dans les graves : une calibration avec un sonomètre de référence est recommandée.

## Compilation

Prérequis : Android Studio avec JDK 17 ou plus récent, SDK Android 37.

| Composant | Version |
|---|---|
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.8.2 |
| Kotlin | 2.4.21 |
| minSdk / targetSdk | 24 / 37 |

Toutes les versions des dépendances sont dans `gradle/libs.versions.toml`.

### Signature de la version release

Créer `keystore.properties` à la racine du projet (ignoré par Git) :

```properties
storeFile=chemin/vers/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Sans ce fichier, la version release est générée non signée.

## Tests

```
./gradlew :app:testDebugUnitTest
```

Les tests unitaires vérifient les filtres de pondération avec les tables de la norme IEC 61672-1, et le sonomètre avec des réponses de référence (salves de son, vitesses de décroissance, Leq, saturation, pause et reprise).
