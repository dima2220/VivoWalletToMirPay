# VivoToMirPay

![Android](https://img.shields.io/badge/Android-7.1%2B%20(API%2025--35)-3DDC84?logo=android&logoColor=white)
![OS](https://img.shields.io/badge/ROM-OriginOS%20%7C%20FuntouchOS-4285F4)
![UI](https://img.shields.io/badge/UI-Android%2013%20Material%20You-A8C7FA)
![Root](https://img.shields.io/badge/Root%20%2F%20Shizuku-Not%20Required-brightgreen)
![APK Size](https://img.shields.io/badge/APK%20Size-~25%20KB-informational)

**VivoToMirPay** (`com.vivotomirpay.stub`) — лёгкое приложение-заглушка для смартфонов **Vivo** и **iQOO** (с прошивками **OriginOS** и **FuntouchOS**), позволяющее переназначить **двойное нажатие кнопки питания** со стандартного китайского кошелька **Vivo Wallet** на мгновенный запуск **Mir Pay**, **СБПэй** или любого другого приложения.

Работает **без Root-прав**, **без Shizuku** и **без удаления системных приложений**.

---

## ✨ Основные возможности

- ⚡ **Мгновенный запуск без промежуточных окон** — при двойном нажатии кнопки питания прозрачная [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) без анимации перенаправляет вызов в выбранное приложение и сразу закрывается (`finish()`).
- 💳 **Не сбивает приложение бесконтактной оплаты по умолчанию** — заглушка не регистрирует фиктивный HCE-сервис (`HostApduService`), поэтому **Mir Pay** остаётся основным платёжным средством NFC в системе.
- 🎯 **Гибкий выбор приложения для запуска**:
  - Быстрые пресеты в один тап: **Mir Pay** (`ru.nspk.mirpay`), **СБПэй** (`ru.nspk.sbpay`), **Vivo Wallet — NFC-ключ** (`com.vivo.wallet` / `NewSwipeActivity`).
  - Выбор **любого установленного приложения** из встроенного списка или ввод произвольного `packageName`.
- 🎨 **Интерфейс в стиле Android 13 (Material You / Material 3)** — тёмная палитра, индикатор текущего обработчика по умолчанию, кнопка вызова системного диалога выбора («Всегда») и диагностический журнал последнего срабатывания.
- 📳 **Опциональный виброотклик** — настраиваемая короткая вибрация (`35 мс`) в момент перехвата двойного нажатия.

---

## 🔍 Как это работает

На смартфонах Vivo / iQOO при двойном нажатии кнопки питания система формирует и отправляет неявный (implicit) системный `Intent`:

```text
Action:   vivo.intent.action.NFC_BUS_SWINGSPLASH
Category: android.intent.category.DEFAULT
Extras:   lb_from = "2"
```

По умолчанию в китайских прошивках OriginOS этот интент обрабатывается системным кошельком `com.vivo.wallet` (`com.vivo.pay.swing.activity.NewSwipeActivity`) или `com.vivo.seclient` (`com.vivo.pay.fexswipe.activity.NewSwipeActivity`).

**VivoToMirPay** объявляет экспортированную Activity [`SwipeEntryActivity`](app/src/main/java/com/vivotomirpay/stub/SwipeEntryActivity.java) с тем же `<intent-filter>` и системными метаданными Vivo в [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):

```xml
<meta-data android:name="com.vivo.wallet.support.typeswing" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.SupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.CommonSupportDoubleClick" android:value="true" />
<meta-data android:name="com.vivo.wallet.nfc.supportVersion" android:value="2" />
<meta-data android:name="com.vivo.pay.nfc.supportVersion" android:value="1" />
```

Благодаря этому система Android распознаёт **VivoToMirPay** как штатный обработчик двойного нажатия кнопки питания и выводит стандартное системное окно выбора приложения, где можно выбрать **VivoToMirPay** и нажать **«Всегда» (По умолчанию)**.

---

## 🚀 Установка и настройка

### 1. Установка APK
Скачайте и установите готовый подписанный файл [`VivoToMirPayStub.apk`](VivoToMirPayStub.apk) на смартфон.

### 2. Включение двойного нажатия в настройках Vivo
Убедитесь, что в настройках смартфона включён быстрый вызов кошелька по двойному нажатию кнопки питания:
- **OriginOS / FuntouchOS:** `Настройки` → `Другие сети и подключения` → `NFC` → **«Дважды нажмите кнопку питания»** (или в настройках `Vivo Wallet` → `Быстрая оплата по двойному нажатию кнопки питания`).

### 3. Выбор запускаемого приложения
1. Откройте приложение **Vivo -> Mir Pay** с рабочего стола.
2. В первой карточке **«Какое приложение запускать»** выберите **Mir Pay** (`ru.nspk.mirpay`), **СБПэй** или нажмите **«Все приложения…»**, чтобы выбрать любое установленное приложение.
3. Нажмите **«Сохранить»**.

### 4. Назначение обработчиком по умолчанию
1. Во второй карточке нажмите кнопку **«Вызвать NFC_BUS_SWINGSPLASH (Выбрать «Всегда»)»** — либо просто дважды нажмите кнопку питания на корпусе телефона.
2. В появившемся системном окне выбора приложения отметьте галочку **«Всегда» / «Использовать по умолчанию»** и выберите **Vivo -> Mir Pay**.
3. Статусная плашка вверху приложения сменится на зелёную:
   > `✓ Двойное нажатие питания: эта заглушка установлена по умолчанию`



---

## 📁 Структура проекта

```text
vivotomirpay/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml                  # Манифест с метаданными Vivo и intent-filter NFC_BUS_SWINGSPLASH
│       ├── java/com/vivotomirpay/stub/
│       │   ├── MainActivity.java                # Интерфейс настроек в стиле Android 13 (Material You)
│       │   ├── SwipeEntryActivity.java          # Прозрачная Activity-перехватчик двойного нажатия питания
│       │   └── PrefsHelper.java                 # Хранение настроек и диагностического лога в SharedPreferences
│       └── res/
│           ├── drawable/ic_launcher.xml         # Векторная иконка приложения
│           └── values/
│               ├── strings.xml                  # Строковые ресурсы
│               └── styles.xml                   # Темы приложения и прозрачной Activity без анимации
├── build_apk.ps1                                # Автономный скрипт сборки и подписи APK через Android SDK Build-Tools
├── build.gradle
├── settings.gradle
└── VivoToMirPayStub.apk                         # Готовый к установке подписанный APK-файл
```

---

### Требования
- **JDK 8+** (или выше, скрипт компилирует с флагом `--release 8`)
- **Android SDK** (`platforms/android-34` или `android-36.1`, `build-tools/36.1.0`)
---

## 📄 Лицензия

Проект распространяется свободно в ознакомительных и личных целях (MIT License).
