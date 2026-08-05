<div align="center">

<img src=".github/readme-images/app-icon.png" width="96" alt="RU Anime Extensions">

# RU Anime Extensions

**Каталог русскоязычных аниме-расширений для Aniyomi, Anikku и совместимых приложений**

[![Build](https://github.com/Sindrow33/ru-anime-extensions/actions/workflows/build_push.yml/badge.svg)](https://github.com/Sindrow33/ru-anime-extensions/actions/workflows/build_push.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![License](https://img.shields.io/github/license/Sindrow33/ru-anime-extensions?color=blue)](LICENSE)
[![Last commit](https://img.shields.io/github/last-commit/Sindrow33/ru-anime-extensions)](https://github.com/Sindrow33/ru-anime-extensions/commits/main)

[![Добавить в Aniyomi](https://img.shields.io/badge/Добавить_в-Aniyomi-2F80ED?style=for-the-badge)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json)
[![Добавить в Anikku](https://img.shields.io/badge/Добавить_в-Anikku-8B5CF6?style=for-the-badge)](https://intradeus.github.io/http-protocol-redirector/?r=anikku://add-repo?url=https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json)

</div>

---

## 📖 О проекте

**RU Anime Extensions** — независимый репозиторий расширений, добавляющих русскоязычные источники аниме в приложения, совместимые с форматом расширений Aniyomi.

Расширения позволяют искать тайтлы, открывать каталоги источников и получать доступ к доступным на них сериям непосредственно из приложения.

### Возможности

- 🇷🇺 подборка русскоязычных аниме-источников;
- 📦 установка и обновление расширений через единый репозиторий;
- 🔄 автоматическая сборка и публикация APK через GitHub Actions;
- 🧩 отдельный модуль для каждого источника;
- 🛠 открытый исходный код на Kotlin;
- 📱 совместимость с Aniyomi, Anikku и некоторыми их форками.

> [!IMPORTANT]
> Репозиторий содержит только программные расширения-клиенты и **не хранит видео, изображения или другой медиаконтент**.

---

## 🚀 Установка

### Быстрая установка

Нажмите одну из кнопок:

<div align="center">

[![Aniyomi](https://img.shields.io/badge/Установить_репозиторий-Aniyomi-2F80ED?style=for-the-badge)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json)
[![Anikku](https://img.shields.io/badge/Установить_репозиторий-Anikku-8B5CF6?style=for-the-badge)](https://intradeus.github.io/http-protocol-redirector/?r=anikku://add-repo?url=https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json)

</div>

### Ручное добавление

1. Откройте **Aniyomi**, **Anikku** или совместимое приложение.
2. Перейдите в **Настройки → Обзор → Репозитории расширений**.
3. Нажмите **Добавить**.
4. Вставьте адрес репозитория:

```text
https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json
```

5. Подтвердите добавление репозитория.
6. Откройте раздел аниме-расширений и установите нужные источники.

> [!TIP]
> Если каталог не появился сразу, обновите список расширений или перезапустите приложение.

---

## 📦 Доступные источники

В репозитории представлены расширения для следующих русскоязычных источников:

| | | |
|:---|:---|:---|
| AMD | AniBaza | AnidubDigital |
| Anidub | AniFilm | AniJoy |
| AniLiberty | Animaunt | AnimeGO |
| Animelib | Animevost | AniStar |
| Astar | DubClub | FindAnime |
| JamClub | Jut.su | OtakuJoy |
| PlagueStudios | Ruchime | Senu |
| SHIZA Project | Smotret-Anime | YummyAnime |

Актуальный список, версии и адреса источников находятся в файле [`index.min.json`](https://raw.githubusercontent.com/Sindrow33/ru-anime-extensions/repo/index.min.json).

> [!WARNING]
> В каталоге также могут присутствовать глобальные источники с контентом **18+**. Учитывайте возрастные ограничения и законодательство вашей страны.

---

## 📥 Ручная загрузка APK

Если приложение не поддерживает подключение пользовательских репозиториев, расширения можно установить вручную.

Все собранные APK находятся в ветке [`repo`](https://github.com/Sindrow33/ru-anime-extensions/tree/repo/apk):

<div align="center">

[![Открыть каталог APK](https://img.shields.io/badge/Открыть_каталог-APK-22C55E?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Sindrow33/ru-anime-extensions/tree/repo/apk)

</div>

При ручной установке Android может запросить разрешение на установку приложений из неизвестных источников.

---

## 🏗️ Структура проекта

```text
ru-anime-extensions/
├── src/
│   ├── ru/                 # Русскоязычные расширения
│   └── all/                # Глобальные расширения
├── core/                   # Общая логика
├── lib/                    # Общие библиотеки и экстракторы
├── lib-multisrc/           # Компоненты для нескольких источников
├── gradle/                 # Конфигурация сборки
├── .github/
│   ├── workflows/          # CI/CD
│   ├── scripts/            # Скрипты сборки и публикации
│   └── ISSUE_TEMPLATE/     # Шаблоны обращений
└── settings.gradle.kts
```

Ветка `main` содержит исходный код, а ветка `repo` — собранные APK и индекс репозитория.

---

## 🛠️ Локальная сборка

### Требования

- Git;
- JDK 17;
- Android SDK;
- подключение к интернету для загрузки Gradle-зависимостей.

### Подготовка

```bash
git clone https://github.com/Sindrow33/ru-anime-extensions.git
cd ru-anime-extensions
```

### Сборка отдельного расширения

Например, debug-сборка AnimeGO:

```bash
./gradlew :src:ru:animego:assembleDebug
```

Для Windows:

```powershell
gradlew.bat :src:ru:animego:assembleDebug
```

Название Gradle-модуля соответствует пути расширения:

```text
src/ru/animego → :src:ru:animego
```

### Проверка форматирования

```bash
./gradlew spotlessCheck
```

Автоматическое исправление форматирования:

```bash
./gradlew spotlessApply
```

---

## 🤝 Как помочь проекту

Любые полезные изменения приветствуются:

- исправление неработающих источников;
- обновление доменов;
- добавление новых источников;
- улучшение парсеров и видеоэкстракторов;
- исправление ошибок;
- улучшение документации.

### Порядок работы

1. Создайте fork репозитория.
2. Создайте отдельную ветку:

   ```bash
   git checkout -b fix/source-name
   ```

3. Внесите изменения.
4. Соберите и протестируйте расширение.
5. Проверьте форматирование.
6. Создайте Pull Request с описанием изменений.

При изменении расширения не забудьте обновить его `extVersionCode` в `build.gradle`.

---

## 🐞 Ошибки и предложения

Перед созданием обращения:

- обновите приложение;
- обновите установленные расширения;
- проверьте, открывается ли сайт источника в браузере;
- убедитесь, что проблема относится именно к расширению;
- поищите похожее обращение среди существующих Issues.

Для сообщений об ошибках и запросов новых источников используйте подготовленные шаблоны:

<div align="center">

[![Создать обращение](https://img.shields.io/badge/Создать-Issue-EA4AAA?style=for-the-badge&logo=github)](https://github.com/Sindrow33/ru-anime-extensions/issues/new/choose)

</div>

Пожалуйста, указывайте:

- название и версию расширения;
- название и версию приложения;
- версию Android;
- пошаговый сценарий воспроизведения;
- ожидаемое и фактическое поведение;
- логи или скриншоты, если они доступны.

Ошибки самого приложения следует отправлять разработчикам соответствующего приложения, а не в этот репозиторий.

---

## ⚠️ Отказ от ответственности

Этот проект:

- не хранит и не распространяет медиаконтент;
- не связан с владельцами поддерживаемых сайтов;
- не контролирует доступность и содержимое внешних источников;
- не связан официально с разработчиками Aniyomi или Anikku;
- предоставляется исключительно как программный каталог расширений.

Все товарные знаки, названия и материалы принадлежат их соответствующим владельцам.

Пользователь самостоятельно несёт ответственность за соблюдение законодательства своей страны и правил используемых сервисов.

---

## 📄 Лицензия

Проект распространяется на условиях [Apache License 2.0](LICENSE).

```text
Licensed under the Apache License, Version 2.0.
You may not use this project except in compliance with the License.
```

---

<div align="center">

**Если репозиторий оказался полезен — поставьте ему ⭐**

[Сообщить об ошибке](https://github.com/Sindrow33/ru-anime-extensions/issues/new/choose)
·
[Исходный код](https://github.com/Sindrow33/ru-anime-extensions)
·
[Скачать APK](https://github.com/Sindrow33/ru-anime-extensions/tree/repo/apk)

</div>