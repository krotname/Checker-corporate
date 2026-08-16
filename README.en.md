# Checker Corporate

[![CI](https://github.com/krotname/CompanyStatusChecker/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)
[![Mutation Testing](https://github.com/krotname/CompanyStatusChecker/actions/workflows/mutation-testing.yml/badge.svg?branch=main&event=push)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/mutation-testing.yml?query=branch%3Amain+event%3Apush)
[![CodeQL](https://github.com/krotname/CompanyStatusChecker/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/codeql.yml?query=branch%3Amain)
[![Coverage](https://codecov.io/gh/krotname/CompanyStatusChecker/branch/main/graph/badge.svg)](https://app.codecov.io/gh/krotname/CompanyStatusChecker)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/krotname/CompanyStatusChecker/badge)](https://securityscorecards.dev/viewer/?uri=github.com/krotname/CompanyStatusChecker)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%20LTS-007396)](https://openjdk.org/projects/jdk/21/)

## EN

### What it is
`Checker Corporate` is a production-oriented Java showcase that validates company INN and returns status from DaData.
It is designed to demonstrate clean layering, testability, and CI quality posture.

### What’s inside

- `validation` — INN format and checksum validation.
- `client` — DaData HTTP transport and response parser.
- `config` — runtime configuration loading.
- `ui` — tiny HTTP server for `/`, `/health`, `/api/check`.
- `static/index.html` — dashboard-style browser UI.

Review documents: [architecture](docs/architecture.md), [quality gates](docs/quality-gates.md), [OpenAPI](docs/openapi.yaml).

### Run

```bash
cp src/main/resources/checker.example.properties src/main/resources/checker.properties
# token=<YOUR_DADATA_TOKEN>

./mvnw -q -DskipTests package
java -jar target/checker-corporate-*.[0-9].jar 9710083390
```

For a test or proxied API, override the endpoint with `DADATA_ENDPOINT` or the
`api.endpoint` key in `checker.properties`.

On Windows, use `mvnw.cmd` instead of `./mvnw`.

### API

- `GET /health` — automation endpoint.
- `GET /api/check?inn=<INN>` — company status in JSON.
This API is described in `docs/openapi.yaml`.

### Quality and reviewability

- Structured package layout with small, focused classes.
- Multiple test categories (unit/integration/ui/contract).
- Static bug-pattern analysis with SpotBugs in the default `verify` gate.
- Reproducible Maven artifacts through a fixed `project.build.outputTimestamp`.
- Source and Javadoc jars are built during `package` and attached to CI/release artifacts.
- Docker image runs as the non-root `app` user and includes a Java-based `HEALTHCHECK`.
- Security and release automation in GitHub Actions.
- Clear runtime setup: environment variable or properties resource.
- CycloneDX SBOM generation for dependency transparency.
- Dependency Review blocks pull requests that introduce high-severity runtime vulnerabilities.
- Release workflow emits artifact provenance and SBOM attestations for the JAR.
- Release workflow publishes the Docker image to `ghcr.io/krotname/company-status-checker` for tag releases.
- Community health files and issue forms for consistent public collaboration.

### Docker

```bash
./mvnw -q -DskipTests package
docker compose up --build
```

Open `http://localhost:8080`.

### Tests by layer

- `@Tag("unit")` — pure domain and validation tests.
- `@Tag("integration")` — network client and API integration tests.
- `@Tag("ui")` — API tests through the embedded HTTP server.
- `@Tag("contract")` — external JSON/OpenAPI contract tests.

Run by category:

```bash
./mvnw -q test                     # all tests
./mvnw -q test -Punit-tests        # unit only
./mvnw -q test -Pintegration-tests # integration only
./mvnw -q test -Pui-tests          # UI/API smoke only
./mvnw -q test -Pcontract-tests    # contract only
./mvnw -q verify -Pmutation-tests  # mutation testing gate
```

### CI gates

The main CI workflow exposes separate `unit`, `integration`, `ui`, and `contract` jobs and uploads Surefire reports for each category. A dedicated Mutation Testing workflow runs PIT with `mutationThreshold >= 80` and publishes the PIT HTML/XML report as an artifact.

### License

GPL-3.0-or-later — [LICENSE](LICENSE).
