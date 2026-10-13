# Double Power Key

![Android](https://img.shields.io/badge/Android-7.1%2B%20(API%2025--35)-3DDC84?logo=android&logoColor=white)
![OS](https://img.shields.io/badge/ROM-OriginOS%20%7C%20FuntouchOS-4285F4)
![UI](https://img.shields.io/badge/UI-Android%2013%20Material%20You-A8C7FA)
![Root](https://img.shields.io/badge/Root%20%2F%20Shizuku-Not%20Required-brightgreen)
![APK Size](https://img.shields.io/badge/APK%20Size-~30%20KB-informational)

**Double Power Key** (`com.vivotomirpay.stub`) — лёгкое приложение-заглушка для смартфонов **Vivo** и **iQOO** (с прошивками **OriginOS** и **FuntouchOS**), позволяющее переназначить **двойное нажатие кнопки питания** со стандартного китайского кошелька **Vivo Wallet** на:

- запуск **Mir Pay**, **СБПэй**, **Vivo Wallet (ключ)** или **любого установленного приложения**;
- переключение **фонарика**;
- запуск **Circle to Search** (как в MiCts);

Работает **без Root-прав**, **без Shizuku** и **без удаления системных приложений**.

> Проект основан на коде `VivoToMirPay`. Имя и список возможностей расширены по
> обратной связи из сообщества Vivo / iQOO.

---

## ✨ Основные возможности

- ⚡ **Мгновенный запуск без промежуточных окон** — при двойном нажатии кнопки питания прозрачная [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) без анимации перенаправляет вызов в выбранное приложение / спец-функцию и сразу закрывается (`finish()`).
- 💳 **Не сбивает приложение бесконтактной оплаты по умолчанию** — заглушка не регистрирует фиктивный HCE-сервис (`HostApduService`), поэтому **Mir Pay** остаётся основным платёжным средством NFC в системе.
- 🔦 **Пресет «Фонарик»** — спец-значение `special:torch`. При двойном нажатии кнопки питания вспышка камеры переключается вкл/выкл. Состояние хранится в `SharedPreferences` и синхронизируется с фактическим состоянием `CameraManager` через `setTorchMode`.
- 🔍 **Пресет «Circle to Search»** — спец-значение `special:circle_to_search`. Тот же механизм, что в проекте [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS): приложение напрямую вызывает скрытый системный API `IVoiceInteractionManagerService.showSessionFromSession()` через reflection + библиотеку `org.lsposed.hiddenapibypass`, передавая Bundle с ключами `omni.entry_point`, `invocation_time_ms`, `micts_trigger`. Система воспринимает это как жест Circle to Search и открывает ассистента по умолчанию (Google). **Не требует root, не требует accessibility, не требует никаких разрешений.**
- 🎯 **Гибкий выбор приложения для запуска**:
  - Быстрые пресеты в один тап: **Mir Pay** (`ru.nspk.mirpay`), **СБПэй** (`ru.nspk.sbpay`), **Vivo Wallet — NFC-ключ** (`com.vivo.wallet` / `NewSwipeActivity`), **Фонарик**, **Circle to Search**, **Gemini**.
  - Выбор **любого установленного приложения** из встроенного списка **с динамическим поиском** по названию или packageName.
  - Ввод произвольного `packageName` или `special:*` вручную.
- 🛡️ **Безопасный журнал** — лог последнего срабатывания в `PrefsHelper.saveTriggerLog` обрезается до 1000 символов, а значения каждого extra — до 100 символов, чтобы произвольный отправитель интента не мог раздуть `SharedPreferences` и UI заглушки.
- 🧭 **Понятные сообщения об ошибках** — `SwipeEntryActivity` не глотает исключения молча: причина сбоя пишется в журнал и показывается тостом, а приоритетным остаётся `getLaunchIntentForPackage`. Если у пакета несколько MAIN-Activity, выбирается та, что с категорией `LAUNCHER`, а не «первая попавшаяся».
- 🎨 **Интерфейс в стиле Android 13 (Material You / Material 3)** — тёмная палитра, индикатор текущего обработчика по умолчанию, кнопка вызова системного диалога выбора («Всегда») и диагностический журнал последнего срабатывания. Над каждой группой кнопок есть короткая пояснительная подсказка, чтобы с первого взгляда было понятно, что и зачем делает каждая кнопка.
- 📳 **Опциональный виброотклик** — настраиваемая короткая вибрация (`35 мс`) в момент перехвата двойного нажатия.
- 🔒 **`android:allowBackup="false"`** — бэкапить здесь нечего (только выбранный пакет и лог), а восстановленный бэкап мог бы подменить целевой пакет.

---

## 🔍 Как это работает

На смартфонах Vivo / iQOO при двойном нажатии кнопки питания система формирует и отправляет неявный (implicit) системный `Intent`:

```text
Action:   vivo.intent.action.NFC_BUS_SWINGSPLASH
Category: android.intent.category.DEFAULT
Extras:   lb_from = "2"
```

По умолчанию в китайских прошивках OriginOS этот интент обрабатывается системным кошельком `com.vivo.wallet` (`com.vivo.pay.swing.activity.NewSwipeActivity`) или `com.vivo.seclient` (`com.vivo.pay.fexswipe.activity.NewSwipeActivity`).

**Double Power Key** объявляет экспортированную Activity [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) с тем же `<intent-filter>` и системными метаданными Vivo в [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):

```xml
<meta-data android:name="com.vivo.wallet.support.typeswing" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.SupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.CommonSupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.supportVersion" android:value="2" />
<meta-data android:name="com.vivo.pay.nfc.supportVersion" android:value="1" />
```

> ⚠️ **Дублирование метаданных**. Эти же пять `<meta-data>` продублированы **внутри самой `SwipeEntryActivity`**. Некоторые сборки OriginOS / FuntouchOS ищут `com.vivo.wallet.*` именно на самом компоненте-обработчике, а не на `<application>`. Дублирование повышает надёжность перехвата на разных прошивках Vivo — проверьте на своём устройстве.

Благодаря этому система Android распознаёт **Double Power Key** как штатный обработчик двойного нажатия кнопки питания и выводит стандартное системное окно выбора приложения, где можно выбрать **Double Power Key** и нажать **«Всегда» (По умолчанию)**.

---

## 🚀 Установка и настройка

### 1. Установка APK
Скачайте и установите готовый подписанный APK из раздела `releases/` (или соберите сами — см. ниже) на смартфон.

### 2. Включение двойного нажатия в настройках Vivo
Убедитесь, что в настройках смартфона включён быстрый вызов кошелька по двойному нажатию кнопки питания:
- **OriginOS / FuntouchOS:** `Настройки` → `Другие сети и подключения` → `NFC` → **«Дважды нажмите кнопку питания»** (или в настройках `Vivo Wallet` → `Быстрая оплата по двойному нажатию кнопки питания`).

### 3. Выбор запускаемого приложения / спец-функции
1. Откройте приложение **Double Power Key** с рабочего стола.
2. В карточке **«Что запускает двойное нажатие»** выберите:
   - пресет в один тап: **Mir Pay**, **СБПэй**, **Wallet (Ключ)**, **Фонарик**, **Circle to Search**, **Gemini**; либо
   - нажмите **«Все приложения…»** — откроется диалог **с динамическим поиском** по названию или packageName; либо
   - введите `packageName` или `special:*` вручную в поле.
3. Нажмите **«Сохранить»**.

### 4. Назначение обработчиком по умолчанию
1. В карточке **«Обработчик кнопки питания Vivo»** нажмите **«Вызвать NFC_BUS_SWINGSPLASH (Выбрать «Всегда»)»** — либо просто дважды нажмите кнопку питания на корпусе телефона.
2. В появившемся системном окне выбора приложения отметьте галочку **«Всегда» / «Использовать по умолчанию»** и выберите **Double Power Key**.
3. Статусная плашка вверху приложения сменится на зелёную:
   > `✓ Двойное нажатие питания: эта заглушка установлена по умолчанию`

---

## 📁 Структура проекта

```text
double-power-key/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml                  # Манифест: метаданные Vivo на уровне <application> + дубликат внутри SwipeEntryActivity
│       ├── java/com/vivotomirpay/stub/
│       │   ├── MainActivity.java               # Интерфейс настроек Material You, пресеты, динамический поиск, подсказки
│       │   ├── SwipeEntryActivity.java          # Перехватчик двойного нажатия: фонарик / Circle to Search / Gemini / запуск пакета
│       │   └── PrefsHelper.java                # SharedPreferences с ограничением длины лога и хранением состояния фонарика
│       └── res/
│           ├── drawable/ic_launcher.xml         # Векторная иконка приложения
│           └── values/
│               ├── strings.xml                  # app_name = "Double Power Key"
│               └── styles.xml                   # Темы приложения и прозрачной Activity без анимации
├── build.gradle
├── settings.gradle                              # pluginManagement + dependencyResolutionManagement
├── gradle/wrapper/                              # Gradle Wrapper
├── gradlew / gradlew.bat                        # POSIX / Win запуск Gradle Wrapper
├── build_apk.sh                                 # POSIX скрипт сборки (debug / release)
├── build_apk.ps1                                # PowerShell скрипт сборки (debug / release)
└── README.md
```

---

## 🔧 Сборка APK

В репозитории есть **Gradle Wrapper**, поэтому Gradle ставить не обязательно — `gradlew` сам скачает нужную версию при первом запуске.

### Требования
- **JDK 17+** (JDK 21 тоже работает)
- **Android SDK** с компонентами:
  - `platforms;android-35` (или `android-36`)
  - `build-tools;35.0.0`
  - Переменная окружения `ANDROID_HOME` должна указывать на корень Android SDK, либо создайте файл `local.properties` со строкой `sdk.dir=/path/to/AndroidSDK`.

### Сборка через Gradle напрямую

```bash
# Release (по умолчанию подписывается debug-ключом — этого достаточно для локальной установки)
./gradlew assembleRelease

# Debug
./gradlew assembleDebug
```

Готовый APK появится по пути:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

### Сборка через вспомогательные скрипты

**Linux / macOS:**
```bash
./build_apk.sh                # release, debug-подпись
./build_apk.sh debug          # debug
./build_apk.sh release /path/keystore.jks STORE_PASS keyAlias KEY_PASS   # release со своим ключом
```

**Windows (PowerShell):**
```powershell
.\build_apk.ps1                          # release, debug-подпись
.\build_apk.ps1 -Debug                  # debug
.\build_apk.ps1 -Sign C:\keys\release.jks -StorePass **** -Alias release -KeyPass ****
```

### Подпись собственным ключом (опционально)

Если вы хотите, чтобы release-сборка подписывалась вашим ключом, задайте переменные окружения перед запуском Gradle:

```bash
export VVTM_KEYSTORE=/path/to/release.jks
export VVTM_KEYSTORE_PASSWORD=******
export VVTM_KEY_ALIAS=release
export VVTM_KEY_PASSWORD=******
./gradlew assembleRelease
```

Без них `app/build.gradle` использует debug-ключ по умолчанию (`debug.keystore`, пароль `android`, alias `androiddebugkey`). Это удобно для локальной установки, но не подойдёт для публикации в Google Play.

---

## 🔒 Безопасность

- **`android:allowBackup="false"`** — бэкапить здесь нечего (только выбранный packageName и короткий лог), а восстановленный бэкап мог бы подменить целевой пакет на другом устройстве.
- **Экспортированная `SwipeEntryActivity` принимает интенты от любого приложения**, поэтому:
  - значения `extras` пишутся в `SharedPreferences` только как обрезанные строки (≤ 100 символов на значение, ≤ 1000 символов на весь лог);
  - `MainActivity` выводит ровно тот же лог в `TextView`, поэтому ограничение длины также защищает UI от раздувания.
- **Все исключения в `SwipeEntryActivity` пишутся в журнал и показываются тостом** — никаких тихих `catch (Exception ignored)` в пользовательском коде.

---

## 📄 Лицензия

Проект распространяется свободно в ознакомительных и личных целях (MIT License).
