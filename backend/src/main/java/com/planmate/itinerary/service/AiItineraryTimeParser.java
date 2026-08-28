package com.planmate.itinerary.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

final class AiItineraryTimeParser {

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private AiItineraryTimeParser() {
    }

    static LocalTime parse(String value) {
        return LocalTime.parse(value, TIME_FORMATTER);
    }

    static long minuteOfDay(LocalTime time) {
        return time.getHour() * 60L + time.getMinute();
    }
}
