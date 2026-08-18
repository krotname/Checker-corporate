# Checker Corporate

[![CI](https://github.com/krotname/CompanyStatusChecker/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)
[![Mutation Testing](https://github.com/krotname/CompanyStatusChecker/actions/workflows/mutation-testing.yml/badge.svg?branch=main&event=push)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/mutation-testing.yml?query=branch%3Amain+event%3Apush)
[![CodeQL](https://github.com/krotname/CompanyStatusChecker/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/krotname/CompanyStatusChecker/actions/workflows/codeql.yml?query=branch%3Amain)
[![Coverage](https://codecov.io/gh/krotname/CompanyStatusChecker/branch/main/graph/badge.svg)](https://app.codecov.io/gh/krotname/CompanyStatusChecker)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/krotname/CompanyStatusChecker/badge)](https://securityscorecards.dev/viewer/?uri=github.com/krotname/CompanyStatusChecker)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/13149/badge)](https://www.bestpractices.dev/projects/13149)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%20LTS-007396)](https://openjdk.org/projects/jdk/21/)

[English README](README.en.md)

## RU

### Что это
`Checker Corporate` — это демонстрационный Java-сервис для валидации ИНН юрлица и проверки его статуса через DaData API. Проект показывает практический подход к:

- валидации входных данных до сетевых вызовов;
- устойчивой обработке интеграционных ошибок;
- выделению доменной модели (`CheckResult`, `CompanyStatus`);
- предоставлению CLI и HTTP UI;
- автоматизации качества через GitHub Actions.

### Что внутри

- `com.krotname.checker.validation` — проверка ИНН и контрольных сумм.
- `com.krotname.checker.client` — HTTP-клиент и парсер ответа DaData.
- `com.krotname.checker.config` — безопасная загрузка конфигурации.
- `com.krotname.checker.ui` — HTTP-сервер (`/`, `/health`, `/api/check`).
- `src/main/resources/static/index.html` — dashboard-style UI для ручной проверки и визуального демо.

Подробные материалы для ревью: [architecture](docs/architecture.md), [quality gates](docs/quality-gates.md), [OpenAPI](docs/openapi.yaml).

### Запуск

1. Установить Java 21.
2. Подготовить токен (предпочтительно переменной окружения):

```bash
set DADATA_TOKEN=your_token   # Windows
export DADATA_TOKEN=your_token # macOS/Linux
```

или, **только для локальных экспериментов**:

```bash
cp src/main/resources/checker.example.properties src/main/resources/checker.properties
# token=<Ваш токен DADATA>
```

`checker.properties` лежит в `src/main/resources`, поэтому Maven кладёт его внутрь
собранного JAR, а Dockerfile — внутрь образа. Реальный токен, записанный туда,
уезжает вместе с любым переданным артефактом, и достать его сможет каждый, кто
получил JAR или образ. Для сборок, которыми делятся, используйте `DADATA_TOKEN`
или менеджер секретов.

Для тестового или проксируемого API endpoint можно переопределить через `DADATA_ENDPOINT`
либо ключ `api.endpoint` в `checker.properties`.

На Windows используйте `mvnw.cmd` вместо `./mvnw`.

#### CLI

```bash
./mvnw -q -DskipTests package
java -jar target/checker-corporate-*.[0-9].jar 9710083390
```

#### Веб UI

```bash
./mvnw -q -DskipTests package
java -jar target/checker-corporate-*.[0-9].jar --server 8080
```

Откройте `http://localhost:8080`.

`/api/check` не требует аутентификации и на каждый запрос тратит квоту DaData
владельца токена, поэтому сервер по умолчанию слушает только loopback. Порт без
аргумента берётся из `CHECKER_PORT`, адрес привязки — из `CHECKER_BIND_ADDRESS`;
менять адрес на `0.0.0.0` имеет смысл только внутри контейнера, где границей
служит опубликованный порт.

### API

- `GET /health` — health-check для автоматизации.
- `GET /api/check?inn=<ИНН>` — статус компании.
Оба endpoint соответствуют `docs/openapi.yaml`.

Пример ответа:

```json
{
  "inn": "9710083390",
  "status": "ACTIVE",
  "dadataStatus": "ACTIVE",
  "message": "Организация активна."
}
```

### Docker

```bash
./mvnw -q -DskipTests package
docker compose up --build
```

Откройте `http://localhost:8080`.

В контейнере `CHECKER_BIND_ADDRESS=0.0.0.0` (иначе публикация порта не работала бы),
а compose публикует порт только на `127.0.0.1`. Чтобы отдать UI в сеть, снимите это
ограничение осознанно и закройте `/api/check` доступом на уровне сети или обратного
прокси. Порт меняется переменной `CHECKER_PORT` — её же читает `HEALTHCHECK`.

### UI surface

- Встроенный dashboard с состоянием сервиса, быстрыми сценариями и структурированным выводом результата.
- JSON можно копировать прямо из браузерного UI.
- Встроенный health ping показывает готовность локального сервера без внешних инструментов.

### Тестовая стратегия

- **Unit**: валидация ИНН, маппинг статусов DaData, конфигурация, доменные фабрики.
- **Интеграционные**: HTTP-клиент DaData с локальным тестовым сервером + маршруты API.
- **UI**: smoke-проверки `/`, `/api/check`, `/health`, включая ошибки валидации.
- **Contract/через интеграционный слой**: формат `DadataResponseParser` и поведение `CheckerCorporate.check`.

Классификация тестов:

- `@Tag("unit")` — изолированные и доменные проверки.
- `@Tag("integration")` — интеграции с HTTP-клиентом и HTTP API.
- `@Tag("ui")` — интеграционные проверки поведения пользовательского API.
- `@Tag("contract")` — проверки внешнего JSON/OpenAPI контракта.

Запуск выборочно по профилям:

```bash
./mvnw -q test                           # все тесты
./mvnw -q test -Punit-tests              # только unit
./mvnw -q test -Pintegration-tests       # только интеграционные (включая ui)
./mvnw -q test -Pui-tests                # только UI/API smoke
./mvnw -q test -Pcontract-tests          # только contract
./mvnw -q verify -Pmutation-tests        # mutation testing gate
```

### Качество и автоматизация

- `CI` (`.github/workflows/ci.yml`) — `./mvnw verify`.
- `CI` отдельно запускает `unit`, `integration`, `ui` и `contract` test jobs с публикацией Surefire reports.
- Docker image build + `/health` smoke test in CI.
- Docker image запускается под non-root пользователем `app` и содержит Java-based `HEALTHCHECK`.
- `JaCoCo` с минимальным порогом покрытия `LINE >= 0.70`.
- `PIT` mutation testing с минимальным порогом `mutationThreshold >= 80`.
- `SpotBugs` bug-pattern analysis с `effort=Max`, `threshold=Low` и fail-on-warning режимом.
- `Checkstyle` на этапе `verify`.
- Воспроизводимые Maven artifacts через фиксированный `project.build.outputTimestamp`.
- Source/Javadoc jars создаются на этапе `package` и прикладываются к CI/release artifacts.
- `CodeQL` и `OpenSSF Scorecard`.
- `Dependabot`, `Release` workflow.
- CycloneDX SBOM (`target/bom.xml`, `target/bom.json`) для supply-chain review.
- Dependency Review блокирует PR с новыми runtime-зависимостями высокой критичности.
- Release workflow выпускает artifact provenance и SBOM attestations для JAR.
- Release workflow публикует Docker image в `ghcr.io/krotname/company-status-checker` для tag-релизов.
- Community health files: `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, issue/PR templates.

```bash
./mvnw -q test
./mvnw -q verify
./mvnw -q verify -Pmutation-tests
```
