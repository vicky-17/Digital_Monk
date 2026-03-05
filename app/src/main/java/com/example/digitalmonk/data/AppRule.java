package com.example.digitalmonk.data;

import java.util.List;

public class AppRule {
    public String packageName;
    public boolean isLocked;
    public int dailyLimit; // in minutes
    public List<Schedule> schedules;

    public static class Schedule {
        public boolean enabled;
        public String startTime; // "21:00"
        public String endTime;   // "07:00"
        public List<Integer> days; // [0, 1, 2...]
    }
}