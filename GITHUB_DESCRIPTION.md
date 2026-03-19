# Описание репозитория

`dialogue-api` — Spring Boot HTTP API-сервис с SQLite, OpenAI Responses API и OpenAI audio transcription API.

Основные возможности:

- пошаговое ведение диалога через `dialogId / stepId`
- сохранение шагов диалога в SQLite
- строгая валидация JSON-ответа модели
- retry при невалидном JSON
- подсчёт токенов и накопительного `dialogTotalTokens`
- распознавание аудио в текст через `POST /api/audio/transcribe`
- Telegram-уведомления
- systemd-скрипты для установки и управления сервисом

Подходит как backend для Telegram-бота, orchestration-агента или интеграционного AI-сервиса.
