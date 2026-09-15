package com.retra.emulator

/**
 * Small native-language bridge for gameplay UI that is rendered outside the WebView.
 * The Web UI writes the same `ui_language` preference via AndroidBridge.setUiPreference.
 */
internal fun MainActivity.uiText(english: String): String {
    val language = prefs.getString("ui_language", "en") ?: "en"
    if (language == "en") return english

    val seconds = Regex("^(\\d+) seconds$").matchEntire(english)?.groupValues?.getOrNull(1)
    if (seconds != null) return if (language == "vi") "$seconds giây" else "$seconds detik"
    val rewindSeconds = Regex("^Rewind gameplay by (\\d+) seconds$").matchEntire(english)?.groupValues?.getOrNull(1)
    if (rewindSeconds != null) return if (language == "vi") "Tua ngược $rewindSeconds giây" else "Putar mundur $rewindSeconds detik"
    val upToSeconds = Regex("^Up to (\\d+) sec available$").matchEntire(english)?.groupValues?.getOrNull(1)
    if (upToSeconds != null) return if (language == "vi") "Có thể tua ngược tối đa $upToSeconds giây" else "Tersedia hingga $upToSeconds detik"
    if (english.startsWith(".sav for ")) {
        val title = english.removePrefix(".sav for ")
        return if (language == "vi") ".sav cho $title" else ".sav untuk $title"
    }
    if (english.startsWith("Currently ")) {
        val speed = english.removePrefix("Currently ")
        return if (language == "vi") "Hiện tại $speed" else "Saat ini $speed"
    }
    if (english.startsWith("Uses ") && english.endsWith(" from Settings")) {
        val speed = english.removePrefix("Uses ").removeSuffix(" from Settings")
        return if (language == "vi") "Dùng $speed từ Cài đặt" else "Menggunakan $speed dari Pengaturan"
    }

    val vi = mapOf(
        "Menu" to "Menu",
        "Back" to "Quay lại",
        "Load" to "Tải",
        "Save" to "Lưu",
        "Quick + Slot 1–10" to "Nhanh + Ô 1–10",
        "Unavailable during Local Link" to "Không khả dụng khi Local Link đang hoạt động",
        "Rewind" to "Tua ngược",
        "Unavailable while linked" to "Không khả dụng khi đang liên kết",
        "5 sec • 10 sec • 15 sec" to "5 giây • 10 giây • 15 giây",
        "Speed mode" to "Chế độ tốc độ",
        "Normal speed" to "Tốc độ thường",
        "Disabled while linked" to "Bị tắt khi đang liên kết",
        "Cheats" to "Cheat",
        "Edit layout" to "Chỉnh bố cục",
        "Adjust the current controller layout" to "Điều chỉnh bố cục điều khiển hiện tại",
        "Import save" to "Nhập bản lưu",
        "Settings" to "Cài đặt",
        "Link remote" to "Liên kết từ xa",
        "Link local" to "Liên kết cục bộ",
        "Screenshot" to "Chụp màn hình",
        "Reset" to "Đặt lại",
        "Close" to "Đóng",
        "Save state" to "Lưu state",
        "Load state" to "Tải state",
        "Not enough history yet" to "Chưa có đủ lịch sử",
        "Performance" to "Hiệu năng",
        "Compatibility" to "Tương thích"
    )
    val id = mapOf(
        "Menu" to "Menu",
        "Back" to "Kembali",
        "Load" to "Muat",
        "Save" to "Simpan",
        "Quick + Slot 1–10" to "Cepat + Slot 1–10",
        "Unavailable during Local Link" to "Tidak tersedia saat Local Link aktif",
        "Rewind" to "Putar mundur",
        "Unavailable while linked" to "Tidak tersedia saat terhubung",
        "5 sec • 10 sec • 15 sec" to "5 dtk • 10 dtk • 15 dtk",
        "Speed mode" to "Mode kecepatan",
        "Normal speed" to "Kecepatan normal",
        "Disabled while linked" to "Dinonaktifkan saat terhubung",
        "Cheats" to "Cheat",
        "Edit layout" to "Edit tata letak",
        "Adjust the current controller layout" to "Atur tata letak kontrol saat ini",
        "Import save" to "Impor simpanan",
        "Settings" to "Pengaturan",
        "Link remote" to "Link jarak jauh",
        "Link local" to "Link lokal",
        "Screenshot" to "Tangkapan layar",
        "Reset" to "Atur ulang",
        "Close" to "Tutup",
        "Save state" to "Simpan state",
        "Load state" to "Muat state",
        "Not enough history yet" to "Riwayat belum cukup",
        "Performance" to "Performa",
        "Compatibility" to "Kompatibilitas"
    )
    return when (language) {
        "vi" -> vi[english] ?: english
        "id" -> id[english] ?: english
        else -> english
    }
}
