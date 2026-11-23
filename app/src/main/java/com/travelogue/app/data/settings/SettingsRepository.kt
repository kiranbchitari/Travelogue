package com.travelogue.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelogue.app.core.designsystem.theme.AppTheme
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.data.backup.BackupHistory
import com.travelogue.app.data.backup.BackupJson
import com.travelogue.app.data.backup.BackupRecord
import com.travelogue.app.data.backup.HolidayPersonArchive
import com.travelogue.app.data.backup.HolidayPrefsArchive
import com.travelogue.app.data.db.enumOr
import com.travelogue.app.domain.ExchangeRate
import com.travelogue.app.domain.HolidayPerson
import com.travelogue.app.domain.HolidayPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The look chosen in Settings → Appearance. */
    val theme: AppTheme = AppTheme.CLASSIC,
    val defaultCurrency: String = "INR",
    val demoLoaded: Boolean = false,
    /** Manual rates used to bring every trip into the default currency (Settings → Exchange rates). */
    val exchangeRates: List<ExchangeRate> = emptyList(),
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val look = stringPreferencesKey("app_theme")
        val currency = stringPreferencesKey("default_currency")
        val demo = booleanPreferencesKey("demo_loaded")
        val rates = stringPreferencesKey("exchange_rates")
        val demoTravellers = stringPreferencesKey("demo_travellers")
        val holidayMyName = stringPreferencesKey("holiday_my_name")
        val holidayPartnerName = stringPreferencesKey("holiday_partner_name")
        val holidayMyWeeklyOff = stringPreferencesKey("holiday_my_weekly_off")
        val holidayPartnerWeeklyOff = stringPreferencesKey("holiday_partner_weekly_off")
        val holidayShowIndian = booleanPreferencesKey("holiday_show_indian")
        val holidayExtraPeople = stringPreferencesKey("holiday_extra_people")
        val lastDriveBackup = stringPreferencesKey("last_drive_backup")
        val lastFileBackup = stringPreferencesKey("last_file_backup")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = enumOr(p[Keys.theme], ThemeMode.SYSTEM),
            theme = enumOr(p[Keys.look], AppTheme.CLASSIC),
            defaultCurrency = p[Keys.currency] ?: "INR",
            demoLoaded = p[Keys.demo] ?: false,
            exchangeRates = decodeRates(p[Keys.rates]),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Names and weekly days off for everyone in the holiday planner. */
    val holidayPrefs: Flow<HolidayPrefs> = context.dataStore.data.map { p ->
        HolidayPrefs(
            myName = p[Keys.holidayMyName]?.takeIf { it.isNotBlank() } ?: HolidayPrefs.DEFAULT_MY_NAME,
            partnerName = p[Keys.holidayPartnerName]?.takeIf { it.isNotBlank() } ?: HolidayPrefs.DEFAULT_PARTNER_NAME,
            myWeeklyOff = decodeWeekdays(p[Keys.holidayMyWeeklyOff]),
            partnerWeeklyOff = decodeWeekdays(p[Keys.holidayPartnerWeeklyOff]),
            showIndianHolidays = p[Keys.holidayShowIndian] ?: true,
            extras = decodePeople(p[Keys.holidayExtraPeople]),
        )
    }.distinctUntilChanged()

    suspend fun setHolidayPrefs(prefs: HolidayPrefs) {
        context.dataStore.edit {
            it[Keys.holidayMyName] = prefs.myName.trim().take(HolidayPrefs.MAX_NAME)
            it[Keys.holidayPartnerName] = prefs.partnerName.trim().take(HolidayPrefs.MAX_NAME)
            it[Keys.holidayMyWeeklyOff] = encodeWeekdays(prefs.myWeeklyOff)
            it[Keys.holidayPartnerWeeklyOff] = encodeWeekdays(prefs.partnerWeeklyOff)
            it[Keys.holidayShowIndian] = prefs.showIndianHolidays
            it[Keys.holidayExtraPeople] = encodePeople(prefs.extras)
        }
    }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[Keys.theme] = mode.name } }
    suspend fun setAppTheme(theme: AppTheme) { context.dataStore.edit { it[Keys.look] = theme.name } }
    suspend fun setDefaultCurrency(code: String) { context.dataStore.edit { it[Keys.currency] = code.uppercase() } }
    suspend fun setDemoLoaded(v: Boolean) { context.dataStore.edit { it[Keys.demo] = v } }
    /** Ids of travellers that loading the sample trips created (as opposed to ones that already existed). */
    suspend fun demoTravellerIds(): List<Long> =
        context.dataStore.data.first()[Keys.demoTravellers].orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    suspend fun setDemoTravellerIds(ids: List<Long>) {
        context.dataStore.edit { if (ids.isEmpty()) it.remove(Keys.demoTravellers) else it[Keys.demoTravellers] = ids.joinToString(",") }
    }

    /** The last full backup to Google Drive and to a ZIP file. */
    val backupHistory: Flow<BackupHistory> = context.dataStore.data.map { p ->
        BackupHistory(drive = decodeRecord(p[Keys.lastDriveBackup]), file = decodeRecord(p[Keys.lastFileBackup]))
    }.distinctUntilChanged()

    suspend fun recordBackup(record: BackupRecord) {
        val key = if (record.isDrive) Keys.lastDriveBackup else Keys.lastFileBackup
        context.dataStore.edit { it[key] = BackupJson.encodeToString(BackupRecord.serializer(), record) }
    }

    /**
     * Forgets every preference. The backup history is kept: the backups themselves still exist in
     * Drive or on the phone, and knowing about them matters most right after the data is cleared.
     */
    suspend fun clear() {
        context.dataStore.edit { p ->
            val kept = listOf(Keys.lastDriveBackup, Keys.lastFileBackup).mapNotNull { k -> p[k]?.let { v -> Pair(k, v) } }
            p.clear()
            kept.forEach { (k, v) -> p[k] = v }
        }
    }

    private fun decodeRecord(raw: String?): BackupRecord? =
        raw?.let { runCatching { BackupJson.decodeFromString(BackupRecord.serializer(), it) }.getOrNull() }

    /** Sets (or with a null/zero rate, removes) 1 [from] = [rate] [to]. Any inverse pair is replaced. */
    suspend fun setExchangeRate(from: String, to: String, rate: Double?) {
        val f = from.uppercase(); val t = to.uppercase()
        context.dataStore.edit { p ->
            val kept = decodeRates(p[Keys.rates]).filterNot { (it.from == f && it.to == t) || (it.from == t && it.to == f) }
            val next = if (rate != null && rate > 0) kept + ExchangeRate(f, t, rate) else kept
            p[Keys.rates] = encodeRates(next)
        }
    }

    suspend fun replaceExchangeRates(rates: List<ExchangeRate>) {
        context.dataStore.edit { it[Keys.rates] = encodeRates(rates.filter { r -> r.rate > 0 }) }
    }

    companion object {
        /** ISO weekday numbers, Monday = 1: "6,7". An empty string means no weekly day off at all. */
        fun encodeWeekdays(days: Set<DayOfWeek>): String = days.map { it.value }.sorted().joinToString(",")

        /** Absent (never set) falls back to Saturday and Sunday. */
        fun decodeWeekdays(raw: String?): Set<DayOfWeek> =
            raw?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }?.toSet() ?: HolidayPrefs.Weekend

        private val PeopleSerializer = ListSerializer(HolidayPersonArchive.serializer())

        fun archive(p: HolidayPerson) = HolidayPersonArchive(p.id, p.name, p.weeklyOff.map { it.value }.sorted())

        /**
         * Tidies a list of added people wherever it came from (this app, or a backup file): ids must
         * be usable and unique, names fit, and there are never more people than the planner can show.
         */
        fun toPeople(archived: List<HolidayPersonArchive>): List<HolidayPerson> = archived
            .filter { it.id.isNotBlank() && it.id.length <= 40 && it.id != HolidayPerson.ME && it.id != HolidayPerson.PARTNER }
            .distinctBy { it.id }
            .take(HolidayPrefs.MAX_PEOPLE - 2)
            .mapIndexed { i, a ->
                HolidayPerson(a.id, a.name.trim().take(HolidayPrefs.MAX_NAME).ifBlank { "Person ${i + 3}" }, decodeWeekdays(a.weeklyOff.joinToString(",")))
            }

        fun encodePeople(people: List<HolidayPerson>): String = BackupJson.encodeToString(PeopleSerializer, toPeople(people.map(::archive)).map(::archive))

        /** Absent or unreadable means nobody has been added. */
        fun decodePeople(raw: String?): List<HolidayPerson> =
            raw?.let { runCatching { toPeople(BackupJson.decodeFromString(PeopleSerializer, it)) }.getOrNull() }.orEmpty()

        /** Everything about the planner's people and its settings, as a backup carries it. */
        fun archive(prefs: HolidayPrefs) = HolidayPrefsArchive(
            myName = prefs.myName,
            partnerName = prefs.partnerName,
            myWeeklyOff = prefs.myWeeklyOff.map { it.value }.sorted(),
            partnerWeeklyOff = prefs.partnerWeeklyOff.map { it.value }.sorted(),
            showIndianHolidays = prefs.showIndianHolidays,
            extras = prefs.extras.map(::archive),
        )

        /**
         * What the planner's settings become when a backup is restored on a device that has [current].
         * The backup's names, weekly days off and holiday switch win. People are merged like their days
         * off: everyone in the backup comes back as the backup had them, and anyone added on this
         * device since then stays. An older backup that names nobody keeps the current names.
         */
        fun restore(backup: HolidayPrefsArchive, current: HolidayPrefs): HolidayPrefs {
            val restored = toPeople(backup.extras)
            return HolidayPrefs(
                myName = backup.myName.ifBlank { current.myName },
                partnerName = backup.partnerName.ifBlank { current.partnerName },
                myWeeklyOff = decodeWeekdays(backup.myWeeklyOff.joinToString(",")),
                partnerWeeklyOff = decodeWeekdays(backup.partnerWeeklyOff.joinToString(",")),
                showIndianHolidays = backup.showIndianHolidays,
                extras = (restored + current.extras.filter { mine -> restored.none { it.id == mine.id } }).take(HolidayPrefs.MAX_PEOPLE - 2),
            )
        }

        /** Stored as "EUR>INR=92.5;USD>INR=84". */
        fun encodeRates(rates: List<ExchangeRate>): String = rates.joinToString(";") { "${it.from}>${it.to}=${it.rate}" }

        fun decodeRates(raw: String?): List<ExchangeRate> = raw.orEmpty().split(';').mapNotNull { part ->
            val pair = part.substringBefore('=', "")
            val rate = part.substringAfter('=', "").toDoubleOrNull() ?: return@mapNotNull null
            val from = pair.substringBefore('>', "").trim().uppercase()
            val to = pair.substringAfter('>', "").trim().uppercase()
            if (from.length != 3 || to.length != 3 || rate <= 0) null else ExchangeRate(from, to, rate)
        }
    }
}
