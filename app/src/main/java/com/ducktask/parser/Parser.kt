package com.ducktask.parser

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class ParsedResult(
    val time: LocalDateTime,
    val event: String,
    val repeat: RepeatRule? = null
)

data class RepeatRule(
    val years: Int = 0,
    val months: Int = 0,
    val weeks: Int = 0,
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0
) {
    fun hasRepeat(): Boolean = years > 0 || months > 0 || weeks > 0 || days > 0 || hours > 0 || minutes > 0
}

class ParseException(message: String) : Exception(message)

object TimeParser {
    private val CN_NUM = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '〇' to 0, '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4,
        '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9,
        '两' to 2
    )

    private val CN_UNIT = mapOf(
        '十' to 10, '拾' to 10, '百' to 100, '佰' to 100,
        '千' to 1000, '仟' to 1000, '万' to 10000, '萬' to 10000
    )

    fun parse(text: String): ParsedResult {
        val parser = LocalParser(text, CN_NUM, CN_UNIT)
        return parser.parse()
    }
}

private class LocalParser(
    private val text: String,
    private val cnNum: Map<Char, Int>,
    private val cnUnit: Map<Char, Int>
) {
    private val now = LocalDateTime.now()
    private var idx = 0
    private var afternoon: Boolean? = null
    private var event = ""
    private val timeFields = mutableMapOf<String, Int>()
    private val timeDeltaFields = mutableMapOf<String, Long>()
    private val repeat = mutableMapOf<String, Int>()

    private val words: List<String>

    init {
        words = tokenize(text)
    }

    private fun tokenize(text: String): List<String> {
        val processed = parseCnNumber(text)
        return processed
            .replace("年", " 年 ")
            .replace("月", " 月 ")
            .replace("日", " 日 ")
            .replace("号", " 号 ")
            .replace("点", " 点 ")
            .replace("分", " 分 ")
            .replace("秒", " 秒 ")
            .replace("周", " 周 ")
            .replace("星期", " 周 ")
            .replace("礼拜", " 周 ")
            .replace("的", " ")
            .replace("提醒", " ")
            .replace("后", " 后 ")
            .replace("每", " 每 ")
            .replace("隔", " 隔 ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun parseCnNumber(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            val num = cnNum[char]
            if (num != null) {
                var numVal = num
                var j = i + 1
                while (j < text.length) {
                    val unit = cnUnit[text[j]]
                    if (unit != null) {
                        if (unit == 10 && numVal < 10) {
                            numVal *= unit
                        } else {
                            numVal += unit
                        }
                        j++
                    } else {
                        break
                    }
                }
                result.append(numVal)
                i = j
            } else {
                result.append(char)
                i++
            }
        }
        return result.toString()
    }

    fun parse(): ParsedResult {
        while (idx < words.size) {
            val beginning = idx

            consumeRepeat()
            consumeYearPeriod() || consumeMonthPeriod() || consumeDayPeriod()
            consumeWeekdayPeriod() || consumeHourPeriod() || consumeMinutePeriod() || consumeSecondPeriod()
            consumeYear() || consumeMonth() || consumeDay()
            consumeHour()

            if (idx != beginning) {
                consumeWord("准时", "是")
                if (consumeWord("提醒")) {
                    consumeWord("我")
                }
                consumeToEnd()

                try {
                    var resultTime = now

                    if (timeDeltaFields.containsKey("years")) {
                        resultTime = resultTime.plusYears(timeDeltaFields["years"]!!)
                    }
                    if (timeDeltaFields.containsKey("months")) {
                        resultTime = resultTime.plusMonths(timeDeltaFields["months"]!!)
                    }
                    if (timeDeltaFields.containsKey("weeks")) {
                        resultTime = resultTime.plusWeeks(timeDeltaFields["weeks"]!!)
                    }
                    if (timeDeltaFields.containsKey("days")) {
                        resultTime = resultTime.plusDays(timeDeltaFields["days"]!!)
                    }
                    if (timeDeltaFields.containsKey("hours")) {
                        resultTime = resultTime.plusHours(timeDeltaFields["hours"]!!)
                    }
                    if (timeDeltaFields.containsKey("minutes")) {
                        resultTime = resultTime.plusMinutes(timeDeltaFields["minutes"]!!)
                    }
                    if (timeDeltaFields.containsKey("seconds")) {
                        resultTime = resultTime.plusSeconds(timeDeltaFields["seconds"]!!)
                    }

                    if (timeFields.containsKey("year")) {
                        resultTime = resultTime.withYear(timeFields["year"]!!)
                    }
                    if (timeFields.containsKey("month")) {
                        resultTime = resultTime.withMonth(timeFields["month"]!!)
                    }
                    if (timeFields.containsKey("day")) {
                        resultTime = resultTime.withDayOfMonth(timeFields["day"]!!)
                    }
                    if (timeFields.containsKey("hour")) {
                        resultTime = resultTime.withHour(timeFields["hour"]!!)
                    }
                    if (timeFields.containsKey("minute")) {
                        resultTime = resultTime.withMinute(timeFields["minute"]!!)
                    }
                    if (timeFields.containsKey("second")) {
                        resultTime = resultTime.withSecond(timeFields["second"]!!)
                    }

                    if (resultTime.isBefore(now)) {
                        if (timeFields.containsKey("day") || timeFields.containsKey("month") || timeFields.containsKey("year")) {
                            throw ParseException("时间已经是过去时了，请重新设置一个将来的时间")
                        }
                        resultTime = resultTime.plusDays(1)
                    }

                    val repeatRule = if (repeat.isNotEmpty()) {
                        RepeatRule(
                            years = repeat["year"] ?: 0,
                            months = repeat["month"] ?: 0,
                            weeks = repeat["week"] ?: 0,
                            days = repeat["day"] ?: 0,
                            hours = repeat["hour"] ?: 0,
                            minutes = repeat["minute"] ?: 0
                        )
                    } else null

                    return ParsedResult(
                        time = resultTime,
                        event = event.ifBlank { "提醒" },
                        repeat = repeatRule
                    )
                } catch (e: Exception) {
                    if (e is ParseException) throw e
                    throw ParseException("时间或日期超出范围")
                }
            } else {
                idx++
            }
        }
        throw ParseException("无法理解输入，请尝试更明确的表达，如'明天下午3点提醒我开会'")
    }

    private fun consumeRepeat(): Boolean {
        val beginning = idx
        if (consumeWord("每", "每隔")) {
            consumeWord("间隔")
            val repeatCount = consumeDigit() ?: 1
            consumeWord("个")

            when {
                consumeWord("年") && consumeMonth() -> {
                    repeat["year"] = repeatCount
                    return true
                }
                consumeWord("月") && consumeDay() -> {
                    repeat["month"] = repeatCount
                    return true
                }
                consumeWord("天") -> {
                    repeat["day"] = repeatCount
                    if (!consumeHour()) {
                        timeFields["hour"] = 8
                        timeFields["minute"] = 0
                    }
                    return true
                }
                currentWord() in listOf("周", "星期") -> {
                    if (peekNextWord() in listOf("周", "星期")) {
                        advance()
                    }
                    if (consumeWeekdayPeriod()) {
                        repeat["week"] = repeatCount
                        return true
                    }
                }
                consumeWord("小时") -> {
                    consumeMinute()
                    if (repeatCount < 12) {
                        throw ParseException("每小时重复太频繁了，至少需要间隔12小时")
                    }
                    repeat["hour"] = repeatCount
                    return true
                }
                consumeWord("分", "分钟") -> {
                    throw ParseException("暂不支持分钟级重复提醒")
                }
                consumeWord("工作日") -> {
                    throw ParseException("暂不支持工作日提醒，请使用'每天'试试")
                }
            }
        }
        idx = beginning
        return false
    }

    private fun consumeYearPeriod(): Boolean {
        val beginning = idx
        when {
            consumeWord("今年") -> timeDeltaFields["years"] = 0
            consumeWord("明年") -> timeDeltaFields["years"] = 1
            consumeWord("后年") -> timeDeltaFields["years"] = 2
            else -> {
                val tmp = consumeDigit()
                if (tmp != null && consumeWord("年") && peekNextWord() in listOf("后", "以后")) {
                    advance()
                    timeDeltaFields["years"] = tmp.toLong()
                }
            }
        }

        if (!timeDeltaFields.containsKey("years")) {
            idx = beginning
            return false
        }

        if ((timeDeltaFields["years"] ?: 0) >= 100) {
            throw ParseException("时间跨度太大了，恕我不能保证这么久之后的服务")
        }
        consumeWord("的")
        consumeMonth()
        return true
    }

    private fun consumeMonthPeriod(): Boolean {
        val beginning = idx
        when {
            consumeWord("下个月", "下月") -> timeDeltaFields["months"] = 1
            currentWord().isNotEmpty() && currentWord().first().isDigit() -> {
                val tmp = consumeDigit()
                consumeWord("个")
                if (currentWord() == "月" && peekNextWord() in listOf("后", "以后")) {
                    advance()
                    timeDeltaFields["months"] = (tmp ?: 0).toLong()
                }
            }
        }

        if (!timeDeltaFields.containsKey("months")) {
            idx = beginning
            return false
        }

        if ((timeDeltaFields["months"] ?: 0) > 100) {
            throw ParseException("时间跨度太大了")
        }
        consumeWord("的")
        consumeDay()
        return true
    }

    private fun consumeDayPeriod(): Boolean {
        val beginning = idx
        var hasHour = false
        var hour = 8
        var days: Long? = null

        when {
            consumeWord("今天") -> days = 0
            consumeWord("今晚") -> {
                days = 0
                afternoon = true
                hour = 20
            }
            consumeWord("明早", "明天") -> {
                days = 1
                afternoon = false
            }
            consumeWord("明晚") -> {
                days = 1
                afternoon = true
                hour = 20
            }
            consumeWord("后天") -> days = 2
            consumeWord("大后天") -> days = 3
            else -> {
                val tmp = consumeDigit()
                if (tmp != null && consumeWord("天")) {
                    if (consumeWord("后", "以后")) {
                        days = tmp.toLong()
                    } else if (consumeHourPeriod()) {
                        days = tmp.toLong()
                        hasHour = true
                    }
                }
            }
        }

        if (days == null) {
            idx = beginning
            return false
        }

        if (days > 1000) {
            throw ParseException("时间跨度太大了")
        }
        timeDeltaFields["days"] = days

        if (consumeWord("(") || consumeWord("（")) {
            if (consumeWord("周", "星期")) {
                if (!consumeWord("日", "天")) consumeDigit()
            }
            consumeWord(")") || consumeWord("）")
        }

        if (!hasHour && !consumeHour()) {
            timeFields["hour"] = hour
            timeFields["minute"] = (0..3).random()
        }
        return true
    }

    private fun consumeWeekdayPeriod(): Boolean {
        val beginning = idx
        var weekday: Int? = null
        var weekDelta = 0

        if (consumeWord("周", "下周", "下个周", "星期", "下星期", "下个星期", "礼拜", "下礼拜", "下个礼拜")) {
            if (consumeWord("日", "天")) {
                weekday = 6
            } else {
                val d = consumeDigit(consume = false)
                if (d != null && d in 1..6) {
                    consumeDigit()
                    weekday = d - 1
                    if (now.dayOfWeek.value % 7 == weekday) {
                        weekDelta = 1
                    }
                }
            }
        } else if (currentWord().isNotEmpty() && currentWord().first().isDigit()) {
            val tmp = consumeDigit()
            consumeWord("个")
            if (currentWord() in listOf("周", "星期", "礼拜") && peekNextWord() in listOf("后", "以后")) {
                advance()
                weekDelta = tmp ?: 0
            }
        }

        when {
            weekday != null -> {
                timeDeltaFields["weekday"] = weekday.toLong()
                timeDeltaFields["days"] = 1
            }
            weekDelta > 0 -> {
                if (weekDelta > 100) {
                    throw ParseException("时间跨度太大了")
                }
                timeDeltaFields["weeks"] = weekDelta.toLong()
            }
            else -> {
                idx = beginning
                return false
            }
        }

        if (!consumeHour()) {
            timeFields["hour"] = 8
            timeFields["minute"] = 0
        }
        return true
    }

    private fun consumeHourPeriod(): Boolean {
        val beginning = idx
        var hours = 0
        var minutes = 0

        when {
            currentWord().isNotEmpty() && currentWord().first().isDigit() -> {
                val tmp = consumeDigit()
                consumeWord("个")
                if (consumeWord("半小时")) {
                    if (consumeWord("后", "以后")) {
                        hours = tmp ?: 0
                        minutes = 30
                    }
                } else if (consumeWord("小时", "钟头")) {
                    if (consumeWord("后", "以后") || consumeMinutePeriod()) {
                        hours = tmp ?: 0
                    }
                }
            }
            consumeWord("半小时") || (consumeWord("半个") && consumeWord("小时", "钟头")) -> {
                if (consumeWord("后", "以后")) {
                    minutes = 30
                }
            }
        }

        if (hours == 0 && minutes == 0) {
            idx = beginning
            return false
        }

        if (hours > 100) {
            throw ParseException("时间跨度太大了")
        }

        if (hours > 0) timeDeltaFields["hours"] = hours.toLong()
        if (minutes > 0) timeDeltaFields["minutes"] = minutes.toLong()
        return true
    }

    private fun consumeMinutePeriod(): Boolean {
        val beginning = idx
        val minuteDelta = consumeDigit()

        if (minuteDelta != null) {
            if (consumeWord("分", "分钟")) {
                consumeSecondPeriod()
                consumeWord("后", "以后")
                if (minuteDelta > 1000) {
                    throw ParseException("时间跨度太大了")
                }
                timeDeltaFields["minutes"] = minuteDelta.toLong()
                return true
            }
        } else if (consumeWord("等会", "一会", "一会儿")) {
            timeDeltaFields["minutes"] = 10
            return true
        }

        idx = beginning
        return false
    }

    private fun consumeSecondPeriod(): Boolean {
        val beginning = idx
        val secondDelta = consumeDigit()

        if (secondDelta != null) {
            if (consumeWord("秒", "秒钟")) {
                consumeWord("后", "以后")
                if (secondDelta > 10000) {
                    throw ParseException("时间跨度太大了")
                }
                timeDeltaFields["seconds"] = secondDelta.toLong()
                return true
            }
        }
        idx = beginning
        return false
    }

    private fun consumeYear(): Boolean {
        val beginning = idx
        val year = consumeDigit()

        if (year == null || !consumeWord("年", "-", "/", ".")) {
            idx = beginning
            return false
        }

        if (consumeMonth()) {
            if (year > 3000) {
                throw ParseException("恕不能保证公元${year}年的服务")
            }
            if (year < now.year) {
                throw ParseException("请设置一个今年的日期")
            }
            timeFields["year"] = year
            return true
        }
        return false
    }

    private fun consumeMonth(): Boolean {
        val beginning = idx

        if (consumeWord("农历", "阴历")) {
            throw ParseException("暂不支持农历提醒")
        }
        if (consumeWord("工作日")) {
            throw ParseException("暂不支持工作日提醒")
        }

        val month = consumeDigit()
        if (month == null || !consumeWord("月", "-", "/", ".")) {
            idx = beginning
            return false
        }

        if (month > 12) {
            throw ParseException("一年没有${month}个月")
        }

        if (consumeDay()) {
            timeFields["month"] = month
            return true
        }
        idx = beginning
        return false
    }

    private fun consumeDay(): Boolean {
        val beginning = idx
        val day = consumeDigit()

        if (day == null || (!consumeWord("日", "号") && beginning == idx)) {
            idx = beginning
            return false
        }

        if (day > 31) {
            throw ParseException("一个月没有${day}天")
        }

        timeFields["day"] = day
        consumeWord("(", "（")
        if (consumeWord("周", "星期")) {
            if (!consumeWord("日", "天")) consumeDigit()
        }
        consumeWord(")", "）")

        if (!consumeHour()) {
            timeFields["hour"] = 8
            timeFields["minute"] = 0
        }
        return true
    }

    private fun consumeHour(): Boolean {
        val beginning = idx

        when {
            consumeWord("凌晨", "半夜", "夜里", "深夜") -> {
                afternoon = false
                timeDeltaFields["days"] = (timeDeltaFields["days"] ?: 0) + 1
                timeFields["hour"] = 0
                timeFields["minute"] = 0
            }
            consumeWord("早", "早上", "早晨", "今早", "上午") -> {
                afternoon = false
                timeFields["hour"] = 8
                timeFields["minute"] = 0
            }
            consumeWord("中午") -> {
                timeFields["hour"] = 12
                timeFields["minute"] = 0
            }
            consumeWord("下午") -> {
                afternoon = true
                timeFields["hour"] = 13
                timeFields["minute"] = 0
            }
            consumeWord("傍晚") -> {
                afternoon = true
                timeFields["hour"] = 18
                timeFields["minute"] = 0
            }
            consumeWord("晚上", "今晚") -> {
                afternoon = true
                timeFields["hour"] = 20
                timeFields["minute"] = 0
            }
        }

        val beginning2 = idx
        val hour = consumeDigit()

        if (hour == null || !consumeWord("点", "点钟", "点整", ":", "：", ".", "時", "时")) {
            idx = beginning2
            return idx != beginning
        }

        if (afternoon == true && hour == 0) {
            timeDeltaFields["days"] = (timeDeltaFields["days"] ?: 0) + 1
        } else if (hour < 12) {
            if (afternoon == true || (now.hour >= 12 && !timeFields.containsKey("hour")
                        && !timeDeltaFields.containsKey("days") && repeat.isEmpty())) {
                timeFields["hour"] = hour + 12
            } else {
                timeFields["hour"] = hour
            }
        } else {
            timeFields["hour"] = hour
        }

        if (!consumeMinute()) {
            timeFields["minute"] = 0
        }

        return true
    }

    private fun consumeMinute(): Boolean {
        val beginning = idx
        val minute = consumeDigit()

        if (minute != null) {
            if (minute !in 0..60) {
                throw ParseException("一小时没有${minute}分钟")
            }
            timeFields["minute"] = minute
            consumeWord("分", "分钟", ":")
            consumeSecond()
        } else if (consumeWord("半")) {
            timeFields["minute"] = 30
        } else if (currentWord() == "1" && peekNextWord() == "刻") {
            advance(2)
            timeFields["minute"] = 15
        } else if (currentWord() == "3" && peekNextWord() == "刻") {
            advance(2)
            timeFields["minute"] = 45
        }

        return idx != beginning
    }

    private fun consumeSecond(): Boolean {
        val beginning = idx
        val second = consumeDigit()

        if (second != null) {
            if (consumeWord("秒", "秒钟")) {
                if (second !in 0..60) {
                    throw ParseException("一分钟没有${second}秒")
                }
                timeFields["second"] = second
                return true
            }
        }
        idx = beginning
        return false
    }

    private fun consumeToEnd() {
        event = words.drop(idx).joinToString("").replace(Regex("[的。，、！？]"), "").trim()
    }

    private fun consumeWord(vararg words: String): Boolean {
        if (currentWord() in words) {
            advance()
            return true
        }
        return false
    }

    private fun consumeDigit(consume: Boolean = true): Int? {
        val word = currentWord()
        if (word.isNotEmpty() && word.first().isDigit()) {
            val digit = word.toInt()
            if (consume) advance()
            return digit
        }
        return null
    }

    private fun currentWord(): String = words.getOrElse(idx) { "" }

    private fun peekNextWord(): String = words.getOrElse(idx + 1) { "" }

    private fun advance(step: Int = 1) {
        idx += step
    }
}
