package com.ducktask.parser

import java.time.LocalDateTime

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

    // Token types
    private data class Token(val word: String, val isDigit: Boolean)
    private val words: List<Token>

    init {
        words = tokenize(text)
    }

    // Multi-character date/time words that should be single tokens
    private val PERIOD_WORDS = setOf(
        "今天", "明天", "后天", "大后天",
        "今早", "今晚", "明早", "明晚",
        "昨晚", "今晚", "今晨", "今午",
        "凌晨", "夜里", "深夜", "半夜",
        "早上", "早晨", "上午", "中午",
        "下午", "傍晚", "晚上",
        "周一", "周二", "周三", "周四", "周五", "周六", "周日",
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
        "礼拜一", "礼拜二", "礼拜三", "礼拜四", "礼拜五", "礼拜六", "礼拜日"
    )

    // Single character period markers
    private val SINGLE_PERIOD_CHARS = setOf('年', '月', '周', '日', '号', '点', '时', '分', '秒')

    private fun tokenize(text: String): List<Token> {
        val processed = parseCnNumber(text)
        val tokens = mutableListOf<Token>()

        var i = 0
        while (i < processed.length) {
            val char = processed[i]

            when {
                // Skip these characters
                char in "的的" -> {
                    i++
                }
                // Single character time/date units
                char in SINGLE_PERIOD_CHARS -> {
                    tokens.add(Token(char.toString(), false))
                    i++
                }
                // Skip 提醒
                i + 2 < processed.length && processed.substring(i, i + 3) == "提醒" -> {
                    i += 3
                }
                // Skip 提醒我
                i + 3 < processed.length && processed.substring(i, i + 4) == "提醒我" -> {
                    i += 4
                }
                // Skip 隔
                char == '隔' -> {
                    i++
                }
                // Skip 后 (relative)
                char == '后' -> {
                    i++
                }
                // Skip 每 when not part of 每周 etc.
                char == '每' && (i + 1 >= processed.length || processed[i + 1] !in "年月周天") -> {
                    i++
                }
                // Check for multi-character period words
                else -> {
                    var matched = false
                    // Try longest match first (3 chars, then 2)
                    for (len in 3 downTo 2) {
                        if (i + len <= processed.length) {
                            val word = processed.substring(i, i + len)
                            if (word in PERIOD_WORDS) {
                                tokens.add(Token(word, false))
                                i += len
                                matched = true
                                break
                            }
                        }
                    }
                    if (!matched) {
                        // Digits - collect consecutive digits
                        if (char.isDigit() || (char == '-' && i + 1 < processed.length && processed[i + 1].isDigit())) {
                            val start = i
                            if (processed[i] == '-') i++
                            while (i < processed.length && processed[i].isDigit()) {
                                i++
                            }
                            tokens.add(Token(processed.substring(start, i), true))
                        }
                        // Chinese numbers
                        else if (cnNum.containsKey(char)) {
                            tokens.add(Token(char.toString(), true))
                            i++
                        }
                        // Other characters - skip
                        else {
                            i++
                        }
                    }
                }
            }
        }

        return tokens
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

            // Try to parse in order: repeat -> periods -> weekday -> hour -> etc.
            val consumedRepeat = consumeRepeat()

            // Parse day/date periods first
            val consumedPeriod = consumeYearPeriod() || consumeMonthPeriod() || consumeDayPeriod()

            // Parse weekday
            val consumedWeekday = consumeWeekdayPeriod()

            // Parse hour/minute periods (relative like "2小时后")
            val consumedRelative = consumeHourPeriod() || consumeMinutePeriod() || consumeSecondPeriod()

            // Parse explicit time
            val consumedTime = consumeYear() || consumeMonth() || consumeDay() || consumeHour()

            if (idx != beginning || consumedRepeat || consumedPeriod || consumedWeekday || consumedRelative || consumedTime) {
                // Time was found or repeat was set
                consumeWord("准时", "是")

                // Skip "提醒" and "我"
                consumeWord("提醒")
                consumeWord("我")

                // Get remaining text as event
                consumeToEnd()

                try {
                    var resultTime = buildResultTime()

                    // Check if time is in the past
                    if (resultTime.isBefore(now)) {
                        // If explicit date was given, it's an error
                        if (timeFields.containsKey("day") || timeFields.containsKey("month") || timeFields.containsKey("year")) {
                            throw ParseException("时间已经是过去时了，请重新设置一个将来的时间")
                        }
                        // For relative times without explicit date, add a day
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

    private fun buildResultTime(): LocalDateTime {
        var resultTime = now

        // Apply time deltas (relative offsets)
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

        // Apply explicit time fields
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

        return resultTime
    }

    private fun consumeRepeat(): Boolean {
        val beginning = idx
        if (consumeWord("每")) {
            var repeatCount = 1
            if (currentIsDigit()) {
                repeatCount = consumeDigit()!!
            }

            when {
                consumeWord("年") -> {
                    repeat["year"] = repeatCount
                    consumeMonth()
                    return true
                }
                consumeWord("月") -> {
                    repeat["month"] = repeatCount
                    consumeDay()
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
                // Handle 周六, 周日 etc. (multi-char weekday after 周)
                currentWord().startsWith("周") && currentWord().length > 1 -> {
                    repeat["week"] = repeatCount
                    consumeWeekdayNumber()
                    return true
                }
                consumeWord("周") || consumeWord("星期") || consumeWord("礼拜") -> {
                    repeat["week"] = repeatCount
                    consumeWeekdayNumber()
                    return true
                }
                consumeWord("小时") || consumeWord("钟头") -> {
                    if (repeatCount < 12) {
                        throw ParseException("每小时重复太频繁了，至少需要间隔12小时")
                    }
                    repeat["hour"] = repeatCount
                    consumeMinute()
                    return true
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
                if (tmp != null && consumeWord("年") && peekWord() == "后") {
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
            throw ParseException("时间跨度太大了")
        }
        consumeWord("的")
        consumeMonth()
        return true
    }

    private fun consumeMonthPeriod(): Boolean {
        val beginning = idx
        when {
            consumeWord("下个月") || consumeWord("下月") -> timeDeltaFields["months"] = 1
            currentIsDigit() -> {
                val tmp = consumeDigit()
                if (consumeWord("个") && consumeWord("月") && peekWord() == "后") {
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
        var hour = 8
        var days: Long? = null

        when {
            consumeWord("今天") -> days = 0
            consumeWord("今晚") -> {
                days = 0
                afternoon = true
                hour = 20
            }
            consumeWord("明早") || consumeWord("明天") -> {
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
            currentIsDigit() -> {
                val tmp = consumeDigit()
                if (tmp != null && consumeWord("天")) {
                    if (consumeWord("后") || consumeWord("以后")) {
                        days = tmp.toLong()
                    } else if (consumeHourPeriod()) {
                        days = tmp.toLong()
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

        // Skip weekday in parentheses like "(周四)"
        if (consumeWord("(") || consumeWord("（")) {
            consumeWord("周")
            if (!consumeWord("日") && !consumeWord("天")) {
                consumeDigit()
            }
            consumeWord(")") || consumeWord("）")
        }

        if (!consumeHour()) {
            timeFields["hour"] = hour
            // Add some randomness to avoid all reminders firing at the same minute
            timeFields["minute"] = (0..3).random()
        }
        return true
    }

    private fun consumeWeekdayPeriod(): Boolean {
        val beginning = idx
        var weekday: Int? = null

        // Handle multi-char weekday tokens like "周六", "周一" etc.
        val word = currentWord()
        if (word.length == 2 && word[0] == '周') {
            val weekdayMap = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
            weekdayMap[word[1]]?.let {
                weekday = it
                advance()
                // If today is the same weekday, schedule for next week
                if (now.dayOfWeek.value == it) {
                    timeDeltaFields["days"] = 7
                }
            }
        } else if (consumeWord("周") || consumeWord("星期") || consumeWord("礼拜")) {
            if (consumeWord("日") || consumeWord("天")) {
                weekday = 7  // Sunday
            } else if (currentIsDigit()) {
                val d = consumeDigit()!!
                if (d in 1..7) {
                    weekday = d
                    // If today is the same weekday, schedule for next week
                    if (now.dayOfWeek.value == d) {
                        timeDeltaFields["days"] = 7
                    }
                }
            }
        }

        if (weekday != null) {
            timeDeltaFields["weekday"] = weekday!!.toLong()
            if (!timeDeltaFields.containsKey("days")) {
                timeDeltaFields["days"] = 1
            }
            if (!consumeHour()) {
                timeFields["hour"] = 8
                timeFields["minute"] = 0
            }
            return true
        }

        idx = beginning
        return false
    }

    private fun consumeWeekdayNumber() {
        // Already consumed "周"/"星期"/"礼拜" before calling this
        // Or the full weekday token like "周六" is still intact
        val word = currentWord()

        // Handle full weekday tokens like "周六", "周日", "周一" etc.
        if (word.length == 2 && word[0] == '周') {
            val weekdayMap = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
            weekdayMap[word[1]]?.let {
                timeDeltaFields["weekday"] = it.toLong()
                advance()
                return
            }
        }

        if (consumeWord("日") || consumeWord("天")) {
            // Sunday = 7
            timeDeltaFields["weekday"] = 7L
        } else if (currentIsDigit()) {
            val d = consumeDigit()!!
            if (d in 1..7) {
                // Store weekday for later calculation
                timeDeltaFields["weekday"] = d.toLong()
            }
        }
    }

    private fun consumeHourPeriod(): Boolean {
        val beginning = idx

        // "半小时后" or "半个小时后"
        if (consumeWord("半") && (consumeWord("小时") || consumeWord("钟头"))) {
            if (consumeWord("后") || consumeWord("以后")) {
                timeDeltaFields["minutes"] = 30
                return true
            }
        }

        // "2小时后" or "2个半小时后"
        if (currentIsDigit()) {
            val tmp = consumeDigit()!!
            if (consumeWord("个") && consumeWord("半") && (consumeWord("小时") || consumeWord("钟头"))) {
                if (consumeWord("后") || consumeWord("以后")) {
                    timeDeltaFields["hours"] = tmp.toLong()
                    timeDeltaFields["minutes"] = 30
                    return true
                }
            } else if (consumeWord("小时") || consumeWord("钟头")) {
                if (consumeWord("后") || consumeWord("以后") || consumeMinutePeriod()) {
                    timeDeltaFields["hours"] = tmp.toLong()
                    return true
                }
            }
        }

        idx = beginning
        return false
    }

    private fun consumeMinutePeriod(): Boolean {
        val beginning = idx
        val minuteDelta = consumeDigit()

        if (minuteDelta != null) {
            if (consumeWord("分") || consumeWord("分钟")) {
                consumeWord("后") || consumeWord("以后")
                if (minuteDelta > 1000) {
                    throw ParseException("时间跨度太大了")
                }
                timeDeltaFields["minutes"] = minuteDelta.toLong()
                return true
            }
        } else if (consumeWord("等会") || consumeWord("一会") || consumeWord("一会儿")) {
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
            if (consumeWord("秒") || consumeWord("秒钟")) {
                consumeWord("后") || consumeWord("以后")
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

        if (year == null || !consumeWord("年")) {
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

        if (consumeWord("农历") || consumeWord("阴历")) {
            throw ParseException("暂不支持农历提醒")
        }
        if (consumeWord("工作日")) {
            throw ParseException("暂不支持工作日提醒")
        }

        val month = consumeDigit()
        if (month == null || !consumeWord("月")) {
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

        if (day == null || (!consumeWord("日") && !consumeWord("号"))) {
            idx = beginning
            return false
        }

        if (day > 31) {
            throw ParseException("一个月没有${day}天")
        }

        timeFields["day"] = day

        // Skip weekday in parentheses
        if (consumeWord("(") || consumeWord("（")) {
            if (consumeWord("周")) {
                consumeWord("日") || consumeWord("天") || consumeDigit()
            }
            consumeWord(")") || consumeWord("）")
        }

        if (!consumeHour()) {
            timeFields["hour"] = 8
            timeFields["minute"] = 0
        }
        return true
    }

    private fun consumeHour(): Boolean {
        val beginning = idx

        // Time of day keywords
        when {
            consumeWord("凌晨") || consumeWord("半夜") || consumeWord("夜里") || consumeWord("深夜") -> {
                afternoon = false
                timeDeltaFields["days"] = (timeDeltaFields["days"] ?: 0) + 1
                timeFields["hour"] = 0
                timeFields["minute"] = 0
            }
            consumeWord("早") || consumeWord("早上") || consumeWord("早晨") || consumeWord("今早") || consumeWord("上午") -> {
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
            consumeWord("晚上") || consumeWord("今晚") -> {
                afternoon = true
                timeFields["hour"] = 20
                timeFields["minute"] = 0
            }
        }

        val beginning2 = idx
        val hour = consumeDigit()

        if (hour == null || !consumeWord("点") && !consumeWord("点钟") && !consumeWord("点整")) {
            idx = beginning2
            return idx != beginning
        }

        // Handle afternoon logic
        if (afternoon == true && hour == 0) {
            // "晚上零点" means next day 0:00
            timeDeltaFields["days"] = (timeDeltaFields["days"] ?: 0) + 1
        } else if (hour < 12) {
            // If it's currently afternoon and no explicit time context, add 12 hours
            if (afternoon == true || (now.hour >= 12 && !timeFields.containsKey("hour")
                        && !timeDeltaFields.containsKey("days") && repeat.isEmpty())) {
                timeFields["hour"] = hour + 12
            } else {
                timeFields["hour"] = hour
            }
        } else {
            timeFields["hour"] = hour
        }

        // Parse minutes
        if (!consumeMinute()) {
            timeFields["minute"] = 0
        }

        return true
    }

    private fun consumeMinute(): Boolean {
        val beginning = idx
        val minute = consumeDigit()

        if (minute != null) {
            if (minute !in 0..59) {
                throw ParseException("一小时没有${minute}分钟")
            }
            timeFields["minute"] = minute
            if (consumeWord("分") || consumeWord("分钟")) {
                consumeSecond()
            }
        } else if (consumeWord("半")) {
            timeFields["minute"] = 30
        } else if (currentWord() == "1" && peekWord() == "刻") {
            advance(2)
            timeFields["minute"] = 15
        } else if (currentWord() == "3" && peekWord() == "刻") {
            advance(2)
            timeFields["minute"] = 45
        }

        return idx != beginning
    }

    private fun consumeSecond(): Boolean {
        val beginning = idx
        val second = consumeDigit()

        if (second != null) {
            if (consumeWord("秒") || consumeWord("秒钟")) {
                if (second !in 0..59) {
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
        val remaining = words.drop(idx).map { it.word }.joinToString("")
        event = remaining.replace(Regex("[的。，、！？]"), "").trim()
    }

    private fun consumeWord(vararg words: String): Boolean {
        if (currentWord() in words) {
            advance()
            return true
        }
        return false
    }

    private fun currentWord(): String = words.getOrElse(idx) { Token("", false) }.word

    private fun currentIsDigit(): Boolean = words.getOrElse(idx) { Token("", false) }.isDigit

    private fun peekWord(): String = words.getOrElse(idx + 1) { Token("", false) }.word

    private fun consumeDigit(): Int? {
        val word = currentWord()
        if (words.getOrElse(idx) { Token("", false) }.isDigit && word.toIntOrNull() != null) {
            val digit = word.toInt()
            advance()
            return digit
        }
        return null
    }

    private fun advance(step: Int = 1) {
        idx += step
    }
}
