# Архитектура проекта

## Кратко

Проект — это HTTP API-сервис на Spring Boot, который:

1. принимает JSON-запросы по шагам диалога
2. хранит шаги в SQLite
3. вызывает OpenAI Responses API
4. валидирует ответ как строгий JSON
5. считает токены
6. возвращает клиенту нормализованный ответ

Дополнительно сервис умеет:

- распознавать аудио в текст через OpenAI transcription API
- отправлять Telegram-уведомления
- работать под systemd

## Подробно

### Основные модули

- `config`
  - `AppProperties` — все прикладные настройки
  - `ApiTokenInterceptor` — проверка `X-API-TOKEN`
  - `WebConfig` — подключение interceptor'а
  - `SQLiteDataDirectoryInitializer` — автосоздание каталога SQLite

- `controller`
  - `DialogueController` — endpoint `POST /api/dialogue/step`
  - `AudioController` — endpoint `POST /api/audio/transcribe`
  - `HealthController` — endpoint `GET /health`

- `service`
  - `DialogueOrchestrationService` — основная бизнес-логика диалогов
  - `AudioTranscriptionService` — бизнес-логика распознавания аудио
  - `RetryPromptService` — загрузка retry prompt из ресурсов
  - `TelegramNotificationService` — безопасная отправка уведомлений
  - `OpenAiApiKeyProvider` — единое разрешение OpenAI API key

- `client`
  - `OpenAiClient` — работа с OpenAI Responses API
  - `OpenAiAudioClient` — работа с OpenAI transcription API
  - `TelegramClient` — HTTP-клиент Telegram Bot API

- `repository`
  - `DialogueStepRepository` — доступ к таблице `dialogue_steps`

- `entity`
  - `DialogueStepEntity` — JPA-модель шага диалога

- `validation`
  - `ModelResponseValidator` — строгая проверка JSON-ответа модели

- `exception`
  - `ApiException`
  - `InvalidModelResponseException`
  - `GlobalExceptionHandler`

### Диалоговый поток

#### Вход

Endpoint:

```text
POST /api/dialogue/step
```

Принимает:

```json
{
  "dialogId": "dialog-001",
  "stepId": 1,
  "prompt": "Сделай план действий"
}
```

#### Обработка

1. Проверяется `X-API-TOKEN`, если он включён.
2. Валидируется тело запроса.
3. Если `stepId == 1`, новый шаг начинается без `previous_response_id`.
4. Если `stepId > 1`, ищется предыдущий шаг в SQLite.
5. Выполняется запрос к OpenAI Responses API.
6. Ответ валидируется как строгий JSON.
7. Если JSON невалиден, выполняется один retry с corrective prompt.
8. Считаются токены текущего шага.
9. Вычисляется `dialogTotalTokens`.
10. Итоговый шаг сохраняется в SQLite.
11. JSON возвращается клиенту.

### Поток audio transcription

Endpoint:

```text
POST /api/audio/transcribe
```

Content-Type:

```text
multipart/form-data
```

Поля:

- `file` — обязательный
- `language` — необязательный
- `source` — необязательный
- `originalFileName` — необязательный

#### Обработка

1. Проверяется `X-API-TOKEN`, если он включён.
2. Валидируется наличие файла.
3. Проверяется размер файла.
4. Файл отправляется в OpenAI transcription API.
5. Возвращается JSON:

```json
{
  "success": true,
  "text": "распознанный текст",
  "source": "telegram",
  "language": "ru",
  "originalFileName": "voice.ogg"
}
```

### Ошибки API

Базовый формат ошибок:

```json
{
  "success": false,
  "error": "текст ошибки",
  "details": "дополнительная техническая информация"
}
```

Поле `details` предназначено для разработчика и особенно полезно для диагностики 500-ошибок.

### SQLite

Используется таблица `dialogue_steps`.

Хранится:

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

### Telegram

Если заданы `app.telegram.bot-token` и `app.telegram.chat-id`, сервис отправляет уведомления:

- о входящем шаге диалога
- о финальном ответе диалога
- о критической ошибке
- о запросе на transcription
- о результате transcription

### Systemd

Сервис называется:

```text
agent-elaira-gpt-server.service
```

Скрипты:

- `scripts/service-install.sh`
- `scripts/service-uninstall.sh`
- `scripts/service-status.sh`
- `scripts/service-restart.sh`
- `scripts/service-start.sh`
- `scripts/service-stop.sh`
