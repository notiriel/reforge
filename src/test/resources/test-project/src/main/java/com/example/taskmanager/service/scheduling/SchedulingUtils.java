package com.example.taskmanager.service.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class SchedulingUtils {

    private SchedulingUtils() {
        // Utility class
    }

    public static LocalDate nextBusinessDay(LocalDate from) {
        LocalDate next = from.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    public static boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
