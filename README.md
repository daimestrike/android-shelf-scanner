# Shelf Scanner

Android-панель оператора для приема построчных JSON-событий от Raspberry Pi по
Bluetooth Classic SPP.

## Запуск

1. Откройте проект в Android Studio.
2. Соберите и установите `app` на Android 8.0 или новее.
3. На Raspberry Pi поднимите RFCOMM/SPP-сервис с UUID
   `00001101-0000-1000-8000-00805F9B34FB`.
4. Каждое сообщение отправляйте отдельной UTF-8 строкой, завершая `\n`.

Для работы без Raspberry Pi нажмите **Запустить Demo Mode**. Тестовые сообщения
будут поступать раз в секунду.

На экране **История** можно сохранить всю историю или отдельную сессию в CSV.
Файл содержит сводные показатели и отдельную строку для каждого Data Matrix и
открывается в Microsoft Excel.

## Команды Raspberry Pi

Кнопки оператора отправляют в тот же RFCOMM-поток JSON, завершённый `\n`:

```json
{"command":"start_session","session_id":"local_...","timestamp":"...","actual_count":24}
{"command":"finish_session","session_id":"local_...","timestamp":"...","actual_count":24}
{"command":"clear_session","session_id":null,"timestamp":"...","actual_count":null}
```

Raspberry Pi должен обрабатывать `start_session`, `finish_session` и
`clear_session`. Незавершённая сессия автоматически сохраняется в Room и
восстанавливается после перезапуска приложения. Фактическое количество товаров
используется для расчёта `Detection Rate = unique_count / actual_count * 100`.
