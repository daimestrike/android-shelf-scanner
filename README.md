# Shelf Scanner

Android-приложение для MVP инвентаризации товаров по Data Matrix. Raspberry Pi
управляет камерами, распознаёт коды и отправляет результаты. Android работает
как панель оператора: управляет сессией, принимает JSON, удаляет дубли,
показывает Detection Rate, сохраняет историю и экспортирует CSV для Excel.

## Возможности

- поиск и подключение к Raspberry Pi по Bluetooth Classic;
- отправка команд начала, завершения и очистки сессии;
- приём одного JSON-сообщения на строку;
- дополнительная дедупликация кодов по полю `value`;
- отображение кодов, дублей, ошибок и средней confidence;
- расчёт Detection Rate по фактическому количеству товаров;
- локальная база Room;
- восстановление незавершённой сессии после перезапуска;
- экспорт всей истории или одной сессии в CSV;
- Demo Mode без Raspberry Pi.

Приложение не фотографирует товары и не распознаёт Data Matrix. Это выполняет
Raspberry Pi.

## Требования

### Android

- Android 8.0 или новее;
- Bluetooth Classic;
- разрешения Bluetooth и поиска устройств;
- предварительное сопряжение с Raspberry Pi рекомендуется.

### Raspberry Pi

- Bluetooth Classic с RFCOMM/SPP-сервером;
- стандартный SPP UUID:
  `00001101-0000-1000-8000-00805F9B34FB`;
- постоянное соединение на время сессии;
- UTF-8;
- один JSON-объект на строку;
- каждый JSON обязательно заканчивается символом новой строки `\n`.

BLE, наушники, колонки и произвольные Bluetooth-устройства не подходят.
Android-приложение является SPP-клиентом, поэтому другая сторона должна
работать как RFCOMM/SPP-сервер.

## Сборка Android

Откройте проект в Android Studio или выполните:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK будет создан здесь:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Подключение

1. Включите Bluetooth на Android и Raspberry Pi.
2. Сопрягите устройства через системные настройки.
3. Запустите RFCOMM/SPP-сервер на Raspberry Pi.
4. В Android-приложении нажмите **Найти устройство**.
5. Выберите Raspberry Pi и нажмите **Подключиться**.
6. После статуса **Подключено** откройте экран сканирования.

Важно: сначала запускается SPP-сервер на Raspberry Pi, затем Android выполняет
подключение.

## Протокол Android -> Raspberry Pi

Команды отправляются в тот же RFCOMM-поток как однострочный JSON с `\n`.

Начало:

```json
{"command":"start_session","session_id":"local_1718352000000","timestamp":"2026-06-15T10:00:00","actual_count":24}
```

Завершение:

```json
{"command":"finish_session","session_id":"local_1718352000000","timestamp":"2026-06-15T10:02:00","actual_count":24}
```

Очистка:

```json
{"command":"clear_session","session_id":null,"timestamp":"2026-06-15T10:03:00","actual_count":null}
```

Поддерживаемые команды:

| Команда | Действие Raspberry Pi |
|---|---|
| `start_session` | Начать съёмку и отправку результатов |
| `finish_session` | Остановить съёмку и отправить финальный результат |
| `clear_session` | Сбросить текущую сессию и накопленные данные |

## Протокол Raspberry Pi -> Android

Пример сообщения:

```json
{
  "session_id": "shelf_001",
  "timestamp": "2026-06-15T10:15:00",
  "status": "scanning",
  "total_detected": 24,
  "unique_count": 21,
  "duplicates": 3,
  "errors": 0,
  "camera_id": "cam_1",
  "confidence_avg": 0.92,
  "codes": [
    {
      "value": "010460123456789021ABC123",
      "camera_id": "cam_1",
      "confidence": 0.94,
      "first_seen": "2026-06-15T10:14:55"
    }
  ]
}
```

Допустимые значения `status`:

- `idle`;
- `scanning`;
- `paused`;
- `finished`;
- `error`.

Обязательные поля сообщения:

- `session_id`;
- `timestamp`;
- `status`;
- `total_detected`;
- `unique_count`;
- `duplicates`;
- `errors`.

Неизвестные дополнительные поля Android игнорирует. Поле `codes` может быть
пустым массивом. Каждый объект необходимо сериализовать в одну строку:

```text
{"session_id":"shelf_001",...}\n
```

Не отправляйте форматированный многострочный JSON: перенос строки завершает
сообщение.

## Минимальный SPP-сервер Raspberry Pi

Ниже приведён ориентир для проверки канала. Для основной реализации обработку
команд и распознавание нужно подключить вместо тестового ответа.

Установите поддержку Bluetooth:

```bash
sudo apt update
sudo apt install -y bluetooth bluez python3-bluez
sudo systemctl enable --now bluetooth
```

Пример Python-сервера:

```python
import bluetooth
import json
from datetime import datetime

SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

server = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
server.bind(("", bluetooth.PORT_ANY))
server.listen(1)

port = server.getsockname()[1]
bluetooth.advertise_service(
    server,
    "ShelfScannerSPP",
    service_id=SPP_UUID,
    service_classes=[SPP_UUID, bluetooth.SERIAL_PORT_CLASS],
    profiles=[bluetooth.SERIAL_PORT_PROFILE],
)

print("RFCOMM channel:", port)
print("Waiting for Android RFCOMM connection...")
client, address = server.accept()
print("Connected:", address)

buffer = b""

try:
    while True:
        chunk = client.recv(4096)
        if not chunk:
            break
        buffer += chunk

        while b"\n" in buffer:
            line, buffer = buffer.split(b"\n", 1)
            if not line.strip():
                continue

            command = json.loads(line.decode("utf-8"))
            print("Command:", command)

            response = {
                "session_id": command.get("session_id") or "test_session",
                "timestamp": datetime.now().isoformat(timespec="seconds"),
                "status": (
                    "finished"
                    if command.get("command") == "finish_session"
                    else "scanning"
                ),
                "total_detected": 1,
                "unique_count": 1,
                "duplicates": 0,
                "errors": 0,
                "camera_id": "cam_1",
                "confidence_avg": 0.95,
                "codes": [
                    {
                        "value": "010460123456789021TEST001",
                        "camera_id": "cam_1",
                        "confidence": 0.95,
                        "first_seen": datetime.now().isoformat(timespec="seconds"),
                    }
                ],
            }

            payload = json.dumps(response, ensure_ascii=False) + "\n"
            client.send(payload.encode("utf-8"))
finally:
    client.close()
    server.close()
```

На разных версиях Raspberry Pi OS может потребоваться отдельно опубликовать
Serial Port Profile через BlueZ. Проверить доступные сервисы можно командой:

```bash
sdptool browse local
```

## Detection Rate

Перед тестом оператор вводит фактическое число товаров на полке. Приложение
рассчитывает:

```text
Detection Rate = уникальные коды / фактическое количество товаров * 100%
```

Пример: найдено 19 уникальных кодов из 20 товаров — Detection Rate равен 95%.
Значение выше 100% означает ошибочный ground truth или ложные распознавания и
намеренно не ограничивается приложением.

## Локальное хранение

Room хранит:

- завершённые и вручную сохранённые сессии;
- список Data Matrix;
- показатели сессии;
- фактическое количество;
- Detection Rate;
- черновик незавершённой сессии.

После аварийного закрытия незавершённая сессия восстанавливается при следующем
запуске приложения.

## CSV и Excel

На экране **История** доступны:

- экспорт всей истории;
- экспорт выбранной сессии.

CSV создаётся в UTF-8 с BOM и разделителем `;`. В нём есть показатели сессии и
отдельная строка для каждого Data Matrix. Формат открывается в Microsoft Excel.

## Demo Mode

На экране подключения нажмите **Запустить Demo Mode**. Приложение будет раз в
секунду генерировать тестовые JSON-сообщения. Bluetooth и Raspberry Pi не нужны.

## Диагностика Bluetooth

Ошибка вида:

```text
read failed, socket might closed or timeout, read ret: -1
```

обычно означает одно из следующего:

- на выбранном устройстве не запущен RFCOMM/SPP-сервер;
- устройство не поддерживает SPP;
- Android и Raspberry Pi не сопряжены;
- сервер использует другой RFCOMM-канал или UUID;
- сервер закрыл сокет сразу после подключения;
- Bluetooth-служба BlueZ не опубликовала Serial Port Profile.

Проверьте:

1. Raspberry Pi видна в системных настройках Bluetooth.
2. Устройства сопряжены.
3. SPP-сервер запущен до подключения Android.
4. Сервер продолжает слушать сокет после `accept()`.
5. Все сообщения завершаются `\n`.
6. Используется UUID `00001101-0000-1000-8000-00805F9B34FB`.

Проверять приложение на наушниках, колонке или произвольном Bluetooth-устройстве
бессмысленно: они используют другие Bluetooth-профили.

## Ограничения MVP

- Bluetooth-интеграцию необходимо проверить на физической связке Android и
  Raspberry Pi;
- автоматическое переподключение пока отсутствует;
- Android подтверждает запись команды в сокет, но не ждёт отдельный ACK от Pi;
- WMS и облачная синхронизация не реализованы;
- распознавание Data Matrix выполняется только на Raspberry Pi.
