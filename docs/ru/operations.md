# Эксплуатация сервиса

## Сборка

```bash
gradle test
gradle bootJar
```

## Локальный запуск

```bash
OPENAI_API_KEY=your_key gradle bootRun
```

## Запуск jar

```bash
java -jar build/libs/dialogue-api-0.0.1-SNAPSHOT.jar
```

## Установка systemd-сервиса

```bash
cd scripts
./service-install.sh
```

Что делает install:

1. проверяет наличие jar
2. создаёт `data/`
3. создаёт `.env.service`, если его ещё нет
4. копирует env-файл в служебный каталог без пробелов в пути
5. создаёт launcher-скрипт
6. пишет unit-файл `agent-elaira-gpt-server.service`
7. включает автозапуск через `systemctl enable`
8. сразу запускает сервис

## Управление сервисом

Старт:

```bash
./service-start.sh
```

Остановка:

```bash
./service-stop.sh
```

Перезапуск:

```bash
./service-restart.sh
```

Статус:

```bash
./service-status.sh
```

Удаление:

```bash
./service-uninstall.sh
```

## Где задавать OpenAI API key для systemd

Файл:

```text
.env.service
```

Пример:

```properties
OPENAI_API_KEY=ваш_ключ
```

После изменения:

```bash
./service-restart.sh
```

## Как смотреть логи

```bash
journalctl -u agent-elaira-gpt-server.service -n 100 --no-pager -l
```

## Типовые проблемы

### Неверный путь к SQLite

Симптом:

```text
path to './data/app.db' ... does not exist
```

Причина:

- сервис установлен из другого каталога проекта
- unit всё ещё указывает на старый workspace

Решение:

1. убедиться, что собирается правильный jar
2. выполнить `./service-install.sh` из актуального проекта
3. проверить launcher в `~/.agent-elaira-gpt-server/`

### Нет OpenAI API key

Симптом:

```text
OpenAI API key is missing
```

Решение:

- заполнить `.env.service`
- выполнить `./service-restart.sh`
