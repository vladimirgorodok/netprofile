package com.example.netprofile.domain

enum class Role { SHARE, RECEIVE }

enum class Status { OK, WARN, BAD, INFO }

enum class PrivateDns { OFF, AUTO, HOSTNAME, UNKNOWN }

sealed interface Apply {
    data class Open(val intentAction: String) : Apply
    data object BatteryExemption : Apply
    data object Manual : Apply
}

data class Snapshot(
    val airplaneMode: Boolean,
    val batteryOptimized: Boolean,
    val dataSaverOn: Boolean,
    val privateDns: PrivateDns,
    val wifiEnabled: Boolean,
)

data class Check(
    val id: String,
    val title: String,
    val current: String,
    val recommended: String,
    val status: Status,
    val reason: String,
    val apply: Apply,
) {
    val canApply: Boolean get() = apply !is Apply.Manual
    val preselected: Boolean get() = canApply && (status == Status.BAD || status == Status.WARN)
}

object Rules {

    const val ACTION_AIRPLANE = "android.settings.AIRPLANE_MODE_SETTINGS"
    const val ACTION_WIFI = "android.settings.WIFI_SETTINGS"
    const val ACTION_WIRELESS = "android.settings.WIRELESS_SETTINGS"
    const val ACTION_TETHER = "android.settings.TETHER_SETTINGS"

    fun evaluate(role: Role, s: Snapshot): List<Check> = when (role) {
        Role.SHARE -> shareChecks(s)
        Role.RECEIVE -> receiveChecks(s)
    }

    /** Значение `private_dns_mode`; если настройка не задана, Android использует режим auto (opportunistic). */
    fun parsePrivateDns(mode: String?): PrivateDns = when (mode) {
        null, "", "opportunistic" -> PrivateDns.AUTO
        "off" -> PrivateDns.OFF
        "hostname" -> PrivateDns.HOSTNAME
        else -> PrivateDns.UNKNOWN
    }

    private fun shareChecks(s: Snapshot): List<Check> = listOf(
        airplane(s),
        battery(s, "раздача Wi-Fi может быть остановлена системой, когда экран выключен"),
        dataSaver(s, "экономия трафика ограничивает фоновую работу раздачи"),
        privateDns(s),
        Check(
            id = "hotspot_band",
            title = "Диапазон точки доступа",
            current = "не читается из приложения",
            recommended = "5 ГГц (если поддерживается устройствами-клиентами)",
            status = Status.INFO,
            reason = "5 ГГц даёт заметно выше скорость на короткой дистанции",
            apply = Apply.Open(ACTION_TETHER),
        ),
        Check(
            id = "hotspot_security",
            title = "Защита точки доступа",
            current = "не читается из приложения",
            recommended = "WPA2/WPA3, пароль не короче 12 символов",
            status = Status.INFO,
            reason = "Открытая точка доступа пускает кого угодно и может быть опасна",
            apply = Apply.Open(ACTION_TETHER),
        ),
        Check(
            id = "hotspot_autoff",
            title = "Авто-выключение точки доступа",
            current = "не читается из приложения",
            recommended = "Отключено",
            status = Status.INFO,
            reason = "Иначе точка выключится при отсутствии клиентов и раздача прервётся",
            apply = Apply.Open(ACTION_TETHER),
        ),
    )

    private fun receiveChecks(s: Snapshot): List<Check> = listOf(
        Check(
            id = "wifi",
            title = "Wi-Fi включён",
            current = if (s.wifiEnabled) "включён" else "выключен",
            recommended = "включён",
            status = if (s.wifiEnabled) Status.OK else Status.BAD,
            reason = "Без Wi-Fi нельзя подключиться к точке доступа",
            apply = Apply.Open(ACTION_WIFI),
        ),
        airplane(s),
        battery(s, "система может разорвать соединение с точкой доступа в фоне"),
        dataSaver(s, "экономия трафика может блокировать фоновый обмен данными"),
        privateDns(s),
        Check(
            id = "hotspot_saved",
            title = "Сеть точки доступа сохранена",
            current = "не читается из приложения",
            recommended = "сохранена и с автоподключением",
            status = Status.INFO,
            reason = "Сохранённая сеть подключается сама, без ручного ввода пароля",
            apply = Apply.Open(ACTION_WIFI),
        ),
    )

    private fun airplane(s: Snapshot) = if (s.airplaneMode) Check(
        id = "airplane",
        title = "Режим полёта",
        current = "включён",
        recommended = "выключен",
        status = Status.BAD,
        reason = "Режим полёта отключает Wi-Fi и раздачу",
        apply = Apply.Open(ACTION_AIRPLANE),
    ) else Check(
        id = "airplane",
        title = "Режим полёта",
        current = "выключен",
        recommended = "выключен",
        status = Status.OK,
        reason = "",
        apply = Apply.Open(ACTION_AIRPLANE),
    )

    private fun battery(s: Snapshot, reason: String) = if (s.batteryOptimized) Check(
        id = "battery",
        title = "Оптимизация батареи",
        current = "включена для приложения",
        recommended = "отключена для приложения",
        status = Status.WARN,
        reason = reason,
        apply = Apply.BatteryExemption,
    ) else Check(
        id = "battery",
        title = "Оптимизация батареи",
        current = "отключена",
        recommended = "отключена для приложения",
        status = Status.OK,
        reason = "",
        apply = Apply.BatteryExemption,
    )

    private fun dataSaver(s: Snapshot, reason: String) = if (s.dataSaverOn) Check(
        id = "data_saver",
        title = "Экономия трафика",
        current = "включена",
        recommended = "выключена",
        status = Status.WARN,
        reason = reason,
        apply = Apply.Open(ACTION_WIRELESS),
    ) else Check(
        id = "data_saver",
        title = "Экономия трафика",
        current = "выключена",
        recommended = "выключена",
        status = Status.OK,
        reason = "",
        apply = Apply.Open(ACTION_WIRELESS),
    )

    private fun privateDns(s: Snapshot) = when (s.privateDns) {
        PrivateDns.HOSTNAME -> Check(
            id = "private_dns",
            title = "Личный DNS",
            current = "указан хост",
            recommended = "автоматически",
            status = Status.WARN,
            reason = "Указанный DNS-сервер может не работать через точку доступа",
            apply = Apply.Open(ACTION_WIRELESS),
        )
        PrivateDns.OFF, PrivateDns.AUTO -> Check(
            id = "private_dns",
            title = "Личный DNS",
            current = if (s.privateDns == PrivateDns.OFF) "выключен" else "автоматически",
            recommended = "автоматически",
            status = Status.OK,
            reason = "",
            apply = Apply.Open(ACTION_WIRELESS),
        )
        PrivateDns.UNKNOWN -> Check(
            id = "private_dns",
            title = "Личный DNS",
            current = "не удалось прочитать",
            recommended = "автоматически",
            status = Status.INFO,
            reason = "Проверьте вручную в настройках сети",
            apply = Apply.Open(ACTION_WIRELESS),
        )
    }
}
