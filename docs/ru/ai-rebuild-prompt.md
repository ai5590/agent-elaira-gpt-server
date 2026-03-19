# Документ для людей и AI: как заново понять и восстановить проект

## Кратко для людей

Это backend-сервис на Spring Boot для двух задач:

1. пошаговая оркестрация диалога через OpenAI Responses API
2. распознавание аудио в текст через OpenAI transcription API

Сервис хранит шаги диалога в SQLite, умеет считать токены, валидирует ответы модели как строгий JSON, шлёт Telegram-уведомления и защищает API токеном `X-API-TOKEN`, если он включён.

## Подробно для AI / разработчика

Ниже описание в таком виде, чтобы по документу можно было практически заново восстановить проект.

### 1. Технологический стек

- Java 21
- Gradle
- Spring Boot
- Spring Web
- Spring Data JPA
- SQLite
- Jackson
- SLF4J + Logback

### 2. Основные настройки

Файл:

```properties
src/main/resources/application.properties
```

Ключевые настройки:

- `server.port`
- `app.security.token`
- `app.openai.api-key`
- `app.openai.model`
- `app.openai.transcription-model`
- `app.openai.base-url`
- `app.audio.max-file-size-mb`
- `app.telegram.bot-token`
- `app.telegram.chat-id`
- `spring.datasource.url`

### 3. Endpoint'ы

#### Диалог

```text
POST /api/dialogue/step
```

Request:

```json
{
  "dialogId": "dialog-001",
  "stepId": 1,
  "prompt": "Сделай план действий"
}
```

Success:

```json
{
  "success": true,
  "dialogId": "dialog-001",
  "stepId": 1,
  "data": {
    "stepDebug": "...",
    "dialogGoal": "...",
    "dialogTotalTokens": 123,
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

#### Audio transcription

```text
POST /api/audio/transcribe
```

Content-Type:

```text
multipart/form-data
```

Поля:

- `file`
- `language`
- `source`
- `originalFileName`

Success:

```json
{
  "success": true,
  "text": "распознанный текст",
  "source": "telegram",
  "language": "ru",
  "originalFileName": "voice.ogg"
}
```

#### Health

```text
GET /health
```

### 4. Контракт ошибки

Все ошибки должны быть в одном стиле:

```json
{
  "success": false,
  "error": "понятное сообщение",
  "details": "технические детали для разработчика"
}
```

`details` можно делать `null`, если дополнительной технической информации нет.

### 5. Логика диалогового endpoint

Алгоритм:

1. Получить `dialogId`, `stepId`, `prompt`
2. Если `stepId == 1`, `previous_response_id` не передавать
3. Если `stepId > 1`, загрузить из БД предыдущий шаг
4. Если предыдущего шага нет, вернуть ошибку
5. Вызвать OpenAI Responses API
6. Проверить JSON-ответ модели
7. Если JSON невалиден, сделать один retry с отдельным corrective prompt
8. Если и retry невалиден, вернуть ошибку
9. Сложить токены обеих попыток
10. Посчитать `dialogTotalTokens`
11. Сохранить запись в `dialogue_steps`
12. Вернуть JSON клиенту

### 6. Логика audio transcription endpoint

Алгоритм:

1. Получить multipart-файл
2. Проверить, что файл есть и не пустой
3. Проверить лимит размера
4. Отправить файл в `/v1/audio/transcriptions`
5. Взять `text`
6. Вернуть JSON-ответ

### 7. Структура пакетов

- `config`
- `controller`
- `dto`
- `entity`
- `repository`
- `service`
- `client`
- `validation`
- `exception`

### 8. Ключевые классы

- `DialogueApiApplication`
- `AppProperties`
- `ApiTokenInterceptor`
- `DialogueController`
- `AudioController`
- `HealthController`
- `DialogueOrchestrationService`
- `AudioTranscriptionService`
- `OpenAiClient`
- `OpenAiAudioClient`
- `OpenAiApiKeyProvider`
- `TelegramNotificationService`
- `DialogueStepEntity`
- `DialogueStepRepository`
- `ModelResponseValidator`
- `GlobalExceptionHandler`

### 9. Что хранится в БД

Только шаги диалога.

Аудиофайлы в БД не сохраняются.

Бинарные файлы не являются обязательным постоянным артефактом системы.

### 10. Поведение Telegram-уведомлений

Если Telegram настроен:

- отправлять уведомление о шаге диалога
- отправлять уведомление о финальном ответе
- отправлять уведомление об ошибке
- отправлять уведомление о transcription request
- отправлять уведомление о transcription result

Если Telegram недоступен:

- не падать
- только писать ошибку в лог

### 11. Что важно помнить при доработке

- не ломать существующие endpoint'ы
- держать ошибки в едином формате
- не логировать секреты
- не сохранять бинарные аудиофайлы в БД
- использовать тот же OpenAI API key, что и в основном клиенте
- обновлять `README`, `docs/api-format.md`, `docs/openapi.yaml` и русские документы в `docs/ru/`
