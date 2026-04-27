package com.ducktask.app.parser

import com.ducktask.app.domain.model.RepeatRule
import com.ducktask.app.util.DUCK_TIME_ZONE
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedResult(
    val time: LocalDateTime,
    val event: String,
    val repeat: RepeatRule? = null,
    val description: String
)

class ParseException(message: String) : Exception(message)

object ChineseNumberNormalizer {
    private val cnNum = mapOf(
        '〇' to 0,
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
        '壹' to 1,
        '贰' to 2,
        '貮' to 2,
        '叁' to 3,
        '肆' to 4,
        '伍' to 5,
        '陆' to 6,
        '柒' to 7,
        '捌' to 8,
        '玖' to 9,
        '两' to 2
    )

    private val cnUnit = mapOf(
        '十' to 10,
        '拾' to 10,
        '百' to 100,
        '佰' to 100,
        '千' to 1000,
        '仟' to 1000,
        '万' to 10000,
        '萬' to 10000,
        '亿' to 100000000,
        '億' to 100000000
    )

    fun normalize(text: String): String {
        val protectedWeekday = replaceMixedArabicChineseNumbers(
            text
            .replace(Regex("((?:周|星期|礼拜)[一二三四五六日天])(?=\\d)"), "$1 ")
            .replace('：', ':')
        )
        val firstPass = mutableListOf<Any>()
        for (char in protectedWeekday) {
            when {
                char in cnUnit -> {
                    val unit = cnUnit.getValue(char)
                    val previous = firstPass.lastOrNull()
                    if (previous is Int) {
                        firstPass[firstPass.lastIndex] = previous * unit
                    } else if (unit == 10) {
                        firstPass += 10
                    } else {
                        firstPass += char.toString()
                    }
                }
                char in cnNum -> firstPass += cnNum.getValue(char)
                else -> firstPass += char.toString()
            }
        }

        val merged = mutableListOf<Any>()
        for (item in firstPass) {
            val previous = merged.lastOrNull()
            if (item is Int && previous is Int) {
                merged[merged.lastIndex] = previous + item
            } else {
                merged += item
            }
        }

        return buildString {
            merged.forEach { append(it.toString()) }
        }
    }

    private fun replaceMixedArabicChineseNumbers(text: String): String {
        return Regex("(\\d)十([一二三四五六七八九\\d]?)").replace(text) { match ->
            val tens = match.groupValues[1].toInt() * 10
            val onesRaw = match.groupValues[2]
            val ones = when {
                onesRaw.isBlank() -> 0
                onesRaw.first().isDigit() -> onesRaw.toInt()
                else -> cnNum[onesRaw.first()] ?: 0
            }
            (tens + ones).toString()
        }
    }
}

object TimeParser {
    private var testClock: java.time.Clock? = null

    fun parse(text: String): ParsedResult {
        val clock = testClock ?: java.time.Clock.system(DUCK_TIME_ZONE)
        return parse(text, LocalDateTime.now(clock))
    }

    fun parse(text: String, now: LocalDateTime): ParsedResult {
        val parser = RuleParser(text, now.withNano(0))
        return parser.parse()
    }

    fun setClock(clock: java.time.Clock) {
        testClock = clock
    }

    fun resetClock() {
        testClock = null
    }
}

private enum class DayPeriod {
    MORNING,
    NOON,
    AFTERNOON,
    EVENING,
    NIGHT
}

private data class ParseState(
    var date: LocalDate,
    var hour: Int? = null,
    var minute: Int = 0,
    var second: Int = 0,
    var repeat: RepeatRule? = null,
    var relativeInstant: LocalDateTime? = null,
    var hasDate: Boolean = false,
    var hasAbsoluteDate: Boolean = false,
    var hasTime: Boolean = false,
    var dayPeriod: DayPeriod? = null,
    val consumed: MutableList<IntRange> = mutableListOf()
)

private class RuleParser(
    private val originalText: String,
    private val now: LocalDateTime
) {
    private val text = ChineseNumberNormalizer.normalize(originalText)
    private val state = ParseState(date = now.toLocalDate())

    fun parse(): ParsedResult {
        rejectUnsupported()

        parseRepeat()
        parseRelativeDuration()
        parseRelativeDate()
        parseAbsoluteDate()
        parseWeekday()
        parseDayPeriod()
        parseClockTime()

        if (state.relativeInstant == null && !state.hasDate && !state.hasTime && state.repeat == null) {
            throw ParseException("无法理解输入，请尝试更明确的表达，如“明天下午3点提醒我开会”")
        }

        val parsedTime = buildReminderTime()
        val adjustedTime = adjustFirstRun(parsedTime)
        val event = extractEvent()

        return ParsedResult(
            time = adjustedTime,
            event = event.ifBlank { "闹钟" },
            repeat = state.repeat,
            description = originalText
        )
    }

    private fun rejectUnsupported() {
        if (text.contains("农历") || text.contains("阴历")) {
            throw ParseException("暂不支持农历提醒")
        }
        if (text.contains("工作日")) {
            throw ParseException("暂不支持工作日提醒")
        }

        val rangePatterns = listOf(
            Regex("\\d{1,2}[:点时]\\d{0,2}\\s*[-~到至]\\s*\\d{1,2}[:点时]?\\d{0,2}"),
            Regex("(?:周|星期|礼拜)[1-7日天]\\s*[-~到至]\\s*(?:周|星期|礼拜)[1-7日天]"),
            Regex("\\d{1,2}[号日]\\s*[-~到至]\\s*\\d{1,2}[号日]")
        )
        if (rangePatterns.any { it.containsMatchIn(text) }) {
            throw ParseException("暂不支持连续时间段提醒，请拆成多个提醒")
        }
    }

    private fun parseRepeat() {
        Regex("每(?:隔)?(?:\\d+)?(?:分钟|分)").find(text)?.let {
            throw ParseException("暂不支持分钟级别的重复提醒")
        }

        parseYearRepeat()?.let { return }
        parseMonthRepeat()?.let { return }
        parseWeekRepeat()?.let { return }
        parseDayRepeat()?.let { return }
        parseHourRepeat()
    }

    private fun parseYearRepeat(): Unit? {
        val match = Regex("每(?:(?:隔)?(?:(\\d+)个?)?|个)?年(?:\\s*(\\d{1,2})月(\\d{1,2})(?:号|日)?)?").find(text)
            ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        checkRepeatCount(count)
        state.repeat = RepeatRule(years = count)
        match.groupValues[2].toIntOrNull()?.let { month ->
            val day = match.groupValues[3].toIntOrNull() ?: 1
            state.date = safeDate(now.year, month, day)
            state.hasDate = true
            state.hasAbsoluteDate = true
        }
        consume(match.range)
        return Unit
    }

    private fun parseMonthRepeat(): Unit? {
        val match = Regex("每(?:(?:隔)?(?:(\\d+)个?)?|个)?月(?:\\s*(\\d{1,2})(?:号|日)?)?").find(text)
            ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        checkRepeatCount(count)
        state.repeat = RepeatRule(months = count)
        val day = match.groupValues[2].toIntOrNull()
        if (day != null) {
            state.date = safeDate(now.year, now.monthValue, day)
            state.hasDate = true
            state.hasAbsoluteDate = true
        }
        consume(match.range)
        return Unit
    }

    private fun parseWeekRepeat(): Unit? {
        val match = Regex("每(?:(?:隔)?(?:(\\d+)个?)?|个)?(?:周|星期|礼拜)(?:的)?(?:(?:周|星期|礼拜)?([1-7日天]))?").find(text)
            ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        checkRepeatCount(count)
        state.repeat = RepeatRule(weeks = count)
        match.groupValues[2].takeIf { it.isNotBlank() }?.let {
            state.date = nextWeekday(weekdayValue(it), allowToday = true)
            state.hasDate = true
        }
        consume(match.range)
        return Unit
    }

    private fun parseDayRepeat(): Unit? {
        val match = Regex("每(?:(?:隔)?(?:(\\d+)个?)?|个)?天").find(text) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        checkRepeatCount(count)
        state.repeat = RepeatRule(days = count)
        consume(match.range)
        return Unit
    }

    private fun parseHourRepeat() {
        val match = Regex("每(?:(?:隔)?(?:(\\d+)个?)?|个)?(?:小时|钟头)").find(text) ?: return
        val count = match.groupValues[1].toIntOrNull() ?: 1
        if (count <= 2) {
            throw ParseException("小时级重复需间隔2小时以上")
        }
        checkRepeatCount(count)
        state.repeat = RepeatRule(hours = count)
        consume(match.range)
    }

    private fun parseRelativeDuration() {
        val patterns = listOf(
            Regex("(\\d+)个?半(?:小时|钟头)后") to { m: MatchResult ->
                now.plusHours(m.groupValues[1].toLong()).plusMinutes(30)
            },
            Regex("半(?:个)?(?:小时|钟头)后") to { _: MatchResult ->
                now.plusMinutes(30)
            },
            Regex("(\\d+)(?:小时|钟头)(?:(\\d+)分(?:钟)?)?后") to { m: MatchResult ->
                now.plusHours(m.groupValues[1].toLong()).plusMinutes(m.groupValues[2].toLongOrNull() ?: 0)
            },
            Regex("(\\d+)分(?:钟)?(?:(\\d+)秒(?:钟)?)?后") to { m: MatchResult ->
                now.plusMinutes(m.groupValues[1].toLong()).plusSeconds(m.groupValues[2].toLongOrNull() ?: 0)
            },
            Regex("(\\d+)秒(?:钟)?后") to { m: MatchResult ->
                now.plusSeconds(m.groupValues[1].toLong())
            }
        )

        for ((pattern, builder) in patterns) {
            val match = pattern.find(text) ?: continue
            state.relativeInstant = builder(match)
            state.hasTime = true
            consume(match.range)
            return
        }

        Regex("(等会|一会|一会儿)").find(text)?.let {
            state.relativeInstant = now.plusMinutes(10)
            state.hasTime = true
            consume(it.range)
        }
    }

    private fun parseRelativeDate() {
        val fixed = listOf(
            "大后天" to 3L,
            "后天" to 2L,
            "明天" to 1L,
            "明日" to 1L,
            "明儿" to 1L,
            "今天" to 0L,
            "今晚" to 0L,
            "今早" to 0L,
            "明晚" to 1L,
            "明早" to 1L
        )
        for ((word, days) in fixed) {
            val index = text.indexOf(word)
            if (index >= 0) {
                state.date = now.toLocalDate().plusDays(days)
                state.hasDate = true
                consume(index until index + word.length)
                return
            }
        }

        Regex("(\\d+)天后").find(text)?.let {
            val days = it.groupValues[1].toLong()
            if (days > 1000) throw ParseException("时间跨度太大了")
            state.date = now.toLocalDate().plusDays(days)
            state.hasDate = true
            consume(it.range)
            return
        }

        Regex("(\\d+)个?星期后").find(text)?.let {
            val weeks = it.groupValues[1].toLong()
            if (weeks > 100) throw ParseException("时间跨度太大了")
            state.date = now.toLocalDate().plusWeeks(weeks)
            state.hasDate = true
            consume(it.range)
            return
        }

        Regex("(\\d+)个?月后").find(text)?.let {
            val months = it.groupValues[1].toLong()
            if (months > 100) throw ParseException("时间跨度太大了")
            state.date = now.toLocalDate().plusMonths(months)
            state.hasDate = true
            consume(it.range)
            return
        }

        Regex("下(?:个)?月(?:\\s*(\\d{1,2})(?:号|日)?)?").find(text)?.let {
            val base = now.toLocalDate().plusMonths(1)
            val day = it.groupValues[1].toIntOrNull() ?: base.dayOfMonth
            state.date = safeDate(base.year, base.monthValue, day)
            state.hasDate = true
            consume(it.range)
            return
        }

        Regex("(今年|明年|后年)(?:\\s*(\\d{1,2})月(\\d{1,2})(?:号|日)?)?").find(text)?.let {
            val delta = when (it.groupValues[1]) {
                "明年" -> 1
                "后年" -> 2
                else -> 0
            }
            val year = now.year + delta
            val month = it.groupValues[2].toIntOrNull()
            val day = it.groupValues[3].toIntOrNull()
            state.date = if (month != null && day != null) {
                safeDate(year, month, day)
            } else {
                now.toLocalDate().withYear(year)
            }
            state.hasDate = true
            state.hasAbsoluteDate = month != null && day != null
            consume(it.range)
        }
    }

    private fun parseAbsoluteDate() {
        val fullDate = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})(?:号|日)?").find(text)
            ?: Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").find(text)
        fullDate?.let {
            state.date = safeDate(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt()
            )
            state.hasDate = true
            state.hasAbsoluteDate = true
            consume(it.range)
            return
        }

        Regex("(?<!\\d)(\\d{1,2})月(\\d{1,2})(?:号|日)?").find(text)?.let {
            state.date = safeDate(now.year, it.groupValues[1].toInt(), it.groupValues[2].toInt())
            state.hasDate = true
            state.hasAbsoluteDate = true
            consume(it.range)
            return
        }

        Regex("(?<![\\d点时:])(\\d{1,2})(?:号|日)(?![\\d:])").find(text)?.let {
            state.date = safeDate(now.year, now.monthValue, it.groupValues[1].toInt())
            state.hasDate = true
            state.hasAbsoluteDate = true
            consume(it.range)
        }
    }

    private fun parseWeekday() {
        if (state.hasDate) return
        Regex("(下个?|下)?(?:周|星期|礼拜)([1-7日天])").find(text)?.let {
            if (rangeConsumed(it.range)) return
            val explicitNext = it.groupValues[1].isNotBlank()
            state.date = nextWeekday(weekdayValue(it.groupValues[2]), allowToday = !explicitNext)
            state.hasDate = true
            consume(it.range)
        }
    }

    private fun parseDayPeriod() {
        val periods = listOf(
            Regex("凌晨|半夜|夜里|深夜") to DayPeriod.NIGHT,
            Regex("早上|早晨|上午|今早|明早|早") to DayPeriod.MORNING,
            Regex("中午") to DayPeriod.NOON,
            Regex("下午") to DayPeriod.AFTERNOON,
            Regex("傍晚") to DayPeriod.AFTERNOON,
            Regex("晚上|今晚|明晚") to DayPeriod.EVENING
        )

        for ((pattern, period) in periods) {
            val match = pattern.find(text) ?: continue
            state.dayPeriod = period
            state.hasTime = true
            consume(match.range)
            return
        }
    }

    private fun parseClockTime() {
        val colonTime = Regex("(?<!\\d)(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?").find(text)
        if (colonTime != null) {
            applyClock(
                hour = colonTime.groupValues[1].toInt(),
                minute = colonTime.groupValues[2].toInt(),
                second = colonTime.groupValues[3].toIntOrNull() ?: 0
            )
            consume(colonTime.range)
            return
        }

        val pointTime = Regex("(\\d{1,2})(?:点钟|点整|点|时)(?:(\\d{1,2})(?:分钟|分)?|半(?=$|\\s|提醒|叫我|请|，|。|！|？|,|\\.)|(1刻)|(3刻))?(?:(\\d{1,2})秒(?:钟)?)?").find(text)
            ?: return
        val minute = when {
            pointTime.groupValues[2].isNotBlank() -> pointTime.groupValues[2].toInt()
            pointTime.groupValues[3].isNotBlank() -> 15
            pointTime.groupValues[4].isNotBlank() -> 45
            pointTime.value.contains("半") -> 30
            else -> 0
        }
        applyClock(
            hour = pointTime.groupValues[1].toInt(),
            minute = minute,
            second = pointTime.groupValues[5].toIntOrNull() ?: 0
        )
        consume(pointTime.range)
    }

    private fun applyClock(hour: Int, minute: Int, second: Int) {
        if (minute !in 0..59) throw ParseException("一小时没有${minute}分钟")
        if (second !in 0..59) throw ParseException("一分钟没有${second}秒")

        var finalHour = hour
        if (finalHour == 24) {
            finalHour = 0
            state.date = state.date.plusDays(1)
            state.hasDate = true
        }
        if (finalHour !in 0..23) throw ParseException("一天没有${hour}小时")

        when {
            state.dayPeriod in setOf(DayPeriod.AFTERNOON, DayPeriod.EVENING) && finalHour in 1..11 -> {
                finalHour += 12
            }
            state.dayPeriod == DayPeriod.EVENING && finalHour == 0 -> {
                state.date = state.date.plusDays(1)
                state.hasDate = true
            }
            state.dayPeriod == DayPeriod.NIGHT && finalHour == 0 && !state.hasDate -> {
                state.date = state.date.plusDays(1)
                state.hasDate = true
            }
            state.dayPeriod == null && !state.hasDate && state.repeat == null && now.hour >= 12 && finalHour in 1..11 -> {
                finalHour += 12
            }
        }

        state.hour = finalHour
        state.minute = minute
        state.second = second
        state.hasTime = true
    }

    private fun buildReminderTime(): LocalDateTime {
        state.relativeInstant?.let { return it }

        val defaultHour = when (state.dayPeriod) {
            DayPeriod.NIGHT -> 0
            DayPeriod.MORNING -> 8
            DayPeriod.NOON -> 12
            DayPeriod.AFTERNOON -> if (text.contains("傍晚")) 18 else 13
            DayPeriod.EVENING -> 20
            null -> 8
        }

        val time = LocalTime.of(state.hour ?: defaultHour, state.minute, state.second)
        return LocalDateTime.of(state.date, time)
    }

    private fun adjustFirstRun(parsedTime: LocalDateTime): LocalDateTime {
        val repeat = state.repeat
        var result = parsedTime

        if (repeat != null && repeat.isRepeating()) {
            while (!result.isAfter(now)) {
                result = when {
                    repeat.years > 0 -> result.plusYears(repeat.years.toLong())
                    repeat.months > 0 -> result.plusMonths(repeat.months.toLong())
                    repeat.weeks > 0 -> result.plusWeeks(repeat.weeks.toLong())
                    repeat.days > 0 -> result.plusDays(repeat.days.toLong())
                    repeat.hours > 0 -> now.plusHours(repeat.hours.toLong())
                    repeat.minutes > 0 -> now.plusMinutes(repeat.minutes.toLong())
                    repeat.seconds > 0 -> now.plusSeconds(repeat.seconds.toLong())
                    else -> result
                }
            }
            return result
        }

        if (!result.isAfter(now)) {
            if (state.hasAbsoluteDate) {
                throw ParseException("${format(result)}已经过去了，请重设一个将来的提醒")
            }
            while (!result.isAfter(now)) {
                result = result.plusDays(1)
            }
        }
        return result
    }

    private fun extractEvent(): String {
        val consumedChars = BooleanArray(text.length)
        state.consumed.forEach { range ->
            for (index in range) {
                if (index in consumedChars.indices) consumedChars[index] = true
            }
        }

        val remaining = buildString {
            text.forEachIndexed { index, char ->
                if (!consumedChars[index]) append(char)
            }
        }

        return remaining
            .replace(Regex("提醒我|提醒|叫我|准时|是"), "")
            .replace(Regex("^请"), "")
            .replace(Regex("[\\s的了在，。！？、,.?！“”\"'（）()]"), "")
            .trim()
    }

    private fun nextWeekday(targetWeekday: Int, allowToday: Boolean): LocalDate {
        val current = now.dayOfWeek.value
        var delta = (targetWeekday - current + 7) % 7
        if (delta == 0 && !allowToday) delta = 7
        if (delta == 0 && allowToday) {
            // If we have a parsed hour, use it to check if today is still valid
            // If state.hour is null, we can't determine yet - allow today and let adjustFirstRun handle it
            if (state.hour != null) {
                val targetHour = state.hour!!
                val targetMinute = state.minute ?: 0
                val sameDayTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(targetHour, targetMinute))
                if (!sameDayTime.isAfter(now)) delta = 7
            }
            // If state.hour is null, keep delta = 0 (today)
        }
        return now.toLocalDate().plusDays(delta.toLong())
    }

    private fun weekdayValue(value: String): Int = when (value) {
        "1" -> 1
        "2" -> 2
        "3" -> 3
        "4" -> 4
        "5" -> 5
        "6" -> 6
        "日", "天", "7" -> 7
        else -> throw ParseException("一周没有${value}天")
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            throw ParseException("日期超出范围")
        }
    }

    private fun checkRepeatCount(count: Int) {
        if (count <= 0 || count > 100) throw ParseException("时间跨度太大了")
    }

    private fun consume(range: IntRange) {
        state.consumed += range
    }

    private fun consume(range: Iterable<Int>) {
        val values = range.toList()
        if (values.isNotEmpty()) state.consumed += values.first()..values.last()
    }

    private fun rangeConsumed(range: IntRange): Boolean =
        state.consumed.any { existing -> range.first <= existing.last && existing.first <= range.last }

    private fun format(time: LocalDateTime): String {
        return try {
            time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (_: DateTimeParseException) {
            time.toString()
        }
    }
}
