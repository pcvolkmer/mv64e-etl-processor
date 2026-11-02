# Konfigurationsübersicht

## Inhalt

1. Wesentliche Funktionen für das Modellvorhaben Genomsequenzierung
    1. [Vorgangsnummern](#vorgangsnummern)
    2. [Pseudonymisierung der Patienten-ID](#pseudonymisierung-der-patienten-id)
    3. [Teilnahmeerklärung und Broad-Consent mit gICS](#teilnahmeerklärung-und-broad-consent-mit-gics)
2. Anmeldung und Authentifizierung
    1. [Anmeldung mit einem Passwort](#anmeldung-mit-einem-passwort)
    2. [Nutzung eines Authentifizierungsservers und OpenID Connect](#nutzung-eines-authentifizierungsservers-und-openid-connect)
    3. [Tokenbasierte Authentifizierung für MTBFile-Endpunkt](#tokenbasierte-authentifizierung-für-mtbfile-endpunkt)
3. Konfiguration der Datenein- und ausgabe
   1. [Dateneingang über HTTP/REST](#dateneingang-über-httprest)
   2. [Dateneingang mit Apache Kafka](#dateneingang-mit-apache-kafka)
   3. [Mögliche REST-Endpunkte zur Datenausgabe](#mögliche-rest-endpunkte-zur-datenausgabe)
   4. [Datenübermittlung mit Apache Kafka](#datenübermittlung-mit-apache-kafka)
4. [Duplikaterkennung](#duplikaterkennung)
5. [Blockieren weiterer initialer Submissions](#blockieren-weiterer-initialer-submissions)
6. [FollowUp-Erkennung](#followup-erkennung)
7. [Transformation von Werten](#transformation-von-werten)
8. [Deployment mit Docker-Compose](#deployment-mit-docker-compose)
9. [Test der Betriebsbereitschaft](#test-der-betriebsbereitschaft)

## Essenzielle Funktionen für das Modellvorhaben Genomsequenzierung

### Vorgangsnummern

Für jede Datenübermittlung muss eine neue Vorgangsummer generiert werden, die
jede Übertragung eindeutig identifizieren, aber gleichzeitig dem Patienten zugeordnet werden kann.
Dies lässt sich durch weitere Pseudonyme abbilden, allerdings werden pro Originalwert mehrere
Pseudonyme benötigt.
Zu diesem Zweck muss in gPas eine **Multi-Pseudonym-Domäne** konfiguriert werden (siehe auch
*APP_PSEUDONYMIZE_GPAS_CCDN*).

**WICHTIG:** Deaktivierte Pseudonymisierung ist nur für Tests nutzbar. Vorgangsummern sind zufällig
und werden anschließend verworfen.

### Pseudonymisierung der Patienten-ID

Wenn eine URI zu einer gPAS-Instanz (Version >= 2023.1.0) angegeben ist, wird diese verwendet.
Ist diese nicht gesetzt, wird intern eine Anonymisierung der Patienten-ID vorgenommen.

* `APP_PSEUDONYMIZE_PREFIX`: Standortbezogenes Präfix - `UNKNOWN`, wenn nicht gesetzt
* `APP_PSEUDONYMIZE_GENERATOR`: `BUILDIN` oder `GPAS` - `BUILDIN`, wenn nicht gesetzt
* `APP_PSEUDONYMIZE_HMAC_KEY`: Wenn in Verbindung mit `BUILDIN` gesetzt, wird anstelle `SHA256` nun `SHA256-HMAC` mit
  dem angegebenen Key verwendet.

**Hinweis**

Die Pseudonymisierung erfolgt im ETL-Prozessor nur für die Patienten-ID.
Andere IDs werden mithilfe des standortbezogenen Präfixes (erneut) anonymisiert, um für den
aktuellen Kontext nicht vergleichbare IDs bereitzustellen.

#### Pseudonymisierung mit gPAS

Wurde die Verwendung von gPAS konfiguriert, so sind weitere Angaben zu konfigurieren.

Ab Version 2025.1 (Multi-Pseudonym Support)

* `APP_PSEUDONYMIZE_GPAS_URI`: URI der gPAS-Instanz REST API (e.g. http://127.0.0.1:9990/ttp-fhir/fhir/gpas)
* `APP_PSEUDONYMIZE_GPAS_USERNAME`: gPas Basic-Auth Benutzername
* `APP_PSEUDONYMIZE_GPAS_PASSWORD`: gPas Basic-Auth Passwort
* `APP_PSEUDONYMIZE_GPAS_PATIENT_DOMAIN`: gPas Domänenname für Patienten ID (ebenfalls gültig:
  `APP_PSEUDONYMIZE_GPAS_PID_DOMAIN`)
* `APP_PSEUDONYMIZE_GPAS_GENOM_DE_TAN_DOMAIN`: gPAS Multi-Pseudonym-Domäne für genomDE Vorgangsnummern (Clinical data
  node)

Soll anstelle der REST-Schnittstelle von gPAS die SOAP-Schnittstelle verwendet werden,
so ist nicht die URI der gPAS-Instanz anzugeben, sondern der SOAP-Endpoint:

* `APP_PSEUDONYMIZE_GPAS_SOAP_ENDPOINT`: SOAP-Endpoint der gPAS-Instanz (e.g. http://127.0.0.1:9990/gpas/gpasService)

#### Eingebaute Anonymisierung

Wurde keine oder die Verwendung der eingebauten Anonymisierung konfiguriert, so wird für die
Patienten-ID der entsprechende SHA-256-Hash gebildet und Base32-codiert - hierzu werden die ersten 42 Zeichen des Hashes
ohne endende "=" - zuzüglich des konfigurierten Präfixes als Patienten-Pseudonym verwendet.

Beispiel für ID `123` und Prefix `123`:

`UNKNOWN_uzs2iwjaiixz2ql6jbt67xcpxcqeuhz774p2a7uzr2`

Wird `APP_PSEUDONYMIZE_HMAC_KEY` konfiguriert, wird der Hash mit diesem Wert als HMAC-Key gebildet.
Auch hier werden die ersten 42 Zeichen Base64-codiert verwendet.
Hierdurch wird die Nutzung von Rainbow-Tables erschwert, solange der HMAC-Key geheim gehalten wird.

Beispiel für ID `123` und HMAC-Key `TEST` (gleichzeitig HMAC-Key):

`UNKNOWN_3u2nc3hzwbe2kylbmcrhoahgkisxl5jfbcwr3wonr3`

### Teilnahmeerklärung und Broad-Consent mit gICS

Externe Consent-Services können konfiguriert werden.

* `APP_CONSENT_SERVICE`: Zu verwendender (externer) Consent-Service:
    * `NONE`: Verwende Consent-Angaben im MTB-File v1 und ändere diese nicht.
    * `GICS`: Verwende gICS der [Greifswalder Tools gPAS und gICS](https://www.ths-greifswald.de/).

Ab gIcs Version 2.13.0 kann im ETL-Processor
per [REST-Schnittstelle](https://simplifier.net/guide/ttp-fhir-gateway-ig/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-isConsented?version=current)
der Einwilligungsstatus abgefragt werden.
Vor der MTB-Übertragung kann der zum Sendezeitpunkt verfügbarer Einwilligungsstatus über Endpunkt
*currentPolicyStatesForPerson* (MTB-File v2) abgefragt werden.

Falls Anbindung an gICS aktiviert wurde, wird der Einwilligungsstatus der MTB Datei ignoriert.
Stattdessen werden vorhandene Einwilligungen abgefragt und in die MTB Datei eingebettet.

Es werden zwei Einwilligungsdomänen unterstützt, eine für Broad Consent und als zweites GenomDE
Modelvorhaben §64e.

#### Hinweise

1. Die aktuelle Impl. nimmt an, dass die hinterlegten Domänen der Einwilligungen ausschließlich für
   die genannten Art von Einwilligungen genutzt werden. Es finde keine weitere Filterung statt. Wir
   fragen pro Domäne die Schnittstelle `CurrentPolicyStatesForPerson` - siehe
   auch [IG TTP-FHIR Gateway](https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-currentPolicyStatesForPerson.html)
   ab.
2. Die Einwilligung wird für den Patienten-Identifier der MTB abgerufen und anschließend durch das
   DNPM Pseudonym ersetzt.
3. Abfragen von Einwilligungen über gesonderte Pseudonyme anstatt des MTB-Identifiers fehlt in der
   ersten Implementierung.

#### Konfiguration

* `APP_CONSENT_SERVICE`: Folgende Werte sind möglich:
    * Der Wert `GICS` aktiviert die Standard-Abfrage bei gICS
    * Der Wert `GICS_GET_BC` aktiviert die **experimentelle** Abfrage per HTTP-GET Request **nur** für Broad-Consent.
    * Der Wert `NONE` deaktiviert die Abfrage in gICS.
* `APP_CONSENT_GICS_URI`: URI der gICS-Instanz (z.B. `http://localhost:8090/ttp-fhir/fhir/gics`)
* `APP_CONSENT_GICS_USERNAME`: gIcs Basic-Auth Benutzername
* `APP_CONSENT_GICS_PASSWORD`: gIcs Basic-Auth Passwort
* `APP_CONSENT_GICS_PERSONIDENTIFIERSYSTEM`: Derzeit wird nur die PID unterstützt. wenn leer wird
  `https://ths-greifswald.de/fhir/gics/identifiers/Patienten-ID` angenommen
* `APP_CONSENT_GICS_BROADCONSENTDOMAINNAME`: Domäne in der gIcs Broad Consent Einwilligungen
  verwaltet. Falls Wert leer, wird `MII` angenommen.
* `APP_CONSENT_GICS_GENOMDECONSENTDOMAINNAME`: Domäne in der gIcs GenomDE Modelvorhaben §64e
  Einwilligungen verwaltet. Falls Wert leer, wird keine Consent-Information abgerufen.
* `APP_CONSENT_GICS_POLICYCODE`: Die entscheidende Objekt-ID der zu prüfenden Einwilligung-Regel.
  Falls leer wird `2.16.840.1.113883.3.1937.777.24.5.3.6` angenommen.
* `APP_CONSENT_GICS_POLICYSYSTEM`: Das System der Einwilligung-Regel der Objekt-IDs. Falls leer wird
  `urn:oid:2.16.840.1.113883.3.1937.777.24.5.3` angenommen.
* `APP_CONSENT_GICS_POLICYURI`: Die Version der Einwilligung. Falls leer wird Version 1.6d
  (`urn:oid:2.16.840.1.113883.3.1937.777.24.2.1790`) angenommen.

Die **experimentelle* Abfrage über `GICS_GET_BC` setzt voraus, dass bereits ein MV-Consent im eingehenden Datensatz
vorhanden ist und ergänzt nur den Broad-Consent.

## Anmeldung und Authentifizierung

### Anmeldung mit einem Passwort

Ein initialer Administrator-Account kann optional konfiguriert werden und sorgt dafür, dass
bestimmte Bereiche nur nach einem erfolgreichen Login erreichbar sind.

* `APP_SECURITY_ADMIN_USER`: Muss angegeben werden zur Aktivierung der Zugriffsbeschränkung.
* `APP_SECURITY_ADMIN_PASSWORD`: Das Passwort für den Administrator (Empfohlen).

Ein Administrator-Passwort muss inklusive des Encoding-Präfixes vorliegen.

Hier Beispiele für das Beispielpasswort `very-secret`:

* `{noop}very-secret` (Das Passwort liegt im Klartext vor - nicht empfohlen!)
* `{bcrypt}$2y$05$CCkfsMr/wbTleMyjVIK8g.Aa3RCvrvoLXVAsL.f6KeouS88vXD9b6`
* `{sha256}9a34717f0646b5e9cfcba70055de62edb026ff4f68671ba3db96aa29297d2df5f1a037d58c745657`

Wird kein Administrator-Passwort angegeben, wird ein zufälliger Wert generiert und beim Start der
Anwendung in den Logs angezeigt.

#### Weitere Benutzer

Ab Version 0.15.0 können weitere Benutzer-Accounts konfiguriert werden, ohne OpenID Connect zu verwenden.
Diese haben lediglich Benutzerrechte und können keine Konfigurationen einsehen oder ändern.

Beispiele:

```
APP_SECURITY_USERS[0]_USERNAME=myuser
APP_SECURITY_USERS[0]_PASSWORD={noop}very-secret
APP_SECURITY_USERS[1]_USERNAME=otheruser
APP_SECURITY_USERS[1]_PASSWORD={bcrypt}$2y$05$CCkfsMr/wbTleMyjVIK8g.Aa3RCvrvoLXVAsL.f6KeouS88vXD9b6
...
```

### Nutzung eines Authentifizierungsservers und OpenID Connect

Die folgenden Konfigurationsparameter werden benötigt, um die Authentifizierung weiterer Benutzer an
einen OIDC-Provider zu delegieren.
Ein Admin-Benutzer muss dabei konfiguriert sein.

* `APP_SECURITY_ENABLE_OIDC`: Aktiviert die Nutzung von OpenID Connect. Damit sind weitere Parameter
  erforderlich
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_NAME`: Name. Wird beim zusätzlichen
  Loginbutton angezeigt.
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_ID`: Client-ID
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_SECRET`: Client-Secret
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_SCOPE[0]`: Hier sollte immer `openid`
  angegeben werden.
* `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_CUSTOM_ISSUER_URI`: Die URI des Providers,
  z.B. `https://auth.example.com/realm/example`
* `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_CUSTOM_USER_NAME_ATTRIBUTE`: Name des Attributes, welches
  den Benutzernamen
  enthält.
  Oft verwendet: `preferred_username`

Ist die Nutzung von OpenID Connect konfiguriert, erscheint ein zusätzlicher Login-Button zur Nutzung
mit OpenID Connect
und dem konfigurierten `CLIENT_NAME`.

![Login mit OpenID Connect](docs/login.png)

Weitere Informationen zur Konfiguration des OIDC-Providers
sind [hier](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html#oauth2-client)
zu finden.

#### Rollenbasierte Berechtigungen

Wird OpenID Connect verwendet, gibt es eine rollenbasierte Berechtigungszuweisung.

Die Standardrolle für neue OIDC-Benutzer kann mit der Option `APP_SECURITY_DEFAULT_USER_ROLE`
festgelegt werden.
Mögliche Werte sind `user` oder `guest`. Standardwert ist `user`.

Benutzer mit der Rolle "Gast" sehen nur die Inhalte, die auch nicht angemeldete Benutzer sehen.

Hierdurch ist es möglich, einzelne Benutzer einzuschränken oder durch Änderung der Standardrolle auf
`guest` nur
einzelne Benutzer als vollwertige Nutzer zuzulassen.

![Rollenverwaltung](docs/userroles.png)

Benutzer werden nach dem Entfernen oder der Änderung der vergebenen Rolle automatisch abgemeldet und
müssen sich neu anmelden.
Sie bekommen dabei wieder die Standardrolle zugewiesen.

#### Auswirkungen auf den dargestellten Inhalt

Nur Administratoren haben Zugriff auf den Konfigurationsbereich, nur angemeldete Benutzer können die
anonymisierte oder
pseudonymisierte Patienten-ID sowie den Qualitätsbericht von DNPM:DIP einsehen.

Wurde kein Administrator-Account konfiguriert, sind diese Inhalte generell nicht verfügbar.

### Tokenbasierte Authentifizierung für MTBFile-Endpunkt

Die Anwendung unterstützt das Erstellen und Nutzen einer tokenbasierten Authentifizierung für den
MTB-File-Endpunkt.

Dies kann mit der Umgebungsvariable `APP_SECURITY_ENABLE_TOKENS` aktiviert (`true` oder `false`)
werden
und ist als Standardeinstellung nicht aktiv.

Ist diese Einstellung aktiviert worden, ist es Administratoren möglich, Zugriffstokens für Onkostar
zu erstellen, die
zur Nutzung des MTB-File-Endpunkts eine HTTP-Basic-Authentifizierung voraussetzen.

![Tokenverwaltung](docs/tokens.png)

Hierzu kann der Endpunkt für das Onkostar-Plugin inklusive HTTP-Basic-Authentifizierung
konfiguriert werden.
Ist die Verwendung von Tokens aktiv, werden Anfragen ohne die Angabe der Token-Information abgelehnt.

## Konfiguration der Datenein- und ausgabe

### Dateneingang über HTTP/REST

Anfragen werden, wenn nicht als Duplikat behandelt, nach der Pseudonymisierung und Consenteinbettung direkt an DNPM:DIP
gesendet.

Ein HTTP-Request kann, angenommen die Installation erfolgte auf dem Host `dnpm.example.com` an
nachfolgende URLs gesendet werden:

| HTTP-Request | URL                                     | Bemerkung                                                      |
|--------------|-----------------------------------------|----------------------------------------------------------------|
| `POST`       | `https://dnpm.example.com/mtb`          | Die Anwendung verarbeitet den eingehenden Datensatz            |
| `DELETE`     | `https://dnpm.example.com/mtb/12345678` | Die Anwendung sendet einen Lösch-Request für Pat-ID `12345678` |

Anstelle des Pfads `/mtb` kann auch, wie in Version 0.9 und älter üblich, `/mtbfile` verwendet
werden. Siehe auch: https://github.com/pcvolkmer/mv64e-etl-processor/pull/196

### Dateneingang mit Apache Kafka

Anfragen werden, wenn nicht als Duplikat behandelt, nach der Pseudonymisierung und Consenteinbettung an Apache Kafka
übergeben.
Eine Antwort wird dabei ebenfalls mithilfe von Apache Kafka übermittelt und nach der Entgegennahme verarbeitet.

Siehe hierzu auch: https://github.com/pcvolkmer/mv64e-rest-to-kafka-gateway
und https://github.com/pcvolkmer/mv64e-kafka-to-rest-gateway.

### Mögliche REST-Endpunkte zur Datenausgabe

Für REST-Requests als auch zur Nutzung von Kafka-Topics können Endpunkte konfiguriert werden.

Es ist dabei nur die Konfiguration eines Endpunkts zulässig.
Werden sowohl REST als auch Kafka-Endpunkt konfiguriert, wird nur der REST-Endpunkt verwendet.

#### REST

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein MTB-File an DNPM:DIP gesendet wird:

* `APP_REST_URI`: URI der zu benutzenden API der Backend-Instanz. Zum Beispiel `http://localhost:9000/api`
* `APP_REST_TYPE`: Mögliche Werte `dip` (Standard) für DNPM:DIP und `nngm` für die nNGM-API.
* `APP_REST_USERNAME`: Basic-Auth-Benutzername für den REST-Endpunkt
* `APP_REST_PASSWORD`: Basic-Auth-Passwort für den REST-Endpunkt
* `APP_REST_API_KEY`: Angabe eines API-Keys zur Verwendung mit `APP_REST_TYPE=nngm` für die nNGM-API

#### Routed REST

Seit Version 0.18 ist eine Konfiguration des neu eingeführten **Routing**-Mechanismus möglich.
Damit kann für bestimmte Entitäten und Diagnosen ein abweichender REST-Endpunkt definiert werden.

Beispiel:

* `APP_ROUTING_NNGM_URI`: URI der nNGM-REST-API.
* `APP_ROUTING_NNGM_API_KEY`: API-Key zur Nutzung der nNGM-REST-API.
* `APP_ROUTING_NNGM_ICD10[0]`: Erster ICD-Code - weitere möglich.

Mit dieser Konfiguration wird jeder `PatientRecord`, dessen (letzte) Diagnose einen ICD-10-Code hat, dessen Anfang
konfiguriert ist, nicht über den Standard-Weg versendet, sondern an den für nNGM konfigurierten API-Endpunkt.

Wird zum Beispiel `APP_ROUTING_NNGM_ICD10[0] = C34` konfiguriert, werden Diagnosen wie "C34.0", "C34.1", etc. an das
nNGM
übermittelt.

### Datenübermittlung mit Apache Kafka

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein MTB-File an ein Kafka-Topic
übermittelt wird:

* `APP_KAFKA_OUTPUT_TOPIC`: Zu verwendendes Topic zum Versenden von Anfragen.
* `APP_KAFKA_OUTPUT_RESPONSE_TOPIC`: Topic mit Antworten über den Erfolg des Versendens.
  Standardwert: `APP_KAFKA_TOPIC` mit Anhang "_response".
* `APP_KAFKA_GROUP_ID`: Kafka GroupID des Consumers. Standardwert: `APP_KAFKA_TOPIC` mit Anhang "_
  group".
* `APP_KAFKA_SERVERS`: Zu verwendende Kafka-Bootstrap-Server als kommagetrennte Liste

Wird keine Rückantwort über Apache Kafka empfangen und es gibt keine weitere Möglichkeit den Status
festzustellen, verbleibt der Status auf `UNKNOWN`.

Weitere Einstellungen können über die Parameter von Spring Kafka konfiguriert werden.

Lässt sich keine Verbindung zu dem Backend aufbauen, wird eine Rückantwort mit Status-Code `900`
erwartet, welchen es
für HTTP nicht gibt.

Wird die Umgebungsvariable `APP_KAFKA_INPUT_TOPIC` gesetzt, kann eine Nachricht auch über dieses
Kafka-Topic an den ETL-Prozessor übermittelt werden.

Soll eine SSL-gesicherte Verbindung zu Kafka verwendet werden, so sind die SSL-Zertifikate in
der Spring-Konfiguration anzugeben.
Ein Beispiel findet sich in [`application-dev.yml`](src/main/resources/application-dev.yml).
Dies kann auch mit Umgebungsvariablen wie `SPRING_KAFKA_SECURITY_...` und `SPRING_KAFKA_SSL_...`
umgesetzt werden.

### Antworten und Statusauswertung

Seit Version 0.10 wird die Issue-Liste der Antwort verwendet und die darion enthaltene höchste
Severity-Stufe als Ergebnis verwendet.

| Höchste Severity | Status    |
|------------------|-----------|
| `info`           | `SUCCESS` |
| `warning`        | `WARNING` |
| `error`, `fatal` | `ERROR`   |

## Duplikaterkennung

Die Erkennung von Duplikaten ist normalerweise immer aktiv, kann jedoch über den
Konfigurationsparameter
`APP_DUPLICATION_DETECTION=false` deaktiviert werden.

## Blockieren weiterer initialer Submissions

Diese Anwendung blockiert weitere initiale Submissions nach der ersten erfolgreichen Übertragung in DNPM:DIP.
Sobald für einen Patienten eine Übertragung ohne Issues oder mit maximal Warnungen erfolgte und damit von
DNPM:DIP akzeptiert wurde, werden weitere Meldungen solange verworfen, bis ein Administrator den Patienten
wieder freigegeben hat.

`APP_POST_INITIAL_SUBMISSION_BLOCK` -> `true` | `false` (falls fehlt, wird `false` angenommen)

Nach Aktivierung des Features wird es für ältere Meldungen erforderlich sein, bereits durchgeführte
Initialmeldungen als solche zu kennzeichnen. Dies **muss** manuell in der Datenbank gemacht werden,
da der Typ der Meldung nicht verfügbar ist.

Gezielt Meldungen kennzeichnen:

```sql
UPDATE request
SET submission_type = "INITIAL"
WHERE id IN (...);
```

Alle MTB-File Meldungen (ohne Lösch-Request) kennzeichnen, die erfolgreich an DNPM:DIP gesendet wurden:

```sql
UPDATE request
SET submission_type = "INITIAL"
WHERE (status = "WARNING" OR status = "SUCCESS")
  AND type = "MTB_FILE";
```

## FollowUp-Erkennung

Ab Version 0.16 werden Meldungen als Follow-Ups erkannt und entsprechend weiterleiten, wenn

* die zu bearbeitende Meldung Follow-Ups enthält und das neueste Follow-Up nach der letzten Meldung für den Patienten datiert ist.
* zuvor eine initiale Meldung bereits erfolgte, die akzeptiert wurde.

## Transformation von Werten

In Onkostar kann es vorkommen, dass ein Wert eines Merkmalskatalogs an einem Standort angepasst
wurde und dadurch nicht dem Wert entspricht,
der von DNPM:DIP akzeptiert wird.

Diese Anwendung bietet daher die Möglichkeit, eine Transformation vorzunehmen. Hierzu muss der "Pfad"
innerhalb des JSON-MTB-Files angegeben werden und welcher Wert wie ersetzt werden soll.

Hier ein Beispiel für die erste (Index 0 - weitere dann mit 1,2, ...) Transformationsregel:

* `APP_TRANSFORMATIONS_0_PATH`: Pfad zum Wert in der JSON-MTB-Datei. Beispiel:
  `diagnoses[*].icd10.version` für **alle** Diagnosen
* `APP_TRANSFORMATIONS_0_FROM`: Angabe des Werts, der ersetzt werden soll. Andere Werte bleiben
  dabei unverändert.
* `APP_TRANSFORMATIONS_0_TO`: Angabe des neuen Werts.

## Deployment mit Docker-Compose

Ausführen als Docker Container:

```bash
docker compose up -d
```

### Einfaches Beispiel für ein eigenes Docker-Compose-File

Die Datei [`docs/docker-compose.yml`](docker-compose.yml) zeigt eine einfache Konfiguration für
REST-Requests basierend auf Docker-Compose mit der gestartet werden kann.

### Betrieb hinter einem Reverse-Proxy

Die Anwendung verarbeitet `X-Forwarded`-HTTP-Header und kann daher auch hinter einem Reverse-Proxy
betrieben werden.

Dabei werden, je nachdem welche Header durch den Reverse-Proxy gesendet werden auch Protokoll, Host
oder auch Path-Präfix
automatisch erkannt und verwendet werden. Dadurch ist z.B. eine abweichende Angabe des Pfads
problemlos möglich.

#### Beispiel *Traefik* (mit Docker-Labels):

Das folgende Beispiel zeigt die Konfiguration in einer Docker-Compose-Datei mit Service-Labels.

```
...
  deploy:
    labels:
      - "traefik.http.routers.etl.rule=PathPrefix(`/etl-processor`)"
      - "traefik.http.routers.etl.middlewares=etl-path-strip"
      - "traefik.http.middlewares.etl-path-strip.stripprefix.prefixes=/etl-processor"
...
```

#### Beispiel *nginx*

Das folgende Beispiel zeigt die Konfiguration einer _location_ in einer nginx-Konfigurationsdatei.

```
...
  location /etl-processor {
    set              $upstream http://<beispiel:8080>/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Scheme $scheme;
    proxy_set_header X-Forwarded-Proto  $scheme;
    proxy_set_header X-Forwarded-For    $remote_addr;
    proxy_set_header X-Real-IP          $remote_addr;
    proxy_pass       $upstream;
  }
...
```

### Integration einer Root-CA zur Laufzeit

Zur Integration einer Root-CA zur Laufzeit muss die Umgebungsvariable `SERVICE_BINDING_ROOT` z.B. auf den Wert `/bindings` gesetzt sein.
Zudem muss ein Verzeichnis `bindings/ca-certificates` - analog zum Verzeichnis
[`bindings/ca-certificates`](bindings/ca-certificates) mit einer PEM-Datei und der
Datei [`bindings/ca-certificates/type`](bindings/ca-certificates/type) als Docker-Volume eingebunden werden.

Beispiel für Docker-Compose:

```
...  
  environment:
    SERVICE_BINDING_ROOT: /bindings
    ...
  volumes:
    - "/path/to/bindings/ca-certificates/:/bindings/ca-certificates/:ro"
...
```

## Test der Betriebsbereitschaft
Um die volle Betriebsbereitschaft herzustellen, muss eine erfolgreiche Übertragung mit dem
Submission-Typ *Test* erfolgt sein. Über die Umgebungsvariable wird dieser Übertragungsmodus
aktiviert. Alle Datensätze mit erteilter Teilnahme am Modelvorhaben werden mit der Test-Submission-Kennung
übertragen, unabhängig vom ursprünglichen Wert.

`APP_GENOM_DE_TEST_SUBMISSION` -> `true` | `false` (falls fehlt, wird `false` angenommen)

**ACHTUNG**: Diese Einstellung funktioniert nur, wenn, wie in Marburg, eine vollständige Consent-Abfrage über gICS für
MV-Consent und Broad Consent erfolgt.

Soll eine Testsubmission ohne diese Anbindung durchgeführt werden, kann eine Transformation als Work-Around umgesetzt werden:

```
APP_TRANSFORMATION_0_PATH: "metadata.type"
APP_TRANSFORMATION_0_FROM: "initial"
APP_TRANSFORMATION_0_TO: "test"
```
