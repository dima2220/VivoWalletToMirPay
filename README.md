# Double Power Key

![Android](https://img.shields.io/badge/Android-7.1%2B%20(API%2025--35)-3DDC84?logo=android&logoColor=white)
![OS](https://img.shields.io/badge/ROM-OriginOS%20%7C%20FuntouchOS-4285F4)
![UI](https://img.shields.io/badge/UI-Material%20You%203%20(Dynamic%20Color)-A8C7FA)
![Root](https://img.shields.io/badge/Root%20%2F%20Shizuku-Not%20Required-brightgreen)
![APK Size](https://img.shields.io/badge/APK%20Size-~75%20KB-informational)
![Languages](https://img.shields.io/badge/Languages-Русский%20%7C%20English-blue)

**Double Power Key** (`com.vivotomirpay.stub`) — лёгкое приложение для смартфонов **Vivo** и **iQOO** (прошивки **OriginOS** и **FuntouchOS**), которое переназначает **двойное нажатие кнопки питания** со стандартного китайского кошелька **Vivo Wallet** на:

- запуск **Mir Pay**, **СБПэй** или **любого установленного приложения**;
- переключение **фонарика**;
- **запуск камеры** (в том числе с заблокированного экрана);
- **запуск/отключение VPN** — **Happ** или **incy** (без root, официальные механизмы приложений);
- **Circle to Search** (как в MiCTS);
- **настраиваемое меню действий** — панель, куда можно добавить и действия, и приложения.

Работает **без Root-прав**, **без Shizuku** и **без удаления системных приложений**. Интерфейс — **Material You 3** с динамическими цветами обоев (Android 12+), на старых версиях — базовая палитра Material 3. Языки: **русский + английский** (следует системной настройке; на Android 13+ язык можно задать отдельно для приложения).
---

## ✨ Что нового в 2.1.4

- 📱 **Редизайн Material You 3** — карточки, чипы, переключатели, ползунки, диалоги; динамические цвета из обоев на Android 12+ (палитра резолвится из системных ресурсов и подбирается по светлоте — корректно на любых прошивках), статичная палитра M3 на Android 11 и старше; светлая и тёмная темы следуют системе.
- 🌃 **Отдельное действие на заблокированном экране** — только **фонарик** или **камера** (камера открывается через secure-интент, как по штатному ярлыку локскрина). Настройка **отключается** — и на локскрине выполняется то же действие, что и на разблокированном.
- 📷 **Действие «Камера»** — системный интент камеры; у большинства камер включён `showWhenLocked`, поэтому съёмка открывается прямо с экрана блокировки, как по штатному ярлыку.
- 🛡 **Действие «VPN»** — переключает подключение VPN-клиента **в фоне, без открытия приложений**:
  - **Happ** (`su.happ.proxyutility`, старый пакет `com.happproxy` тоже поддержан) — через полупрозрачный системный перехватчик deeplink **`happ://toggle_without_ui`** (официальный путь «без интерфейса»: перехватчик принимает только хосты `*_without_ui`) и broadcast в экспортированный виджет-приёмник `*.action.widget.click`;
  - **incy** (`llc.itdev.incy`) — основной путь: broadcast **`llc.itdev.incy.TOGGLE`** в экспортированный `VpnIntentReceiver` (полностью в фоне); резервный — скрытая shortcut-команда `VOICE_TOGGLE` (тема без видимого UI);
  - выбор клиента: **Авто** (первый установленный) / **Happ** / **incy**; показывается статус «VPN активен / не активен» (публичный API `TRANSPORT_VPN`).
- 📳 **Настройки вибрации** — вкл/выкл, **длительность импульса** (20–500 мс) и **паттерн**: один импульс / двойной / длинный.
- 🗂 **Меню действий** — если действием выбрано «Меню действий», двойное нажатие открывает настраиваемую панель, которая **плавно выезжает снизу** (bottom sheet в стиле M3): иконки приложений в полном цвете, компактные пункты. Состав настраивается в отдельном экране «Настройка меню»: «Добавить действие…» / «Добавить приложение…» (с поиском), стрелки порядка, крестик удаления. Меню **работает поверх экрана блокировки**: фонарик, камера и VPN переключаются без разблокировки.
- 🌍 **Английский язык** — вся строка ресурсов переведена: `values` (русский) + `values-en`; на Android 13+ доступен выбор языка приложения.
- 🔊 и всё из версии 1.3: пресеты Mir Pay / СБПэй / ключ Vivo Wallet / Circle to Search, выбор любого приложения с поиском, журнал последнего срабатывания, вызов системного диалога «Всегда».

---

## ✨ Основные возможности (как и раньше)

- ⚡ **Мгновенный запуск без промежуточных окон** — прозрачная [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) без анимации перенаправляет вызов в выбранное приложение / спец-функцию и сразу закрывается.
- 💳 **Не сбивает приложение бесконтактной оплаты** — заглушка не регистрирует фиктивный HCE-сервис, поэтому **Mir Pay** остаётся основным платёжным средством NFC в системе.
- 🔦 **Пресет «Фонарик»** — спец-значение `special:torch`. Вспышка переключается вкл/выкл, состояние синхронизируется через `CameraManager.setTorchMode`.
- 🔍 **Пресет «Circle to Search»** — спец-значение `special:circle_to_search`. Тот же механизм, что в проекте [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS): прямой вызов скрытого системного API `IVoiceInteractionManagerService.showSessionFromSession()` через reflection + библиотеку `org.lsposed.hiddenapibypass`. **Не требует root, не требует accessibility, не требует разрешений.**
- 🎯 **Гибкий выбор действия** — быстрые пресеты в один тап, список всех приложений с динамическим поиском, ручной ввод `packageName`.
- 🛡 **Безопасный журнал** — лог последнего срабатывания обрезается до 1000 символов, значения extras — до 100.
- 🧭 **Понятные сообщения об ошибках** — все сбои пишутся в журнал вместо тихих `catch`.
- 🔒 **`android:allowBackup="false"`** — бэкапить здесь нечего, а восстановленный бэкап мог бы подменить целевой пакет.

---

## 🔍 Как это работает

На смартфонах Vivo / iQOO при двойном нажатии кнопки питания система формирует и отправляет неявный системный `Intent`:

```text
Action:   vivo.intent.action.NFC_BUS_SWINGSPLASH
Category: android.intent.category.DEFAULT
Extras:   lb_from = "2"
```

По умолчанию в китайских прошивках OriginOS этот интент обрабатывается системным кошельком `com.vivo.wallet` (`com.vivo.pay.swing.activity.NewSwipeActivity`) или `com.vivo.seclient`.

**Double Power Key** объявляет экспортированную Activity [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) с тем же `<intent-filter>` и системными метаданными Vivo в [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):

```xml
<meta-data android:name="com.vivo.wallet.support.typeswing" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.SupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.CommonSupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.supportVersion" android:value="2" />
<meta-data android:name="com.vivo.pay.nfc.supportVersion" android:value="1" />
```

> ⚠️ Эти же метаданные продублированы внутри самой `SwipeEntryActivity` — некоторые сборки OriginOS / FuntouchOS ищут их именно на компоненте-обработчике.

Дальше заглушка определяет состояние экрана (`KeyguardManager.isKeyguardLocked()`):

- **экран разблокирован** → выполняется действие из карточки «Действие двойного нажатия»;
- **экран заблокирован, настройка «Отдельное действие» включена** → выполняется действие из карточки «Заблокированный экран»;
- **экран заблокирован, настройка выключена** → выполняется то же действие, что и на разблокированном экране.

Исполнение действий вынесено в общий класс [`ActionRunner`](app/src/main/java/com/vivotomirpay/stub/ActionRunner.java), которым пользуются и перехватчик, и меню действий. Переключение VPN инкапсулировано в [`VpnController`](app/src/main/java/com/vivotomirpay/stub/VpnController.java).

---

## 🚀 Установка и настройка

### 1. Установка APK
Установите готовый подписанный APK (или соберите сами — см. ниже).

### 2. Включение двойного нажатия в настройках Vivo
- **OriginOS / FuntouchOS:** `Настройки` → `Другие сети и подключения` → `NFC` → **«Дважды нажмите кнопку питания»** (или в настройках `Vivo Wallet`).

### 3. Настройка действий
1. Откройте **Double Power Key**.
2. В карточке **«Действие двойного нажатия»** нажмите на строку «Что запускать» (или чип-пресет) и выберите приложение или действие.
3. В карточке **«Заблокированный экран»** включите отдельное действие и выберите его (по умолчанию фонарик). Не нужно — просто выключите переключатель.
4. В карточке **«Вибрация»** настройте отклик, в **«VPN-действие»** — клиента (Happ / incy / авто).
5. В карточке **«Меню действий»** соберите свою панель: «Настроить меню» → «Добавить действие…» / порядок стрелками.

### 4. Назначение обработчиком по умолчанию
1. В карточке **«Обработчик кнопки питания»** нажмите **«Вызвать NFC_BUS_SWINGSPLASH («Всегда»)»** — либо просто дважды нажмите кнопку питания.
2. В системном окне выбора отметьте **«Всегда»** и выберите **Double Power Key**.
3. Статусная плашка вверху станет зелёной.

---

## 📁 Структура проекта

```text
double-power-key/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml                  # Метаданные Vivo + MenuActivity + queries для VPN-клиентов
│       ├── java/com/vivotomirpay/stub/
│       │   ├── MainActivity.java               # Material You 3 настройки, пикер действий, редактор меню
│       │   ├── SwipeEntryActivity.java          # Перехват двойного нажатия: раздельная логика локскрина
│       │   ├── ActionRunner.java                # Единый исполнитель действий (torch/camera/CTS/VPN/меню/приложения)
│       │   ├── MenuActivity.java                # Меню действий: плавный bottom sheet, работает над lockscreen
│       │   ├── MenuEditorActivity.java          # Экран «Настройка меню»: добавление действий и приложений
│       │   ├── FlowLayout.java                  # Flow-layout для чипов (перенос на новую строку)
│       │   ├── VpnController.java               # Happ (happ://toggle) и incy (VOICE_*/TOGGLE) без root
│       │   ├── PrefsHelper.java                # Настройки: локскрин, вибрация, VPN, меню (JSON)
│       │   ├── Vibe.java                       # Вибрация: длительность и паттерны
│       │   └── Ui.java                         # Токены M3 + динамическая палитра Material You (API 31+)
│       └── res/
│           ├── drawable/                        # Векторные иконки (M3)
│           ├── drawable-anydpi-v26/             # Адаптивная иконка
│           ├── values/  values-en/              # Русский и английский
│           └── values/  values-night/           # Светлая и тёмная темы
├── build.gradle
├── settings.gradle
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── build_apk.sh / build_apk.ps1
└── README.md
```

---

## 🔧 Сборка APK

В репозитории есть **Gradle Wrapper** — Gradle ставить не обязательно.

### Требования
- **JDK 17+** (JDK 21 тоже работает)
- **Android SDK**: `platforms;android-36`, `build-tools;36.0.0` (или 35)
- `ANDROID_HOME` или `local.properties` со строкой `sdk.dir=/path/to/AndroidSDK`

```bash
./gradlew assembleRelease     # release (debug-ключ по умолчанию)
./gradlew assembleDebug       # debug
```

Готовый APK: `app/build/outputs/apk/release/app-release.apk` (~75 КБ).

### Подпись собственным ключом (опционально)

```bash
export VVTM_KEYSTORE=/path/to/release.jks
export VVTM_KEYSTORE_PASSWORD=******
export VVTM_KEY_ALIAS=release
export VVTM_KEY_PASSWORD=******
./gradlew assembleRelease
```

Без них используется `debug.keystore` (пароль `android`, alias `androiddebugkey`) — достаточно для локальной установки.

Также доступны скрипты: `./build_apk.sh` (Linux/macOS) и `.\build_apk.ps1` (Windows).

---

## 🔒 Безопасность

- **`android:allowBackup="false"`** — восстановленный бэкап мог бы подменить целевой пакет.
- **Экспортированная `SwipeEntryActivity` принимает интенты от любого приложения**, поэтому значения extras пишутся в `SharedPreferences` только как обрезанные строки (≤ 100 символов на значение, ≤ 1000 на весь лог).
- **VPN-действие не хранит никаких учётных данных** и не требует разрешений — оно лишь отправляет в установленный клиент тот же интент, что и его собственные виджет/шорткат.
- Меню действий показывается над экраном блокировки только по явному выбору пользователя.

---

## 🌐 English (short)

**Double Power Key** remaps the double press of the power button on Vivo / iQOO phones (OriginOS / FuntouchOS). Instead of the built-in Vivo Wallet it can launch **Mir Pay**, **SBPay**, **any app**, toggle the **flashlight**, open the **camera** (works above the lock screen), toggle **VPN** (**Happ** via `happ://toggle`, **incy** via its `VOICE_TOGGLE` shortcut commands — no root), trigger **Circle to Search** (MiCTS mechanism) or open a **customizable action menu** where you can add both actions and apps. A **separate action can be assigned for the lock screen** (flashlight by default) — and it can be disabled, so the locked screen behaves exactly like the unlocked one. Vibration is configurable (duration 20–500 ms, single/double/long patterns). The UI is **Material You 3** with wallpaper dynamic colors on Android 12+, languages: **Russian and English**. No root, no Shizuku, APK size ~75 KB.

---

## 📄 Лицензия

Проект распространяется свободно в ознакомительных и личных целях (MIT License).
