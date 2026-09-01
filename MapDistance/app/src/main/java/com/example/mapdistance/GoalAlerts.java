package com.example.mapdistance;

import java.util.ArrayList;
import java.util.List;

/** 这次测量的到达/间隔提醒，以及今日步数目标。 */
public final class GoalAlerts {
    private GoalAlerts() {}

    static final class Hit {
        final AlertKind kind;
        final String title;
        final String text;

        Hit(AlertKind kind, String title, String text) {
            this.kind = kind;
            this.title = title;
            this.text = text;
        }
    }

    /** 在引擎锁内改 session 标记。返回要弹的提醒（锁外再通知）。 */
    static List<Hit> checkTripLocked(android.content.Context c, TrackSession s) {
        List<Hit> lines = new ArrayList<>();
        if (c == null || s == null) {
            return lines;
        }
        if (!TrackEngine.RUNNING.equals(s.state) && !TrackEngine.PAUSED.equals(s.state)) {
            return lines;
        }
        float goalM = Prefs.goalTripM(c);
        if (Prefs.goalKmOn(c) && goalM >= 1 && !s.goalDistFired
                && TrackEngine.alertDistanceM(s) + 0.5 >= goalM) {
            s.goalDistFired = true;
            lines.add(new Hit(AlertKind.TRIP_KM, AlertKind.TRIP_KM.setting,
                    "这次已到 " + Formats.distance(goalM)));
        }
        int gs = Prefs.goalTripSteps(c);
        if (Prefs.goalStepsOn(c) && gs >= 1 && !s.goalStepsFired
                && TrackEngine.alertSteps(s) >= gs) {
            s.goalStepsFired = true;
            lines.add(new Hit(AlertKind.TRIP_STEPS, AlertKind.TRIP_STEPS.setting,
                    "这次已到 " + Formats.steps(gs)));
        }
        float everyM = Prefs.everyM(c);
        if (Prefs.everyKmOn(c) && everyM >= 1) {
            double used = TrackEngine.alertDistanceM(s);
            int n = (int) Math.floor(used / everyM);
            if (n > 0 && n > s.everyDistN) {
                s.everyDistN = n;
                lines.add(new Hit(AlertKind.EVERY_KM, AlertKind.EVERY_KM.setting,
                        "每 " + Formats.distance(everyM) + " · 现在 " + Formats.distance(used)));
            }
        }
        int es = Prefs.everySteps(c);
        if (Prefs.everyStepsOn(c) && es >= 1) {
            int usedSteps = TrackEngine.alertSteps(s);
            int n = usedSteps / es;
            if (n > 0 && n > s.everyStepsN) {
                s.everyStepsN = n;
                lines.add(new Hit(AlertKind.EVERY_STEPS, AlertKind.EVERY_STEPS.setting,
                        "每 " + Formats.steps(es) + " · 现在 " + Formats.steps(usedSteps)));
            }
        }
        return lines;
    }

    static void checkToday(android.content.Context c) {
        int goal = Prefs.todayGoalSteps(c);
        if (!Prefs.todayGoalOn(c) || goal < 1) {
            return;
        }
        String day = StepSensor.dayKey();
        if (day.equals(Prefs.todayGoalFiredDay(c))) {
            return;
        }
        if (StepSensor.get(c).today() < goal) {
            return;
        }
        Prefs.setTodayGoalFiredDay(c, day);
        AlertNotify.show(c, AlertKind.TODAY.setting,
                "今日已到 " + Formats.steps(goal) + "（本机芯片）", AlertKind.TODAY);
    }

    static String tripHint(android.content.Context c, TrackSession s) {
        if (c == null || s == null) {
            return "";
        }
        boolean active = TrackEngine.RUNNING.equals(s.state) || TrackEngine.PAUSED.equals(s.state);
        if (!active) {
            return "";
        }
        List<String> bits = new ArrayList<>();
        float goalM = Prefs.goalTripM(c);
        if (Prefs.goalKmOn(c) && goalM >= 1) {
            double used = TrackEngine.alertDistanceM(s);
            if (s.goalDistFired) {
                bits.add("距离已到");
            } else {
                double left = Math.max(0, goalM - used);
                bits.add("距 " + Formats.distance(goalM) + " 还差 " + Formats.distance(left));
            }
        }
        int gs = Prefs.goalTripSteps(c);
        if (Prefs.goalStepsOn(c) && gs >= 1) {
            int usedSteps = TrackEngine.alertSteps(s);
            if (s.goalStepsFired) {
                bits.add("步数已到");
            } else {
                bits.add("距 " + Formats.steps(gs) + " 还差 "
                        + Formats.steps(Math.max(0, gs - usedSteps)));
            }
        }
        if (bits.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bits.size(); i++) {
            if (i > 0) {
                b.append(" · ");
            }
            b.append(bits.get(i));
        }
        return b.toString();
    }
}
