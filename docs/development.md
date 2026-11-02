# Entwicklungssetup

Zum Starten einer lokalen Entwicklungs- und Testumgebung kann die beiliegende Datei
`dev-compose.yml` verwendet werden.
Diese kann zur Nutzung der Datenbanken **MariaDB** als auch **PostgreSQL** angepasst werden.

Zur Nutzung von Apache Kafka muss dazu ein Eintrag im hosts-File vorgenommen werden und der Hostname
`kafka` auf die lokale
IP-Adresse verweisen. Ohne diese Einstellung ist eine Nutzung von Apache Kafka außerhalb der
Docker-Umgebung nicht möglich.

Zum Bereitstellen von JavaScript- und CSS-Bundles muss der Befehl `npm run build` ausgeführt werden.
Ein kontinuierliches Neubauen bei Änderungen in CSS und JS-Dateien ist mit `npm run dev` möglich.
Dies setzt eine NodeJS-Umgebung voraus.

Beim Start der Anwendung mit dem Profil `dev` wird die in `dev-compose.yml` definierte Umgebung beim
Start der Anwendung mit gestartet:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Die Datei `application-dev.yml` enthält hierzu die Konfiguration für das Profil `dev`.

Beim Ausführen der Integrationstests wird eine Testdatenbank in einem Docker-Container gestartet.
Siehe hier auch die Klasse `AbstractTestcontainerTest` unter `src/integrationTest`.

Ein einfaches Entwickler-Setup inklusive DNPM:DIP ist mit Hilfe
von https://github.com/pcvolkmer/dnpmdip-devenv realisierbar.

## Docker-Images lokal bauen

Ein Docker-Image kann mit dem folgenden Befehl lokal gebaut werden:

```bash
./gradlew bootBuildImage
```

### Integration eines eigenen Root CA Zertifikats

Wird eine eigene Root CA verwendet, die nicht offiziell signiert ist, wird es zu Problemen beim
SSL-Handshake kommen, wenn z.B. gPAS zur Generierung von Pseudonymen verwendet wird.

Hier bietet es sich an, das Root CA Zertifikat in das Image zu integrieren.

#### Integration beim Bauen des Images

Hier muss die Zeile `"BP_EMBED_CERTS" to "true"` in der Datei `build.gradle.kts` verwendet werden
und darf nicht als Kommentar verwendet werden.

Die PEM-Datei mit dem/den Root CA Zertifikat(en) muss dabei im vorbereiteten Verzeichnis [
`bindings/ca-certificates`](bindings/ca-certificates) enthalten sein.
