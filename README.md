# ETL-Processor for bwHC data

Diese Anwendung versendet ein bwHC-MTB-File an das bwHC-Backend und pseudonymisiert die Patienten-ID.

## Pseudonymisierung der Patienten-ID

Wenn eine URI zu einer gPAS-Instanz angegeben ist, wird diese verwendet.
Ist diese nicht gesetzt. wird intern eine Anonymisierung der Patienten-ID vorgenommen.

* `APP_PSEUDONYMIZE_PREFIX`: Standortbezogenes Prefix - `UNKNOWN`, wenn nicht gesetzt
* `APP_PSEUDONYMIZE_GPAS_URI`: URI der gPAS-Instanz inklusive Endpoint (z.B. `http://localhost:8080/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate`)
* `APP_PSEUDONYMIZE_GPAS_TARGET`: gPas Domänenname
* `APP_PSEUDONYMIZE_GPAS_USERNAME`: gPas Basic-Auth Benutzername
* `APP_PSEUDONYMIZE_GPAS_PASSWORD`: gPas Basic-Auth Passwort
* `APP_PSEUDONYMIZE_GPAS_SSLCALOCATION`: Root Zertifikat für gPas, falls es dediziert hinzugefügt werden muss.

## Mögliche Endpunkte

Für REST-Requests als auch (parallel) zur Nutzung von Kafka-Topics können Endpunkte konfiguriert werden.

### REST

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein bwHC-MTB-File an das bwHC-Backend gesendet wird:

* `APP_REST_URI`: URI der zu benutzenden API der bwHC-Backend-Instanz. z.B.: `http://localhost:9000/bwhc/etl/api`

### Kafka-Topics

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein bwHC-MTB-File an ein Kafka-Topic übermittelt wird:

* `APP_KAFKA_TOPIC`: Zu verwendendes Topic
* `APP_KAFKA_SERVERS`: Zu verwendende Kafka-Bootstrap-Server als kommagetrennte Liste

Weitere Einstellungen können über die Parameter von Spring Kafka konfiguriert werden.