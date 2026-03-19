# Формат API

## Endpoint'ы

### `POST /api/dialogue/step`

Создаёт новый шаг диалога или продолжает существующий.

Тело запроса:

```json
{
  "dialogId": "dialog-001",
  "stepId": 1,
  "prompt": "Сделай план действий"
}
```

Успешный ответ:

```json
{
  "success": true,
  "dialogId": "dialog-001",
  "stepId": 1,
  "data": {
    "stepDebug": "На этом шаге анализируется входной запрос и формируется список действий",
    "dialogGoal": "Выполнить задачу пользователя по шагам через набор формализованных действий",
    "dialogTotalTokens": 900,
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
}
```

Ответ при ошибке:

```json
{
  "success": false,
  "error": "текст ошибки",
  "details": "дополнительная техническая информация"
}
```

### `GET /health`

Ответ:

```json
{
  "success": true,
  "status": "UP"
}
```

### `POST /api/audio/transcribe`

Распознаёт аудиофайл в текст через OpenAI.

Content-Type:

```text
multipart/form-data
```

Поля формы:

- `file` — обязательный аудиофайл
- `language` — необязательная строка
- `source` — необязательная строка
- `originalFileName` — необязательная строка

Успешный ответ:

```json
{
  "success": true,
  "text": "распознанный текст",
  "source": "telegram",
  "language": "ru",
  "originalFileName": "voice.ogg"
}
```

Ответ при ошибке:

```json
{
  "success": false,
  "error": "текст ошибки",
  "details": "дополнительная техническая информация"
}
```

## Заголовок авторизации приложения

Если задан `app.security.token`, каждый запрос обязан содержать:

```text
X-API-TOKEN: your-token
```

Если заголовок отсутствует или неверен, API возвращает `401 Unauthorized`.

## Примеры curl

Без токена приложения:

```bash
curl -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-001",
    "stepId": 1,
    "prompt": "Сделай план действий"
  }'
```

С `X-API-TOKEN`:

```bash
curl -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -H 'X-API-TOKEN: your-token' \
  -d '{
    "dialogId": "dialog-001",
    "stepId": 2,
    "prompt": "Продолжи выполнение"
  }'
```

Проверка здоровья сервиса:

```bash
curl http://localhost:7003/health
```

Распознавание аудио:

```bash
curl -X POST http://localhost:7003/api/audio/transcribe \
  -H "X-API-TOKEN: my-secret-token" \
  -F "file=@voice.ogg" \
  -F "language=ru" \
  -F "source=telegram" \
  -F "originalFileName=voice.ogg"
```
