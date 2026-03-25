# Справка

## Что это

`dialogue-api` — сервис, который:

1. принимает `POST /api/dialogue/step`
2. при необходимости продолжает диалог через `previous_response_id`
3. валидирует ответ модели как строгий JSON
4. считает токены
5. сохраняет шаг в SQLite
6. возвращает итоговый JSON клиенту

## Что нужно настроить

В файле `src/main/resources/application.properties`:

```properties
server.port=7003

app.security.token=
app.openai.api-key=
app.openai.model=gpt-5.4-mini
app.openai.base-url=https://api.openai.com

app.telegram.bot-token=
app.telegram.chat-id=

spring.datasource.url=jdbc:sqlite:./data/app.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

Ключ OpenAI можно задать:

1. через `app.openai.api-key`
2. через переменную окружения `OPENAI_API_KEY`

Если ключ не задан ни там, ни там, приложение не запустится.

Поддерживаемые значения `model`:

- `gpt-5.4-mini`
- `gpt-5.4-nano`

## Быстрый запуск

Сборка и тесты:

```bash
gradle test
gradle bootJar
```

Запуск:

```bash
OPENAI_API_KEY=your_key gradle bootRun
```

или:

```bash
java -jar build/libs/dialogue-api-0.0.1-SNAPSHOT.jar
```

## Как вызвать API

Первый шаг:

```bash
curl -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -d '{
    "dialogId": "dialog-001",
    "stepId": 1,
    "prompt": "Сделай план действий",
    "model": "gpt-5.4-mini"
  }'
```

Если включён токен приложения:

```bash
curl -X POST http://localhost:7003/api/dialogue/step \
  -H 'Content-Type: application/json' \
  -H 'X-API-TOKEN: your-token' \
  -d '{
    "dialogId": "dialog-001",
    "stepId": 2,
    "prompt": "Продолжи выполнение",
    "model": "gpt-5.4-nano"
  }'
```

Проверка сервиса:

```bash
curl http://localhost:7003/health
```

## Как работает продолжение диалога

- `stepId=1` запускает новый диалог без `previous_response_id`
- `stepId>1` требует существования предыдущего шага в SQLite
- в новый OpenAI-запрос передаётся `openai_response_id` предыдущего шага

Если предыдущий шаг не найден, API вернёт ошибку.

## Что возвращается в ответе

Сервис всегда добавляет:

- верхний уровень: `model`
- внутри `data`: `model`, `requestTokens`, `responseTokens`, `stepTotalTokens`, `dialogTotalTokens`, `previousResponseIdUsed`

Токены считаются по `usage.total_tokens` каждого запроса OpenAI. При `previous_response_id` входные токены могут включать контекст диалога, поэтому стоимость следующих шагов обычно выше.

## Что хранится в базе

Таблица `dialogue_steps` хранит:

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

## Retry при невалидном JSON

- Если первый ответ модели невалиден, выполняется ровно один retry.
- Текст corrective prompt хранится во внешнем файле `src/main/resources/prompts/retry-invalid-json.txt`.
- Если и второй ответ невалиден, сервис возвращает ошибку и пишет критическую запись в лог.

## Telegram

Если заданы:

```properties
app.telegram.bot-token=
app.telegram.chat-id=
```

сервис отправляет уведомления:

1. о входящем запросе
2. о финальном ответе
3. о критической ошибке после второго неудачного ответа

Если Telegram недоступен, основной запрос продолжает работу.

## systemd

Скрипты:

- `scripts/install-service.sh`
- `scripts/uninstall-service.sh`
- `scripts/service-status.sh`
- `scripts/restart-service.sh`

Они управляют сервисом `agent-elaira-gpt-server.service`.

После установки создаётся файл `.env.service` в корне проекта.
Туда удобно положить:

```properties
OPENAI_API_KEY=ваш_ключ_openai
```

Иначе сервис может запуститься без нужной переменной окружения, даже если в обычном терминале она у вас есть.
