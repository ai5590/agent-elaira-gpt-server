# Подготовка проекта к GitHub

## Что уже подготовлено

- добавлен `.gitignore`
- документация разнесена по `docs/` и `docs/ru/`
- есть краткое описание репозитория в `GITHUB_DESCRIPTION.md`
- есть README
- есть OpenAPI
- есть service-скрипты

## Что желательно сделать перед публикацией

1. Проверить, что в `.env.service` нет реальных секретов
2. Не коммитить `data/`
3. Не коммитить `build/`
4. Убедиться, что README соответствует текущим endpoint'ам
5. Убедиться, что `docs/openapi.yaml` синхронизирован с кодом
6. Прогнать:

```bash
gradle test
gradle bootJar
```

## Что описать в GitHub UI

Краткое описание:

`Spring Boot API для пошагового диалога через OpenAI, SQLite и распознавания аудио в текст.`

Возможные темы:

- spring-boot
- java
- openai
- sqlite
- telegram-bot
- transcription
- api
- systemd
