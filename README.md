# Dialogue API

`dialogue-api` — это HTTP API-сервис на Spring Boot, который принимает внешние POST-запросы, хранит шаги диалога в SQLite, вызывает OpenAI Responses API, проверяет ответ модели как строго валидный JSON, добавляет `dialogTotalTokens`, сохраняет итоговый шаг в базу и возвращает JSON клиенту.

Дополнительно сервис поддерживает распознавание аудио в текст через OpenAI по endpoint `POST /api/audio/transcribe`.

## Основной сценарий

Тело запроса для `POST /api/dialogue/step`:

```json
{
  "dialogId": "dialog-001",
  "stepId": 1,
  "prompt": "Сделай план действий",
  "model": "gpt-5.4-mini"
}
```

Правила обработки:

1. Сервер валидирует `dialogId`, `stepId` и `prompt`.
2. Модель выбирается из `model` в запросе, либо из `app.openai.model`, если поле не передано.
3. Поддерживаются `gpt-5.4-mini` и `gpt-5.4-nano`.
4. Если `stepId == 1`, OpenAI вызывается без `previous_response_id`.
5. Если `stepId > 1`, сервис загружает из SQLite запись `(dialogId, stepId - 1)`.
6. Если предыдущий шаг не найден, API возвращает ошибку и прекращает обработку.
7. `openai_response_id` предыдущего шага используется как `previous_response_id` в первом запросе OpenAI для нового шага.
8. Ответ модели обязан быть строгим JSON-объектом с полями `stepDebug`, `dialogGoal` и `actions`.
9. Если первый ответ модели невалиден, сервис делает ровно один повторный запрос, используя внешний corrective prompt из `src/main/resources/prompts/retry-invalid-json.txt`.
10. Если и повторный ответ невалиден, API пишет критическую ошибку в лог, при необходимости отправляет уведомление в Telegram и возвращает ошибку клиенту.
11. Если JSON валиден, сервер добавляет в ответ `model`, `requestTokens`, `responseTokens`, `stepTotalTokens`, `dialogTotalTokens`, `previousResponseIdUsed`, сохраняет шаг и возвращает ответ клиенту.

## Распознавание аудио

Endpoint `POST /api/audio/transcribe` принимает `multipart/form-data` и распознаёт аудиофайл в текст через OpenAI.

Поддерживаемые поля:

- `file` — обязательный аудиофайл
- `language` — необязательный язык, например `ru`
- `source` — необязательный источник, например `telegram`
- `originalFileName` — необязательное исходное имя файла

Сценарий:

1. Telegram-бот или другой клиент передаёт аудиофайл на API.
2. Сервис отправляет файл в OpenAI transcription API.
3. Получает текстовую расшифровку.
4. Возвращает результат в JSON.

Пример успешного ответа:

```json
{
  "success": true,
  "text": "распознанный текст",
  "source": "telegram",
  "language": "ru",
  "originalFileName": "voice.ogg"
}
```

При ошибке API возвращает единый формат:

```json
{
  "success": false,
  "error": "понятное сообщение",
  "details": "технические детали для разработчика"
}
```

## Цепочка dialogId / stepId

`dialogId` идентифицирует один логический диалог. `stepId` — номер шага внутри этого диалога.

- `dialogId=dialog-001, stepId=1` начинает новую цепочку.
- `dialogId=dialog-001, stepId=2` обязан найти `dialogId=dialog-001, stepId=1`.
- `dialogId=dialog-001, stepId=3` обязан найти `dialogId=dialog-001, stepId=2`.

Это гарантирует последовательное продолжение диалога и позволяет считать накопительные токены по всей цепочке.

## Как используется previous_response_id

Приложение работает через OpenAI Responses API.

- Для первого шага `previous_response_id` не передаётся.
- Для продолжения диалога первый OpenAI-запрос использует `openai_response_id` из предыдущей записи в базе.
- Если первый ответ текущего шага невалиден и выполняется retry, повторный запрос использует ID первого невалидного ответа как `previous_response_id`.

В таблице сохраняются:

- `openai_response_id`: ID финального ответа OpenAI, который был сохранён для шага
- `previous_response_id`: ID ответа, использованного в финальном успешном запросе, либо `null` для самого первого шага

## Подсчёт токенов

Для каждого сохранённого шага приложение хранит:

- `request_tokens`
- `response_tokens`
- `dialog_total_tokens`

Правила:

- Токены текущего шага = `usage.total_tokens` (сумма `total_tokens` по попыткам шага)
- Для первого шага: `dialog_total_tokens = токены текущего шага`
- Для следующего шага: `dialog_total_tokens = dialog_total_tokens предыдущего шага + токены текущего шага`

Поведение при retry:

- Если первый ответ OpenAI невалиден, учитываются обе попытки.
- В базу всё равно сохраняется только финальный валидный `answer_text`.
- `request_tokens`, `response_tokens` и `stepTotalTokens` суммируются по обеим попыткам.
- `dialog_total_tokens` тоже включает обе попытки.

Сервис читает `usage` из ответа OpenAI и аккуратно деградирует, если часть полей отсутствует.
При использовании `previous_response_id` входные токены могут включать контекст диалога, поэтому следующий шаг может быть заметно дороже первого.

## Что такое dialogTotalTokens

`dialogTotalTokens` добавляется сервером после валидации JSON и до сохранения/возврата ответа. В исходном ответе модели это поле не требуется.

Итоговый payload внутри `data`:

```json
{
  "stepDebug": "На этом шаге анализируется входной запрос и формируется список действий",
  "dialogGoal": "Выполнить задачу пользователя по шагам через набор формализованных действий",
  "model": "gpt-5.4-mini",
  "requestTokens": 800,
  "responseTokens": 100,
  "stepTotalTokens": 900,
  "dialogTotalTokens": 900,
  "previousResponseIdUsed": null,
  "actions": [
    {
      "command": "create_file",
      "description": "Создать файл конфигурации приложения",
      "params": {
        "path": "/app/config.json",
        "content": "{\"name\":\"test\"}"
      }
    }
  ]
}
```

## Какой JSON должна вернуть модель

Модель обязана вернуть только один JSON-объект, без markdown и без лишнего текста:

```json
{
  "stepDebug": "string",
  "dialogGoal": "string",
  "actions": [
    {
      "command": "string",
      "description": "string",
      "params": {}
    }
  ]
}
```

Проверки валидации:

1. Весь ответ является валидным JSON.
2. Корень JSON — объект.
3. `stepDebug` существует и является строкой.
4. `dialogGoal` существует и является строкой.
5. `actions` существует и является массивом.
6. Каждый элемент `actions` содержит `command`, `description`, `params`.
7. `command` — строка.
8. `description` — строка.
9. `params` — JSON-объект.

## Конфигурация

Стандартный `src/main/resources/application.properties`:

```properties
server.port=7003

app.security.token=
app.openai.api-key=
app.openai.model=gpt-5.4-mini
app.openai.transcription-model=whisper-1
app.openai.base-url=https://api.openai.com
app.audio.max-file-size-mb=25

app.telegram.bot-token=
app.telegram.chat-id=

spring.datasource.url=jdbc:sqlite:./data/app.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

`app.openai.model` и поле `model` в `POST /api/dialogue/step` поддерживают:

- `gpt-5.4-mini`
- `gpt-5.4-nano`

### OpenAI API key

Порядок разрешения ключа:

1. `app.openai.api-key`
2. переменная окружения `OPENAI_API_KEY`

Если оба варианта пустые, приложение завершится при старте с понятной ошибкой.

### Модель распознавания аудио

Для transcription endpoint используется отдельная настройка:

```properties
app.openai.transcription-model=whisper-1
```

Также можно ограничить максимальный размер аудиофайла:

```properties
app.audio.max-file-size-mb=25
```

### SQLite

По умолчанию SQLite-файл находится по пути `./data/app.db`. Родительский каталог должен существовать или быть доступным для создания пользователем сервиса.

Таблица `dialogue_steps` содержит:

- `id`
- `dialog_id`
- `step_id`
- `prompt_text`
- `answer_text`
- `openai_response_id`
- `previous_response_id`
- `request_tokens`
- `response_tokens`
- `dialog_total_tokens`
- `created_at_ms`

Также создаются:

- unique constraint on `(dialog_id, step_id)`
- index on `dialog_id`

### Уведомления в Telegram

Необязательные настройки:

```properties
app.telegram.bot-token=
app.telegram.chat-id=
```

Если оба поля заполнены, приложение отправляет уведомления:

1. сразу после получения входящего запроса
2. после получения финального ответа, который возвращается клиенту
3. после критической ошибки при неудачном retry
4. при входящем запросе на transcription
5. после успешной расшифровки аудио

Если отправка в Telegram не удалась, основной API-запрос не падает.

### Токен доступа к API

Необязательная защита приложения:

```properties
app.security.token=
```

- Если поле пустое, запросы принимаются без `X-API-TOKEN`.
- Если токен задан, каждый запрос обязан содержать `X-API-TOKEN: <token>`.
- Иначе API вернёт `401 Unauthorized`.

## Retry prompt

Текст corrective prompt вынесен в отдельный файл:

`src/main/resources/prompts/retry-invalid-json.txt`

Файл читается при старте приложения. Если он отсутствует или пуст, запуск завершается ошибкой. Это позволяет менять corrective prompt без изменения Java-кода.

## Сборка и запуск

Сборка:

```bash
gradle test
gradle bootJar
```

Локальный запуск:

```bash
OPENAI_API_KEY=your_key gradle bootRun
```

Или запуск jar-файла:

```bash
java -jar build/libs/dialogue-api-0.0.1-SNAPSHOT.jar
```

Перед первым запуском убедитесь, что существует каталог для SQLite:

```bash
mkdir -p data
```

## Проверка через curl

### 1. Проверка health endpoint

```bash
curl -s http://localhost:7003/health
```

Ожидаемый ответ:

```json
{
  "success": true,
  "status": "UP"
}
```

### 2. Первый шаг диалога без токена приложения

```bash
curl -s -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-test-001",
    "stepId": 1,
    "prompt": "Сделай план действий по настройке сервиса",
    "model": "gpt-5.4-mini"
  }'
```

Ожидается JSON вида:

```json
{
  "success": true,
  "dialogId": "dialog-test-001",
  "stepId": 1,
  "model": "gpt-5.4-mini",
  "data": {
    "stepDebug": "...",
    "dialogGoal": "...",
    "model": "gpt-5.4-mini",
    "requestTokens": 100,
    "responseTokens": 20,
    "stepTotalTokens": 120,
    "dialogTotalTokens": 123,
    "previousResponseIdUsed": null,
    "actions": [
      {
        "command": "...",
        "description": "...",
        "params": {}
      }
    ]
  }
}
```

### 3. Второй шаг того же диалога

Этот запрос проверяет, что работает цепочка `dialogId / stepId` и используется `previous_response_id`.

```bash
curl -s -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-test-001",
    "stepId": 2,
    "prompt": "Продолжи этот план и добавь шаги проверки",
    "model": "gpt-5.4-nano"
  }'
```

### 4. Негативная проверка: отсутствует предыдущий шаг

```bash
curl -s -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-missing-prev",
    "stepId": 2,
    "prompt": "Этот шаг должен завершиться ошибкой"
  }'
```

Ожидается ответ вида:

```json
{
  "success": false,
  "error": "Previous step not found for dialogId=dialog-missing-prev and stepId=1"
}
```

### 5. Проверка с токеном приложения

Сначала задайте в конфиге:

```properties
app.security.token=test-token-123
```

После этого без заголовка запрос должен вернуть `401 Unauthorized`:

```bash
curl -i -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-auth-001",
    "stepId": 1,
    "prompt": "Проверка токена"
  }'
```

А с правильным заголовком запрос должен пройти:

```bash
curl -s -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -H 'X-API-TOKEN: test-token-123' \
  -d '{
    "dialogId": "dialog-auth-001",
    "stepId": 1,
    "prompt": "Проверка токена"
  }'
```

### 6. Что проверить после успешного ответа

- Сервис отвечает на `GET /health`
- Первый шаг с `stepId=1` проходит успешно
- Второй шаг с тем же `dialogId` и `stepId=2` тоже проходит
- В ответе есть `model` и `data.model`
- В ответе есть `data.dialogTotalTokens`
- В `data.actions` приходит массив объектов
- При запросе `stepId=2` без предыдущего шага API возвращает ошибку

### 7. Проверка распознавания аудио через curl

```bash
curl -X POST http://localhost:7003/api/audio/transcribe \
  -H "X-API-TOKEN: my-secret-token" \
  -F "file=@voice.ogg" \
  -F "language=ru" \
  -F "source=telegram" \
  -F "originalFileName=voice.ogg"
```

Если токен приложения выключен, заголовок `X-API-TOKEN` можно не передавать.

Пример успешного ответа:

```json
{
  "success": true,
  "text": "пример распознанного текста",
  "source": "telegram",
  "language": "ru",
  "originalFileName": "voice.ogg"
}
```

## systemd-скрипты

Скрипты лежат в `scripts/` и управляют сервисом `agent-elaira-gpt-server.service`.

- Установка: `scripts/service-install.sh`
- Старт: `scripts/service-start.sh`
- Остановка: `scripts/service-stop.sh`
- Удаление: `scripts/service-uninstall.sh`
- Статус: `scripts/service-status.sh`
- Перезапуск: `scripts/service-restart.sh`

После установки рядом с проектом создаётся файл `.env.service`.
В него можно положить переменные окружения для systemd-сервиса, например:

```properties
OPENAI_API_KEY=ваш_ключ_openai
```

Это важно, потому что systemd не получает автоматически переменные окружения из вашего интерактивного shell.

## Структура проекта

Пакеты:

- `config` - конфигурация, свойства, interceptor для токена, MVC-настройки
- `controller` - REST endpoint'ы
- `dto` - DTO запросов и ответов
- `entity` - JPA entity для `dialogue_steps`
- `repository` - Spring Data repository
- `client` - HTTP-клиенты OpenAI и Telegram, включая transcription client
- `service` - orchestration-логика, transcription-логика, загрузка retry prompt, уведомления Telegram
- `validation` - строгая валидация JSON-ответа модели
- `exception` - исключения API и глобальный обработчик

Документация:

- `docs/model-output-format.json`
- `docs/api-format.md`
- `docs/help.md`
- `docs/openapi.yaml`
- `docs/ru/architecture.md`
- `docs/ru/operations.md`
- `docs/ru/ai-rebuild-prompt.md`
- `docs/ru/github-publish.md`

## Тесты

В проекте есть тесты для:

- строгой валидации JSON
- orchestration-сценария, включая подсчёт токенов при retry
- web API endpoint'ов и проверки `X-API-TOKEN`
- transcription endpoint'а и валидации входного audio upload
