# TaskPulse API

Spring Boot REST API for TaskPulse. Runs on port 8082.

## Environments

Configuration is split by Spring profile. `application.properties` holds what is true
everywhere; the profile file layered on top supplies whatever differs.

| Profile | File | Used for |
| --- | --- | --- |
| `local` | `application-local.properties` | Your machine. Active by default, so `mvn spring-boot:run` needs no flags. |
| `prod` | `application-prod.properties` | https://taskpluse-api.sokhin.site. Every credential comes from an environment variable. |
| `test` | `src/test/resources/application-test.properties` | `mvn test`, in-memory H2. Set by `@ActiveProfiles`, never chosen by hand. |

Switch profiles with `SPRING_PROFILES_ACTIVE=prod` or `-Dspring.profiles.active=prod`.

### Local

Needs PostgreSQL on `localhost:5432` with a `taskpluse_db` database.

```bash
./mvnw spring-boot:run
```

Seeds `demo@taskpulse.dev` / `taskpulse123` when the accounts table is empty, and allows
CORS from `http://localhost:4200`. Every value has a working default but can still be
overridden by an environment variable if your Postgres differs — see the file.

### Production

`application-prod.properties` gives no fallback for a credential on purpose: a missing
variable stops start-up with a named placeholder error, which is a better outcome than a
container quietly running on the development JWT key published in this repository.

Required: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_JWT_SECRET`.

```bash
cp .env.example .env      # then fill it in — .env is gitignored
docker build -t taskpulse-api .
docker run -d --name taskpulse-api -p 8082:8082 --env-file .env taskpulse-api
```

The image already sets `SPRING_PROFILES_ACTIVE=prod`.

`APP_JWT_SECRET` must be at least 32 characters (HS256 needs 256 bits):
`openssl rand -base64 48`. Changing it invalidates every issued token, which is also how
you revoke one that leaked.

### CORS

The web application is served from `https://taskpluse-web.sokhin.site`, a different
origin from the API, so the browser will not send a request the API has not explicitly
allowed. That origin is the default of `app.cors.allowed-origins` in the prod profile;
override it with `APP_CORS_ALLOWED_ORIGINS` (comma-separated, scheme included, no
trailing slash) to add a staging host.

If the two are ever placed behind one reverse proxy, this stops applying entirely —
set the web application's `apiBaseUrl` back to `''` and calls become same-origin.

## Tests

```bash
./mvnw test
```

Runs against in-memory H2, so no Postgres is required and your local database is never
touched.

## API documentation

Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`. Both are reachable in
production too; set `SPRINGDOC_ENABLED=false` to take them off the public internet.
