package com.vivotomirpay.stub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;

import java.util.List;

/**
 * Управление VPN-клиентами Happ и Incy без root — полностью в фоне,
 * без открытия интерфейса приложений.
 *
 * Механизмы найдены при разборе официальных APK (aapt2 dump manifest):
 *
 *  Happ (su.happ.proxyutility, а также устаревший пакет com.happproxy):
 *    - экспортированная полупрозрачная активность
 *      su.happ.proxyutility.feature.deeplink.DeeplinkInterceptorActivity принимает
 *      ACTION_VIEW со схемой happ:// и хостами ТОЛЬКО вида *_without_ui:
 *      connect_without_ui / disconnect_without_ui / toggle_without_ui /
 *      open_without_ui / close_without_ui. Обычные хосты (happ://toggle и т.п.)
 *      матчатся на MainActivity с интерфейсом — поэтому для фонового
 *      переключения используется именно happ://toggle_without_ui;
 *    - экспортированный su.happ.proxyutility.receiver.WidgetProvider обрабатывает
 *      broadcast su.happ.proxyutility.action.widget.click — это тот же обработчик,
 *      что у виджета Happ: переключает VPN без всякого UI.
 *    Классы не хардкодятся: активность ищется неявным интентом с setPackage,
 *    поэтому работает и для старого пакета com.happproxy с другими именами классов.
 *
 *  Incy (llc.itdev.incy) — разбор APK (dexdump):
 *    - экспортированный llc.itdev.incy.receiver.VpnIntentReceiver принимает
 *      broadcast-действия llc.itdev.incy.CONNECT / DISCONNECT / TOGGLE / STATUS,
 *      без permission-защиты, лишних extras не требует;
 *    - экспортированная ShortcutActivity (полупрозрачная тема, excludeFromRecents,
 *      сразу finish()) обрабатывает llc.itdev.incy.VOICE_CONNECT /
 *      VOICE_DISCONNECT / VOICE_TOGGLE — запуск без видимого UI.
 *
 *    ВАЖНО: бродкасты не доставляются приложениям в stopped-state (свежая
 *    установка, force-stop или убитое энергоменеджером приложение) — при этом
 *    queryBroadcastReceivers ресивер всё ещё находит, поэтому «отправили, но
 *    ничего не произошло». Поэтому: 1) отправляем бродкаст с флагом
 *    FLAG_INCLUDE_STOPPED_PACKAGES — система разбудит даже остановленное
 *    приложение; 2) дублируем команду запуском ShortcutActivity — старт
 *    активности будит процесс при любых ограничениях энергоменеджера.
 *    Команды отправляем не toggle, а зависимые от текущего состояния
 *    connect/disconnect — они идемпотентны, поэтому двойная доставка безопасна.
 *
 *  Активный VPN определяется публичным API: перебор сетей ConnectivityManager
 *  и проверка транспорта TRANSPORT_VPN.
 */
public final class VpnController {

    // Happ: актуальный пакет (GitHub-релизы / APK с happ.su) и старый пакет (Play Market).
    public static final String HAPP_PKG_NEW = "su.happ.proxyutility";
    public static final String HAPP_PKG_OLD = "com.happproxy";
    // Incy.
    public static final String INCY_PKG = "llc.itdev.incy";

    private VpnController() {
    }

    // ------------------------------------------------------------------
    // Определение установленного клиента
    // ------------------------------------------------------------------

    public static boolean isPackageInstalled(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isHappInstalled(Context context) {
        return isPackageInstalled(context, HAPP_PKG_NEW) || isPackageInstalled(context, HAPP_PKG_OLD);
    }

    public static boolean isIncyInstalled(Context context) {
        return isPackageInstalled(context, INCY_PKG);
    }

    /**
     * Возвращает имя пакета установленного Happ-клиента или null.
     * Приоритет у нового пакета su.happ.proxyutility.
     */
    public static String getHappPackage(Context context) {
        if (isPackageInstalled(context, HAPP_PKG_NEW)) return HAPP_PKG_NEW;
        if (isPackageInstalled(context, HAPP_PKG_OLD)) return HAPP_PKG_OLD;
        return null;
    }

    /** Имя пакета клиента, которое выберет действие special:vpn при настройке «Авто». */
    public static String resolveClientPackage(Context context, String clientSetting) {
        if ("happ".equals(clientSetting)) {
            return getHappPackage(context);
        }
        if ("incy".equals(clientSetting)) {
            return isIncyInstalled(context) ? INCY_PKG : null;
        }
        // auto: приоритет — Happ, затем Incy.
        String happ = getHappPackage(context);
        if (happ != null) return happ;
        return isIncyInstalled(context) ? INCY_PKG : null;
    }

    /** Человекочитаемое имя клиента по пакету («Happ» / «incy»). */
    public static String clientLabel(String pkg) {
        if (HAPP_PKG_NEW.equals(pkg) || HAPP_PKG_OLD.equals(pkg)) return "Happ";
        if (INCY_PKG.equals(pkg)) return "incy";
        return pkg != null ? pkg : "?";
    }

    // ------------------------------------------------------------------
    // Состояние VPN (публичный API)
    // ------------------------------------------------------------------

    /** Есть ли сейчас активный VPN-туннель в системе (любой клиент). */
    public static boolean isAnyVpnActive(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network[] networks = cm.getAllNetworks();
            if (networks == null) return false;
            for (Network network : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Переключение
    // ------------------------------------------------------------------

    /**
     * Переключает VPN выбранного клиента — в фоне, без открытия интерфейса приложений.
     *
     * Happ:  1) deeplink happ://toggle_without_ui | happ://connect_without_ui |
     *           happ://disconnect_without_ui — экспортированный полупрозрачный
     *           перехватчик (тема Translucent, сам закрывается). Интент неявный,
     *           с setPackage — не зависит от имён классов конкретной версии;
     *        2) broadcast «*.action.widget.click» — тот же обработчик, что у виджета
     *           Happ (ресивер экспортирован, имя экшена строится из реального
     *           пакета и проверяется через queryBroadcastReceivers);
     *        3) честный отказ: приложение не открывается.
     *
     * incy:  1) broadcast llc.itdev.incy.CONNECT / DISCONNECT в экспортированный
     *           VpnIntentReceiver, с FLAG_INCLUDE_STOPPED_PACKAGES (иначе бродкаст
     *           молча не доходит до остановленного/убитого энергоменеджером
     *           приложения). Для режима toggle команда выбирается по факту:
     *           туннель активен — disconnect, не активен — connect;
     *        2) тот же verb дублируется через ShortcutActivity с VOICE_* —
     *           полупрозрачная активность без UI, её старт гарантированно будит
     *           процесс. connect/disconnect идемпотентны, поэтому двойная
     *           доставка безопасна;
     *        3) честный отказ: приложение не открывается.
     *
     * @param mode "toggle" | "connect" | "disconnect"
     * @return человекочитаемый статус результата для журнала.
     */
    public static String controlVpn(Context context, String clientSetting, String mode) {
        String pkg = resolveClientPackage(context, clientSetting);
        if (pkg == null) {
            String wanted = "happ".equals(clientSetting) ? "Happ"
                    : "incy".equals(clientSetting) ? "incy" : "Happ / incy";
            return context.getString(R.string.vpn_status_not_installed, wanted);
        }

        boolean wasActive = isAnyVpnActive(context);
        String label = clientLabel(pkg);
        String verb;
        if ("connect".equals(mode)) {
            verb = "connect";
        } else if ("disconnect".equals(mode)) {
            verb = "disconnect";
        } else {
            verb = "toggle";
        }
        String verbUpper = verb.toUpperCase(java.util.Locale.US);

        // 1) Happ: happ://*_without_ui — фоновое переключение через перехватчик deeplink.
        if (HAPP_PKG_NEW.equals(pkg) || HAPP_PKG_OLD.equals(pkg)) {
            // 1а) Неявный интент с setPackage: резолвится по фильтрам самой версии Happ,
            //     никаких захардкоженных имён классов.
            try {
                Intent deeplink = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("happ://" + verb + "_without_ui"));
                deeplink.addCategory(Intent.CATEGORY_DEFAULT);
                deeplink.setPackage(pkg);
                deeplink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                if (deeplink.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(deeplink);
                    return context.getString(R.string.vpn_status_ok, label, verb,
                            vpnStateHint(context, wasActive));
                }
            } catch (Exception ignored) {
                // Ниже — broadcast-путь.
            }

            // 1б) Broadcast в экспортированный WidgetProvider (обработчик виджета Happ):
            //     имя экшена строится из фактического пакета, наличие ресивера
            //     проверяется заранее, чтобы не докладывать об успехе зря.
            if (sendCheckedBroadcast(context, pkg + ".action.widget.click", pkg)) {
                return context.getString(R.string.vpn_status_ok, label, verb,
                        vpnStateHint(context, wasActive));
            }

            return context.getString(R.string.vpn_status_no_path, label);
        }

        // 2) incy. Бродкасты не доходят до приложений в stopped-state (после
        //    установки, force-stop или убийства энергоменеджером Vivo iManager):
        //    queryBroadcastReceivers ресивер находит, sendBroadcast «молча успешен»,
        //    но Incy ничего не получает — из-за этого переключение не работало,
        //    хотя Happ (запуск активности) работал. Команды делаем зависимыми от
        //    состояния VPN (connect/disconnect вместо toggle) — они идемпотентны,
        //    поэтому доставку можно дублировать без риска двойного переключения.
        if (INCY_PKG.equals(pkg)) {
            String verbResolved = "toggle".equals(verb)
                    ? (wasActive ? "disconnect" : "connect")
                    : verb;
            String verbResolvedUpper = verbResolved.toUpperCase(java.util.Locale.US);
            boolean delivered = false;

            // 2а) Broadcast в экспортированный VpnIntentReceiver.
            //     FLAG_INCLUDE_STOPPED_PACKAGES обязателен: с ним система доставит
            //     бродкаст и разбудит процесс, даже если Incy в stopped-state.
            try {
                Intent broadcast = new Intent("llc.itdev.incy." + verbResolvedUpper);
                broadcast.setPackage(pkg);
                broadcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                List<ResolveInfo> receivers = context.getPackageManager()
                        .queryBroadcastReceivers(broadcast, 0);
                if (receivers != null && !receivers.isEmpty()) {
                    context.sendBroadcast(broadcast);
                    delivered = true;
                }
            } catch (Exception ignored) {
            }

            // 2б) Дублируем через ShortcutActivity (VOICE_*): полупрозрачная
            //     активность без UI, мгновенно finish(). Старт активности будит
            //     процесс даже если бродкаст заблокировал энергоменеджер;
            //     connect/disconnect идемпотентны — двойной запуск безопасен
            //     (в отличие от toggle, поэтому toggle и не дублируем).
            try {
                Intent shortcut = new Intent("llc.itdev.incy.VOICE_" + verbResolvedUpper);
                shortcut.setComponent(new ComponentName(pkg,
                        "llc.itdev.incy.ShortcutActivity"));
                shortcut.addCategory(Intent.CATEGORY_DEFAULT);
                shortcut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                if (shortcut.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(shortcut);
                    delivered = true;
                }
            } catch (Exception ignored) {
            }

            if (delivered) {
                return context.getString(R.string.vpn_status_ok, label, verbResolved,
                        vpnStateHint(context, wasActive));
            }
            return context.getString(R.string.vpn_status_no_path, label);
        }

        return context.getString(R.string.vpn_status_no_path, label);
    }

    /**
     * Отправляет broadcast в конкретный пакет, только если там есть экспортированный
     * ресивер с таким действием. sendBroadcast сам по себе никогда не бросает
     * исключений и «молча успешен», поэтому без проверки нельзя докладывать об успехе.
     *
     * @return true, если ресивер найден и broadcast отправлен.
     */
    private static boolean sendCheckedBroadcast(Context context, String action, String pkg) {
        try {
            Intent broadcast = new Intent(action);
            broadcast.setPackage(pkg);
            // Доставка и в stopped-state (свежая установка / force-stop / убитый
            // энергоменеджером процесс) — иначе бродкаст молча не дойдёт.
            broadcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> receivers = pm.queryBroadcastReceivers(broadcast, 0);
            if (receivers == null || receivers.isEmpty()) {
                return false;
            }
            context.sendBroadcast(broadcast);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String vpnStateHint(Context context, boolean wasActive) {
        return wasActive
                ? context.getString(R.string.vpn_state_was_active)
                : context.getString(R.string.vpn_state_was_inactive);
    }
}
