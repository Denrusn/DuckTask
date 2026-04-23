package com.ducktask.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class TimeParserTest {
    private val morning = LocalDateTime.of(2026, 4, 24, 2, 15)
    private val noon = LocalDateTime.of(2026, 4, 24, 12, 15)

    @Test
    fun normalizesChineseNumbersLikeReferenceParser() {
        assertEquals("9", ChineseNumberNormalizer.normalize("九"))
        assertEquals("11", ChineseNumberNormalizer.normalize("十一"))
        assertEquals("123", ChineseNumberNormalizer.normalize("一百二十三"))
        assertEquals("1203", ChineseNumberNormalizer.normalize("一千二百零三"))
        assertEquals("11101", ChineseNumberNormalizer.normalize("一万一千一百零一"))
        assertEquals("9981", ChineseNumberNormalizer.normalize("99八十一"))
    }

    @Test
    fun parsesImplicitMorningAndAfternoonHours() {
        val early = TimeParser.parse("三点四十五分钟提醒我还二百三十四块钱", morning)
        assertEquals(3, early.time.hour)
        assertEquals(45, early.time.minute)
        assertEquals("还234块钱", early.event)

        val afternoon = TimeParser.parse("三点四十五分钟提醒我还二百三十四块钱", noon)
        assertEquals(15, afternoon.time.hour)
        assertEquals(45, afternoon.time.minute)
        assertEquals("还234块钱", afternoon.event)
    }

    @Test
    fun parsesRelativeMinuteAndSecondDurations() {
        val parsed = TimeParser.parse("一分五十九秒后提醒我", noon)
        assertEquals(119, ChronoUnit.SECONDS.between(noon, parsed.time))
        assertEquals("闹钟", parsed.event)
    }

    @Test
    fun parsesHalfHourDurationAndEvent() {
        val parsed = TimeParser.parse("半个钟头后提醒我同步", noon)
        assertEquals(30, ChronoUnit.MINUTES.between(noon, parsed.time))
        assertEquals("同步", parsed.event)
    }

    @Test
    fun keepsHalfWhenItStartsTheEventText() {
        val parsed = TimeParser.parse("今晚八点半导体制冷片", noon)
        assertEquals(20, parsed.time.hour)
        assertEquals(0, parsed.time.minute)
        assertEquals("半导体制冷片", parsed.event)
    }

    @Test
    fun parsesWeekdayEveningWithHalfHour() {
        val parsed = TimeParser.parse("周日晚上八点半提醒我找入团申请", noon)
        assertEquals(7, parsed.time.dayOfWeek.value)
        assertEquals(20, parsed.time.hour)
        assertEquals(30, parsed.time.minute)
        assertEquals("找入团申请", parsed.event)
    }

    @Test
    fun parsesMonthRepeatAndRollsToNextMonthWhenNeeded() {
        val parsed = TimeParser.parse(
            "每月20号提醒我还信用卡",
            LocalDateTime.of(2026, 4, 21, 8, 0)
        )
        assertEquals(5, parsed.time.monthValue)
        assertEquals(20, parsed.time.dayOfMonth)
        assertEquals("还信用卡", parsed.event)
        assertEquals(1, parsed.repeat?.months)
    }

    @Test
    fun parsesWeeklyRepeat() {
        val parsed = TimeParser.parse("每两周周一上午10点", noon)
        assertEquals(1, parsed.time.dayOfWeek.value)
        assertEquals(10, parsed.time.hour)
        assertEquals(2, parsed.repeat?.weeks)
    }

    @Test
    fun rejectsUnsupportedRangesAndLunarDates() {
        assertThrows(ParseException::class.java) {
            TimeParser.parse("星期一到星期五提醒我早上六点半起床", noon)
        }
        assertThrows(ParseException::class.java) {
            TimeParser.parse("每年农历八月初九提醒我生日", noon)
        }
    }

    @Test
    fun rejectsImpossibleCalendarDates() {
        assertThrows(ParseException::class.java) {
            TimeParser.parse("六月三十一号提醒我交材料", noon)
        }
    }

    @Test
    fun rejectsUnparseableText() {
        assertThrows(ParseException::class.java) {
            TimeParser.parse("哈哈哈哈哈哈", noon)
        }
    }

    @Test
    fun rejectsMinuteRepeatAndTooFrequentHourRepeat() {
        assertThrows(ParseException::class.java) {
            TimeParser.parse("每分钟提醒我一次", noon)
        }
        assertThrows(ParseException::class.java) {
            TimeParser.parse("每两小时", noon)
        }
    }

    @Test
    fun parsesIsoLikeDateTime() {
        val parsed = TimeParser.parse("2026-12-16 09:10:00提醒我提交材料", noon)
        assertEquals(2026, parsed.time.year)
        assertEquals(12, parsed.time.monthValue)
        assertEquals(16, parsed.time.dayOfMonth)
        assertEquals(9, parsed.time.hour)
        assertEquals(10, parsed.time.minute)
        assertEquals("提交材料", parsed.event)
    }

    @Test
    fun parseFailureIsExceptionNotNullResult() {
        val failure = runCatching { TimeParser.parse("哈哈哈哈哈哈", noon) }.exceptionOrNull()
        assertNotNull(failure)
        assertNull(failure?.cause)
    }
}
