# Test with gPAS
1. Download from [Latest Docker-compose version of gPAS](https://www.ths-greifswald.de/gpas/#_download "")
2. copy `./demo/demo_gpas.sql` into `./sqls` folder
3. if needed change port mapping
4. startup via `docker compose up -d`

By default, PSN are created via `localhost:8080/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate` endpoint
You can review created PSN via gPAs web interface running at `http://localhost:8080/gpas-web/` 



