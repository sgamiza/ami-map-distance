package com.example.mapdistance;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 历史轨迹的统计卡片，口径对齐身高 App 的统计页：窗口、习惯、极值、分位、对比。
 */
final class TrackStats {
    static final String[] FILTER_KEYS = {
            "all", "today", "week", "d7", "d30", "d90", "year", "walk", "run", "ride", "auto"
    };
    static final String[] FILTER_LABELS = {
            "全部", "今日", "本周", "近7天", "近30天", "近90天", "今年",
            "只看走路", "只看跑步", "只看骑车", "只看自动"
    };

    private static final String[] WEEK = {"日", "一", "二", "三", "四", "五", "六"};
    private static final long DAY = 24L * 3600_000L;

    static final class Card {
        final String title;
        final String value;
        final String note;

        Card(String title, String value, String note) {
            this.title = title;
            this.value = value;
            this.note = note == null ? "" : note;
        }
    }

    private TrackStats() {}

    static String normalize(String key) {
        if (key == null) {
            return "all";
        }
        for (String k : FILTER_KEYS) {
            if (k.equals(key)) {
                return k;
            }
        }
        return "all";
    }

    static int indexOf(String key) {
        String k = normalize(key);
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            if (FILTER_KEYS[i].equals(k)) {
                return i;
            }
        }
        return 0;
    }

    static String label(String key) {
        return FILTER_LABELS[indexOf(key)];
    }

    static String shareText(Context c, List<TrackSession> all, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("阿米测距统计（").append(label(filter)).append("）\n");
        sb.append("版本 ").append(BuildConfig.VERSION_NAME).append('\n').append('\n');
        List<Card> cards = compute(c, all, filter);
        if (cards.isEmpty()) {
            sb.append("这一筛选下没有记录。");
            return sb.toString();
        }
        for (Card card : cards) {
            sb.append(card.title).append('\n');
            sb.append(card.value).append('\n');
            if (!card.note.isEmpty()) {
                sb.append(card.note).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    static List<TrackSession> filtered(List<TrackSession> all, String filter) {
        String key = normalize(filter);
        return byTime(byMode(all, key), key, System.currentTimeMillis());
    }

    static List<Card> compute(Context c, List<TrackSession> all, String filter) {
        if (all == null) {
            all = Collections.emptyList();
        } else {
            all = TrackClean.views(c, all);
        }
        String key = normalize(filter);
        List<TrackSession> universe = byMode(all, key);
        List<TrackSession> list = byTime(universe, key, System.currentTimeMillis());
        List<Card> out = new ArrayList<>();
        if (list.isEmpty()) {
            return out;
        }
        Totals tot = totals(list);
        out.add(new Card(
                "累计概览",
                tot.n + " 次  ·  " + Formats.distance(tot.m) + "  ·  " + Formats.duration(tot.moveMs),
                "暂停 " + Formats.duration(tot.pauseMs)
                        + "  ·  平均每次 " + Formats.distance(tot.m / tot.n)
                        + " / " + Formats.duration(tot.moveMs / tot.n)
                        + "。操场约 " + laps(tot.m) + " 圈（按 400 米）。"
                        + (Prefs.histClean(c)
                        ? " 数字已按实际移动（去掉飞点和停留），原始点还在库里。"
                        : "")));

        out.add(new Card(
                "步数与热量",
                Formats.steps(tot.steps) + "  ·  " + Formats.kcal(tot.kcal),
                (tot.withSteps == 0 ? "这些记录里还没有步数（旧记录或没给身体活动权限）。"
                        : ("有步数的 " + tot.withSteps + " 次，平均每次 "
                        + Formats.steps(Math.round(tot.steps / (float) tot.withSteps)) + "。"))
                        + (tot.kcal < 0.5 ? "" : " 热量按设置里的体重粗估。")));

        if (!"walk".equals(key) && !"run".equals(key) && !"ride".equals(key)
                && !"auto".equals(key)) {
            addModeSplit(out, list);
        }
        addTrueWalk(out, list, key);
        if (isAllLike(key)) {
            addCalendarWindows(out, universe, System.currentTimeMillis());
        } else {
            addCompare(out, universe, key, System.currentTimeMillis());
        }

        out.add(new Card(
                "配速与时速",
                "均速 " + Formats.speed(c, tot.avgMps())
                        + "  ·  配速 " + Formats.pace(tot.m, tot.moveMs),
                "有时速的记录里，中位均速 " + Formats.speed(c, medianSpeed(list))
                        + "，最快一次均速 " + Formats.speed(c, tot.maxAvgMps)
                        + "，见过的最高瞬时 " + Formats.speed(c, tot.maxInstMps) + "。"));

        addStride(out, list);
        addRecords(out, c, list);
        addLatest(out, list);
        addHabit(out, list);
        addStreaks(out, list);
        addTimeOfDay(out, list);
        addWeekday(out, list);
        addMonths(out, list);
        addYears(out, list);
        addDistanceBuckets(out, list);
        addDurationBuckets(out, list);
        addQuantiles(out, c, list);
        addWeeklyLoad(out, list);
        addSameDay(out, list);
        addTenK(out, list);
        addPlaces(out, list);
        addMarksAndPoints(out, list);
        addBattery(out, list);
        return out;
    }

    private static boolean isAllLike(String key) {
        return "all".equals(key) || "walk".equals(key) || "run".equals(key)
                || "ride".equals(key) || "auto".equals(key);
    }

    private static List<TrackSession> byMode(List<TrackSession> all, String key) {
        List<TrackSession> out = new ArrayList<>();
        for (TrackSession s : all) {
            if (s == null || s.startMs <= 0) {
                continue;
            }
            if ("walk".equals(key) && !TrackEngine.MODE_WALK.equals(s.mode)) {
                continue;
            }
            if ("run".equals(key) && !TrackEngine.MODE_RUN.equals(s.mode)) {
                continue;
            }
            if ("ride".equals(key) && !TrackEngine.MODE_RIDE.equals(s.mode)) {
                continue;
            }
            if ("auto".equals(key) && !TrackEngine.isAuto(s.mode)) {
                continue;
            }
            if ("walk".equals(key) && TrackEngine.isAuto(s.mode)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private static List<TrackSession> byTime(List<TrackSession> src, String key, long now) {
        long cut = cutoff(key, now);
        long end = now + DAY;
        if ("today".equals(key) || "week".equals(key) || "year".equals(key)
                || "d7".equals(key) || "d30".equals(key) || "d90".equals(key)) {
            List<TrackSession> out = new ArrayList<>();
            for (TrackSession s : src) {
                if (s.startMs >= cut && s.startMs < end) {
                    out.add(s);
                }
            }
            return out;
        }
        return src;
    }

    private static long cutoff(String key, long now) {
        if ("today".equals(key)) {
            return startOfDay(now);
        }
        if ("week".equals(key)) {
            return startOfWeek(now);
        }
        if ("year".equals(key)) {
            return startOfYear(now);
        }
        if ("d7".equals(key)) {
            return now - 7 * DAY;
        }
        if ("d30".equals(key)) {
            return now - 30 * DAY;
        }
        if ("d90".equals(key)) {
            return now - 90 * DAY;
        }
        return 0;
    }

    private static void addModeSplit(List<Card> out, List<TrackSession> list) {
        Totals walk = new Totals();
        Totals run = new Totals();
        Totals ride = new Totals();
        Totals auto = new Totals();
        for (TrackSession s : list) {
            if (TrackEngine.MODE_RUN.equals(s.mode)) {
                run.add(s);
            } else if (TrackEngine.MODE_RIDE.equals(s.mode)) {
                ride.add(s);
            } else if (TrackEngine.isAuto(s.mode)) {
                auto.add(s);
            } else {
                walk.add(s);
            }
        }
        if (walk.n == 0 && run.n == 0 && ride.n == 0 && auto.n == 0) {
            return;
        }
        StringBuilder note = new StringBuilder();
        if (walk.n == 0) {
            note.append("还没有走路记录。");
        } else {
            note.append("走路均速 ").append(Formats.speedKmh(walk.avgMps()))
                    .append("，配速 ").append(Formats.pace(walk.m, walk.moveMs)).append("。");
        }
        if (run.n == 0) {
            note.append(" 还没有跑步记录。");
        } else {
            note.append(" 跑步均速 ").append(Formats.speedKmh(run.avgMps()))
                    .append("，配速 ").append(Formats.pace(run.m, run.moveMs)).append("。");
        }
        if (ride.n == 0) {
            note.append(" 还没有骑车记录。");
        } else {
            note.append(" 骑车均速 ").append(Formats.speedKmh(ride.avgMps()))
                    .append("，配速 ").append(Formats.pace(ride.m, ride.moveMs)).append("。");
        }
        if (auto.n == 0) {
            note.append(" 还没有自动记录。");
        } else {
            note.append(" 自动记录看下面「真正走路」。");
        }
        out.add(new Card(
                "走路 / 跑步 / 骑车 / 自动",
                "走 " + walk.n + " 次 " + Formats.distance(walk.m)
                        + "  ·  跑 " + run.n + " 次 " + Formats.distance(run.m)
                        + "  ·  骑 " + ride.n + " 次 " + Formats.distance(ride.m)
                        + "  ·  自动 " + auto.n + " 次",
                note.toString().trim()));
    }

    private static void addTrueWalk(List<Card> out, List<TrackSession> list, String key) {
        double walkM = 0;
        long walkMs = 0;
        int walkSteps = 0;
        double vehM = 0;
        long vehMs = 0;
        int autoN = 0;
        for (TrackSession s : list) {
            if (TrackEngine.isAuto(s.mode)) {
                autoN++;
                walkM += s.walkDistanceM;
                walkMs += s.walkMovingMs;
                walkSteps += Math.max(0, s.walkSteps);
                vehM += s.vehicleDistanceM;
                vehMs += s.vehicleMovingMs;
            } else if (TrackEngine.MODE_WALK.equals(s.mode)) {
                walkM += s.distanceM;
                walkMs += s.movingMs;
                walkSteps += Math.max(0, s.steps);
            }
        }
        if (autoN == 0 && walkM < 1) {
            return;
        }
        double cad = (walkSteps >= 8 && walkMs >= 15_000L) ? walkSteps / (walkMs / 60000.0) : 0;
        double walkSpd = walkMs >= 1000 && walkM > 0 ? walkM / (walkMs / 1000.0) : 0;
        double vehSpd = vehMs >= 1000 && vehM > 0 ? vehM / (vehMs / 1000.0) : 0;
        StringBuilder note = new StringBuilder();
        note.append("手动走路 + 自动模式里识别成走路的部分。开车那段不计入。");
        if (autoN > 0) {
            note.append(" 自动 ").append(autoN).append(" 次里车程 ")
                    .append(Formats.distance(vehM)).append(" · ")
                    .append(Formats.duration(vehMs))
                    .append(" · 均速 ").append(Formats.speedKmh(vehSpd)).append("。");
        }
        out.add(new Card(
                "真正走路",
                Formats.distance(walkM) + "  ·  " + Formats.duration(walkMs)
                        + "  ·  " + Formats.steps(walkSteps)
                        + (cad >= 20 ? "  ·  " + Formats.cadence(cad) : "")
                        + (walkSpd > 0 ? "  ·  " + Formats.speedKmh(walkSpd) : ""),
                note.toString()));
    }

    private static void addCalendarWindows(List<Card> out, List<TrackSession> universe, long now) {
        Window today = window(universe, startOfDay(now), now);
        Window week = window(universe, startOfWeek(now), now);
        Window month = window(universe, startOfMonth(now), now);
        Window year = window(universe, startOfYear(now), now);
        Window yest = window(universe, startOfDay(now) - DAY, startOfDay(now));
        Window lastWeek = window(universe, startOfWeek(now) - 7 * DAY, startOfWeek(now));
        out.add(new Card(
                "日历窗口",
                "今 " + today.n + " 次 / " + Formats.distance(today.m)
                        + "  ·  本周 " + week.n + " / " + Formats.distance(week.m),
                "本月 " + month.n + " 次 " + Formats.distance(month.m)
                        + "，今年 " + year.n + " 次 " + Formats.distance(year.m)
                        + "。昨天 " + yest.n + " 次 " + Formats.distance(yest.m)
                        + "，上周 " + lastWeek.n + " 次 " + Formats.distance(lastWeek.m)
                        + "。"));
    }

    private static void addCompare(List<Card> out, List<TrackSession> universe, String key, long now) {
        long cut = cutoff(key, now);
        if (cut <= 0) {
            return;
        }
        long span = now - cut;
        Window cur = window(universe, cut, now);
        Window prev = window(universe, cut - span, cut);
        if (cur.n == 0 && prev.n == 0) {
            return;
        }
        out.add(new Card(
                "对比上一段",
                deltaKm(cur.m, prev.m) + "  ·  次数 " + deltaN(cur.n, prev.n),
                "这一段 " + cur.n + " 次 " + Formats.distance(cur.m)
                        + "，再往前同样长的一段时间 " + prev.n + " 次 "
                        + Formats.distance(prev.m) + "。"));
    }

    private static void addStride(List<Card> out, List<TrackSession> list) {
        double sumM = 0;
        int sumSteps = 0;
        int n = 0;
        double minCm = 999;
        double maxCm = 0;
        for (TrackSession s : list) {
            double m;
            int st;
            if (TrackEngine.isAuto(s.mode)) {
                m = s.walkDistanceM;
                st = s.walkSteps;
            } else if (TrackEngine.isRide(s.mode)) {
                continue;
            } else {
                m = s.distanceM;
                st = s.steps;
            }
            if (st < 5 || m < 5) {
                continue;
            }
            double cm = m / st * 100.0;
            if (cm < 25 || cm > 160) {
                continue;
            }
            sumM += m;
            sumSteps += st;
            n++;
            if (cm < minCm) {
                minCm = cm;
            }
            if (cm > maxCm) {
                maxCm = cm;
            }
        }
        if (n == 0) {
            out.add(new Card("步幅", "--", "步数不够或 GPS 太短，算不出步幅。"));
            return;
        }
        double avg = sumM / sumSteps * 100.0;
        out.add(new Card(
                "步幅",
                String.format(Locale.CHINA, "平均 %.0f 厘米", avg),
                n + " 次有效记录，最短 " + String.format(Locale.CHINA, "%.0f", minCm)
                        + " 厘米，最长 " + String.format(Locale.CHINA, "%.0f", maxCm)
                        + " 厘米。室内摇手机会让步幅偏短。"));
    }

    private static void addRecords(List<Card> out, Context c, List<TrackSession> list) {
        TrackSession far = null;
        TrackSession longS = null;
        TrackSession steps = null;
        TrackSession fastAvg = null;
        TrackSession slowAvg = null;
        TrackSession maxSp = null;
        TrackSession kcal = null;
        TrackSession marks = null;
        for (TrackSession s : list) {
            if (far == null || s.distanceM > far.distanceM) {
                far = s;
            }
            if (longS == null || s.movingMs > longS.movingMs) {
                longS = s;
            }
            if (steps == null || s.steps > steps.steps) {
                steps = s;
            }
            if (s.distanceM >= 500 && s.movingMs >= 60_000) {
                double a = s.avgSpeedMps();
                if (a > 0.3 && (fastAvg == null || a > fastAvg.avgSpeedMps())) {
                    fastAvg = s;
                }
                if (a > 0.3 && (slowAvg == null || a < slowAvg.avgSpeedMps())) {
                    slowAvg = s;
                }
            }
            if (maxSp == null || s.maxSpeedMps > maxSp.maxSpeedMps) {
                maxSp = s;
            }
            if (kcal == null || s.calories > kcal.calories) {
                kcal = s;
            }
            if (marks == null || s.marks.size() > marks.marks.size()) {
                marks = s;
            }
        }
        StringBuilder note = new StringBuilder();
        note.append("最远：").append(sessionLine(far)).append('\n');
        note.append("最久：").append(sessionLine(longS)).append('\n');
        if (steps != null && steps.steps > 0) {
            note.append("最多步：").append(sessionLine(steps)).append('\n');
        }
        if (fastAvg != null) {
            note.append("最快均速：").append(Formats.speed(c, fastAvg.avgSpeedMps()))
                    .append("  ").append(sessionLine(fastAvg)).append('\n');
        }
        if (slowAvg != null && slowAvg != fastAvg) {
            note.append("最慢均速（≥500 米）：").append(Formats.speed(c, slowAvg.avgSpeedMps()))
                    .append("  ").append(sessionLine(slowAvg)).append('\n');
        }
        if (maxSp != null && maxSp.maxSpeedMps > 0) {
            note.append("最高瞬时：").append(Formats.speed(c, maxSp.maxSpeedMps))
                    .append("  ").append(sessionLine(maxSp)).append('\n');
        }
        if (kcal != null && kcal.calories >= 0.5) {
            note.append("最多热量：").append(Formats.kcal(kcal.calories))
                    .append("  ").append(sessionLine(kcal));
        }
        String value = far == null ? "--" : ("最远 " + Formats.distance(far.distanceM));
        if (longS != null) {
            value += "  ·  最久 " + Formats.duration(longS.movingMs);
        }
        out.add(new Card("个人纪录", value, note.toString().trim()));
        if (marks != null && !marks.marks.isEmpty()) {
            out.add(new Card(
                    "打点最多的一次",
                    Formats.marksCountLine(marks.marks),
                    sessionLine(marks)));
        }
    }

    private static void addLatest(List<Card> out, List<TrackSession> list) {
        TrackSession last = null;
        for (TrackSession s : list) {
            if (last == null || s.startMs > last.startMs) {
                last = s;
            }
        }
        if (last == null) {
            return;
        }
        int since = daysBetween(startOfDay(last.startMs), startOfDay(System.currentTimeMillis()));
        String route = Formats.routeLine(last);
        out.add(new Card(
                "最近一次",
                Formats.headline(last),
                Formats.when(last.startMs)
                        + (route.isEmpty() ? "" : "  " + route)
                        + "  ·  距今 " + Math.max(0, since) + " 天"
                        + (last.marks.isEmpty() ? "" : "  ·  " + Formats.marksCountLine(last.marks))));
    }

    private static void addHabit(List<Card> out, List<TrackSession> list) {
        List<Long> days = uniqueDays(list);
        if (days.isEmpty()) {
            return;
        }
        long first = days.get(0);
        long last = days.get(days.size() - 1);
        int span = daysBetween(first, last);
        int avgGap = list.size() > 1 ? Math.round((float) span / (list.size() - 1)) : 0;
        int maxGap = 0;
        for (int i = 1; i < days.size(); i++) {
            int g = daysBetween(days.get(i - 1), days.get(i));
            if (g > maxGap) {
                maxGap = g;
            }
        }
        int since = daysBetween(last, startOfDay(System.currentTimeMillis()));
        int calDays = Math.max(1, span + 1);
        out.add(new Card(
                "测量习惯",
                list.size() + " 次  ·  " + days.size() + " 个有记录的日子  ·  跨度 " + formatSpan(span),
                "平均约 " + avgGap + " 天一次，最长间隔 " + maxGap + " 天，距上次 "
                        + Math.max(0, since) + " 天。这段日历里活跃天数占 "
                        + pct(days.size(), calDays) + "%。"));
    }

    private static void addStreaks(List<Card> out, List<TrackSession> list) {
        TreeSet<Long> set = new TreeSet<>(uniqueDays(list));
        if (set.isEmpty()) {
            return;
        }
        int longest = 1;
        int cur = 1;
        Long prev = null;
        for (Long d : set) {
            if (prev != null && d - prev == DAY) {
                cur++;
                if (cur > longest) {
                    longest = cur;
                }
            } else if (prev != null) {
                cur = 1;
            }
            prev = d;
        }
        long today = startOfDay(System.currentTimeMillis());
        int ending = 0;
        long probe = set.contains(today) ? today : today - DAY;
        while (set.contains(probe)) {
            ending++;
            probe -= DAY;
        }
        out.add(new Card(
                "连续活跃",
                ending > 0 ? ("当前连续 " + ending + " 天") : "当前没有连着走",
                "历史上最长连续 " + longest + " 天有记录（按开始日期，中间空一天就算断）。"));
    }

    private static void addTimeOfDay(List<Card> out, List<TrackSession> list) {
        int dawn = 0, noon = 0, pm = 0, eve = 0, night = 0;
        for (TrackSession s : list) {
            int h = hour(s.startMs);
            if (h >= 5 && h < 11) {
                dawn++;
            } else if (h >= 11 && h < 14) {
                noon++;
            } else if (h >= 14 && h < 18) {
                pm++;
            } else if (h >= 18 && h < 22) {
                eve++;
            } else {
                night++;
            }
        }
        String peak = "清晨";
        int p = dawn;
        if (noon > p) {
            peak = "中午";
            p = noon;
        }
        if (pm > p) {
            peak = "下午";
            p = pm;
        }
        if (eve > p) {
            peak = "晚上";
            p = eve;
        }
        if (night > p) {
            peak = "夜里";
        }
        out.add(new Card(
                "时段分布",
                "最常 " + peak,
                "清晨(5–11) " + dawn + "  ·  中午 " + noon
                        + "  ·  下午 " + pm + "  ·  晚 " + eve + "  ·  夜里 " + night
                        + "。按开始测量的钟点。"));
    }

    private static void addWeekday(List<Card> out, List<TrackSession> list) {
        int[] n = new int[7];
        double[] m = new double[7];
        for (TrackSession s : list) {
            int w = weekday(s.startMs);
            n[w]++;
            m[w] += s.distanceM;
        }
        int best = 0;
        for (int i = 1; i < 7; i++) {
            if (n[i] > n[best] || (n[i] == n[best] && m[i] > m[best])) {
                best = i;
            }
        }
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            int idx = i == 7 ? 0 : i;
            if (i > 1) {
                b.append("  ");
            }
            b.append("周").append(WEEK[idx]).append(' ').append(n[idx]);
        }
        out.add(new Card(
                "星期分布",
                "最勤是周" + WEEK[best] + "  ·  " + n[best] + " 次 "
                        + Formats.distance(m[best]),
                b.toString()));
    }

    private static void addMonths(List<Card> out, List<TrackSession> list) {
        TreeMap<String, Window> map = new TreeMap<>();
        for (TrackSession s : list) {
            String k = monthKey(s.startMs);
            Window w = map.get(k);
            if (w == null) {
                w = new Window();
                map.put(k, w);
            }
            w.add(s);
        }
        if (map.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Window>> rows = new ArrayList<>(map.entrySet());
        Collections.sort(rows, (a, b) -> {
            int c = Double.compare(b.getValue().m, a.getValue().m);
            return c != 0 ? c : b.getKey().compareTo(a.getKey());
        });
        Window top = rows.get(0).getValue();
        StringBuilder b = new StringBuilder();
        int show = Math.min(8, rows.size());
        for (int i = 0; i < show; i++) {
            Map.Entry<String, Window> e = rows.get(i);
            if (i > 0) {
                b.append('\n');
            }
            b.append(prettyMonth(e.getKey())).append("  ")
                    .append(e.getValue().n).append(" 次  ")
                    .append(Formats.distance(e.getValue().m));
        }
        out.add(new Card(
                "月份排行",
                "最远的月 " + prettyMonth(rows.get(0).getKey())
                        + "  ·  " + Formats.distance(top.m),
                b.toString()));
    }

    private static void addYears(List<Card> out, List<TrackSession> list) {
        TreeMap<Integer, Window> map = new TreeMap<>();
        for (TrackSession s : list) {
            int y = yearOf(s.startMs);
            Window w = map.get(y);
            if (w == null) {
                w = new Window();
                map.put(y, w);
            }
            w.add(s);
        }
        if (map.size() < 1) {
            return;
        }
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Integer, Window> e : map.entrySet()) {
            if (!first) {
                b.append('\n');
            }
            first = false;
            b.append(e.getKey()).append("  ")
                    .append(e.getValue().n).append(" 次  ")
                    .append(Formats.distance(e.getValue().m))
                    .append("  ·  ").append(Formats.steps(e.getValue().steps));
        }
        int thisYear = yearOf(System.currentTimeMillis());
        Window ty = map.get(thisYear);
        out.add(new Card(
                "自然年",
                ty == null ? (map.lastKey() + "  " + Formats.distance(map.get(map.lastKey()).m))
                        : (thisYear + "  " + Formats.distance(ty.m)),
                b.toString()));
    }

    private static void addDistanceBuckets(List<Card> out, List<TrackSession> list) {
        int u1 = 0, u3 = 0, u5 = 0, u10 = 0, over = 0;
        for (TrackSession s : list) {
            double km = s.distanceM / 1000.0;
            if (km < 1) {
                u1++;
            } else if (km < 3) {
                u3++;
            } else if (km < 5) {
                u5++;
            } else if (km < 10) {
                u10++;
            } else {
                over++;
            }
        }
        out.add(new Card(
                "距离分档",
                "5 公里以上 " + (u10 + over) + " 次  ·  10 公里以上 " + over + " 次",
                "<1 公里 " + u1 + "  ·  1–3 " + u3 + "  ·  3–5 " + u5
                        + "  ·  5–10 " + u10 + "  ·  ≥10 " + over
                        + "。马拉松当量 " + String.format(Locale.CHINA, "%.2f", totals(list).m / 42195.0)
                        + " 个全程。"));
    }

    private static void addDurationBuckets(List<Card> out, List<TrackSession> list) {
        int a = 0, b = 0, c = 0, d = 0, e = 0;
        for (TrackSession s : list) {
            long min = s.movingMs / 60_000;
            if (min < 15) {
                a++;
            } else if (min < 30) {
                b++;
            } else if (min < 60) {
                c++;
            } else if (min < 120) {
                d++;
            } else {
                e++;
            }
        }
        out.add(new Card(
                "用时分档",
                "半小时以上 " + (c + d + e) + " 次  ·  两小时以上 " + e + " 次",
                "<15 分 " + a + "  ·  15–30 分 " + b + "  ·  30–60 分 " + c
                        + "  ·  1–2 小时 " + d + "  ·  ≥2 小时 " + e + "。"));
    }

    private static void addQuantiles(List<Card> out, Context c, List<TrackSession> list) {
        List<Double> dist = new ArrayList<>();
        List<Double> spd = new ArrayList<>();
        List<Long> dur = new ArrayList<>();
        for (TrackSession s : list) {
            dist.add(s.distanceM);
            dur.add(s.movingMs);
            if (s.distanceM >= 200 && s.movingMs >= 30_000 && s.avgSpeedMps() > 0.2) {
                spd.add(s.avgSpeedMps());
            }
        }
        Collections.sort(dist);
        Collections.sort(dur);
        String speedNote = spd.size() < 3 ? "有效均速样本太少，先多走几段。"
                : ("均速 P10 " + Formats.speed(c, quantileD(spd, 0.1))
                + "  ·  P50 " + Formats.speed(c, quantileD(spd, 0.5))
                + "  ·  P90 " + Formats.speed(c, quantileD(spd, 0.9)));
        out.add(new Card(
                "距离分位",
                "中位 " + Formats.distance(quantileD(dist, 0.5)),
                "P10 " + Formats.distance(quantileD(dist, 0.1))
                        + "  ·  P90 " + Formats.distance(quantileD(dist, 0.9))
                        + "  ·  用时中位 " + Formats.duration(quantileL(dur, 0.5))
                        + "。\n" + speedNote));
    }

    private static void addWeeklyLoad(List<Card> out, List<TrackSession> list) {
        if (list.size() < 2) {
            return;
        }
        TreeMap<String, Window> weeks = new TreeMap<>();
        for (TrackSession s : list) {
            String k = weekKey(s.startMs);
            Window w = weeks.get(k);
            if (w == null) {
                w = new Window();
                weeks.put(k, w);
            }
            w.add(s);
        }
        double sum = 0;
        double max = 0;
        String maxK = "";
        for (Map.Entry<String, Window> e : weeks.entrySet()) {
            sum += e.getValue().m;
            if (e.getValue().m >= max) {
                max = e.getValue().m;
                maxK = e.getKey();
            }
        }
        double avg = sum / weeks.size();
        out.add(new Card(
                "每周负荷",
                "有记录的周平均 " + Formats.distance(avg),
                weeks.size() + " 个自然周（周一到周日）。最猛的一周 "
                        + prettyWeek(maxK) + " 走了 " + Formats.distance(max) + "。"));
    }

    private static void addSameDay(List<Card> out, List<TrackSession> list) {
        Map<Long, Integer> n = new LinkedHashMap<>();
        int multiDays = 0;
        int max = 1;
        for (TrackSession s : list) {
            long d = startOfDay(s.startMs);
            Integer v = n.get(d);
            int x = (v == null ? 0 : v) + 1;
            n.put(d, x);
        }
        for (int v : n.values()) {
            if (v > 1) {
                multiDays++;
            }
            if (v > max) {
                max = v;
            }
        }
        out.add(new Card(
                "一天多次",
                multiDays == 0 ? "还没有同一天走两次" : (multiDays + " 天走了不止一次"),
                "同一天最多 " + max + " 次。按开始日期算。"));
    }

    private static void addTenK(List<Card> out, List<TrackSession> list) {
        Map<Long, Integer> steps = new LinkedHashMap<>();
        Map<Long, Double> meters = new LinkedHashMap<>();
        for (TrackSession s : list) {
            long d = startOfDay(s.startMs);
            Integer st = steps.get(d);
            steps.put(d, (st == null ? 0 : st) + Math.max(0, s.steps));
            Double m = meters.get(d);
            meters.put(d, (m == null ? 0 : m) + s.distanceM);
        }
        int tenK = 0;
        int fiveKm = 0;
        for (Long d : steps.keySet()) {
            if (steps.get(d) >= 10000) {
                tenK++;
            }
            if (meters.get(d) >= 5000) {
                fiveKm++;
            }
        }
        out.add(new Card(
                "达标日",
                "万步日 " + tenK + "  ·  五公里日 " + fiveKm,
                "只计本 App 里保存过的测量，不是系统全天步数。一天多段会加在一起。"));
    }

    private static void addPlaces(List<Card> out, List<TrackSession> list) {
        Map<String, Integer> from = new LinkedHashMap<>();
        Map<String, Integer> to = new LinkedHashMap<>();
        Map<String, Integer> route = new LinkedHashMap<>();
        for (TrackSession s : list) {
            String a = placeOf(s.fromPlace, s.startAddr);
            String b = placeOf(s.toPlace, s.endAddr);
            if (a != null) {
                bump(from, a);
            }
            if (b != null) {
                bump(to, b);
            }
            if (a != null && b != null) {
                bump(route, a + " → " + b);
            }
        }
        String f = topKey(from);
        String t = topKey(to);
        String r = topKey(route);
        if (f == null && t == null && r == null) {
            out.add(new Card("地点", "还没有可用的出发/到达名",
                    "结束时填从哪到哪，或等联网补上街道名。"));
            return;
        }
        StringBuilder note = new StringBuilder();
        if (f != null) {
            note.append("出发最多：").append(f).append("（").append(from.get(f)).append(" 次）");
        }
        if (t != null) {
            if (note.length() > 0) {
                note.append('\n');
            }
            note.append("到达最多：").append(t).append("（").append(to.get(t)).append(" 次）");
        }
        if (r != null) {
            if (note.length() > 0) {
                note.append('\n');
            }
            note.append("路线最多：").append(r).append("（").append(route.get(r)).append(" 次）");
        }
        out.add(new Card(
                "常去地点",
                r != null ? r : (f != null ? ("常从 " + f) : ("常到 " + t)),
                note.toString()));
    }

    private static void addMarksAndPoints(List<Card> out, List<TrackSession> list) {
        int marks = 0;
        int auto = 0;
        int pts = 0;
        int withMarks = 0;
        for (TrackSession s : list) {
            marks += s.marks.size();
            pts += s.points.size();
            if (!s.marks.isEmpty()) {
                withMarks++;
            }
            for (Checkpoint m : s.marks) {
                if (m.auto) {
                    auto++;
                }
            }
        }
        out.add(new Card(
                "打点与轨迹点",
                "打点 " + marks + "  ·  轨迹点 " + String.format(Locale.CHINA, "%,d", pts),
                withMarks + " 次带打点，其中自动 " + auto + "、手动 "
                        + Math.max(0, marks - auto)
                        + (totals(list).m >= 100
                        ? ("。大约每 " + String.format(Locale.CHINA, "%.0f",
                        totals(list).m / Math.max(1, pts)) + " 米一个轨迹点。")
                        : "。")));
    }

    private static void addBattery(List<Card> out, List<TrackSession> list) {
        int n = 0;
        int sumDrop = 0;
        int maxDrop = 0;
        TrackSession worst = null;
        for (TrackSession s : list) {
            if (s.battStartPct < 0 || s.battEndPct < 0 || s.battSawCharge) {
                continue;
            }
            int d = s.battStartPct - s.battEndPct;
            if (d < 0) {
                continue;
            }
            n++;
            sumDrop += d;
            if (d >= maxDrop) {
                maxDrop = d;
                worst = s;
            }
        }
        if (n == 0) {
            return;
        }
        out.add(new Card(
                "整机电量",
                "平均每次掉 " + String.format(Locale.CHINA, "%.1f", sumDrop / (double) n) + "%",
                n + " 次没在充电时记下了电量。掉得最多一次 "
                        + maxDrop + "%"
                        + (worst == null ? "" : "（" + sessionLine(worst) + "）。")
                        + " 这是整机差值，不是系统里「仅本 App」。"));
    }

    private static Totals totals(List<TrackSession> list) {
        Totals t = new Totals();
        for (TrackSession s : list) {
            t.add(s);
        }
        return t;
    }

    private static Window window(List<TrackSession> list, long from, long to) {
        Window w = new Window();
        for (TrackSession s : list) {
            if (s.startMs >= from && s.startMs < to) {
                w.add(s);
            }
        }
        return w;
    }

    private static String sessionLine(TrackSession s) {
        if (s == null) {
            return "--";
        }
        String name = Formats.nz(s.title);
        return (name.isEmpty() ? Formats.modeLabel(s.mode) : name)
                + "  " + Formats.distance(s.distanceM)
                + "  " + Formats.when(s.startMs);
    }

    private static String placeOf(String user, String geo) {
        String u = Formats.nz(user);
        if (usablePlace(u)) {
            return u;
        }
        String g = Formats.nz(geo);
        if (usablePlace(g)) {
            return g;
        }
        return null;
    }

    private static boolean usablePlace(String s) {
        if (s == null || s.isEmpty() || Formats.needsGeocode(s)) {
            return false;
        }
        return !s.contains("联网后") && s.length() >= 2;
    }

    private static void bump(Map<String, Integer> map, String k) {
        Integer n = map.get(k);
        map.put(k, n == null ? 1 : n + 1);
    }

    private static String topKey(Map<String, Integer> map) {
        String best = null;
        int n = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() > n) {
                n = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static List<Long> uniqueDays(List<TrackSession> list) {
        TreeSet<Long> set = new TreeSet<>();
        for (TrackSession s : list) {
            set.add(startOfDay(s.startMs));
        }
        return new ArrayList<>(set);
    }

    private static double medianSpeed(List<TrackSession> list) {
        List<Double> spd = new ArrayList<>();
        for (TrackSession s : list) {
            if (s.distanceM >= 200 && s.movingMs >= 30_000 && s.avgSpeedMps() > 0.2) {
                spd.add(s.avgSpeedMps());
            }
        }
        if (spd.isEmpty()) {
            return 0;
        }
        Collections.sort(spd);
        return quantileD(spd, 0.5);
    }

    private static double quantileD(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        List<Double> a = new ArrayList<>(sorted);
        Collections.sort(a);
        int i = (int) Math.round((a.size() - 1) * p);
        if (i < 0) {
            i = 0;
        }
        if (i >= a.size()) {
            i = a.size() - 1;
        }
        return a.get(i);
    }

    private static long quantileL(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        List<Long> a = new ArrayList<>(sorted);
        Collections.sort(a);
        int i = (int) Math.round((a.size() - 1) * p);
        if (i < 0) {
            i = 0;
        }
        if (i >= a.size()) {
            i = a.size() - 1;
        }
        return a.get(i);
    }

    private static String laps(double meters) {
        return String.format(Locale.CHINA, "%.1f", meters / 400.0);
    }

    private static String pct(int a, int b) {
        if (b <= 0) {
            return "0";
        }
        return String.format(Locale.CHINA, "%.0f", a * 100.0 / b);
    }

    private static String deltaKm(double cur, double prev) {
        if (prev < 1 && cur < 1) {
            return "两段都几乎没走";
        }
        if (prev < 1) {
            return "上一段几乎没走，这段 " + Formats.distance(cur);
        }
        double r = (cur - prev) / prev * 100.0;
        String dir = Math.abs(r) < 3 ? "基本持平" : (r > 0 ? "多了" : "少了");
        return dir + " " + String.format(Locale.CHINA, "%.0f%%", Math.abs(r));
    }

    private static String deltaN(int cur, int prev) {
        if (prev == 0) {
            return cur + " 次（上一段 0）";
        }
        int d = cur - prev;
        if (d == 0) {
            return "次数一样";
        }
        return (d > 0 ? "+" : "") + d + " 次";
    }

    private static String formatSpan(int days) {
        if (days < 1) {
            return "不足 1 天";
        }
        if (days < 60) {
            return days + " 天";
        }
        int m = Math.round(days / 30.4375f);
        if (m < 24) {
            return m + " 个月";
        }
        return String.format(Locale.CHINA, "%.1f 年", days / 365.25);
    }

    private static int daysBetween(long aDayStart, long bDayStart) {
        return (int) Math.round((bDayStart - aDayStart) / (double) DAY);
    }

    private static Calendar cal(long ms) {
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.setTimeInMillis(ms);
        return c;
    }

    private static long startOfDay(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long startOfWeek(long ms) {
        Calendar c = cal(ms);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int diff = dow - Calendar.MONDAY;
        if (diff < 0) {
            diff += 7;
        }
        c.add(Calendar.DAY_OF_MONTH, -diff);
        return c.getTimeInMillis();
    }

    private static long startOfMonth(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long startOfYear(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int hour(long ms) {
        return cal(ms).get(Calendar.HOUR_OF_DAY);
    }

    private static int weekday(long ms) {
        return cal(ms).get(Calendar.DAY_OF_WEEK) - 1;
    }

    private static int yearOf(long ms) {
        return cal(ms).get(Calendar.YEAR);
    }

    private static String monthKey(long ms) {
        Calendar c = cal(ms);
        return String.format(Locale.CHINA, "%04d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    private static String prettyMonth(String key) {
        String[] p = key.split("-");
        if (p.length != 2) {
            return key;
        }
        return p[0] + "年" + Integer.parseInt(p[1]) + "月";
    }

    private static String weekKey(long ms) {
        Calendar c = cal(ms);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4);
        return String.format(Locale.CHINA, "%04d-W%02d",
                c.get(Calendar.YEAR), c.get(Calendar.WEEK_OF_YEAR));
    }

    private static String prettyWeek(String key) {
        return key.replace("-W", " 第") + "周";
    }

    private static final class Totals {
        int n;
        double m;
        long moveMs;
        long pauseMs;
        int steps;
        int withSteps;
        double kcal;
        double maxAvgMps;
        double maxInstMps;

        void add(TrackSession s) {
            n++;
            m += s.distanceM;
            moveMs += Math.max(0, s.movingMs);
            pauseMs += Math.max(0, s.pausedMs);
            steps += Math.max(0, s.steps);
            if (s.steps > 0) {
                withSteps++;
            }
            kcal += Math.max(0, s.calories);
            double a = s.avgSpeedMps();
            if (a > maxAvgMps) {
                maxAvgMps = a;
            }
            if (s.maxSpeedMps > maxInstMps) {
                maxInstMps = s.maxSpeedMps;
            }
        }

        double avgMps() {
            if (moveMs < 1000 || m <= 0) {
                return 0;
            }
            return m / (moveMs / 1000.0);
        }
    }

    private static final class Window {
        int n;
        double m;
        int steps;

        void add(TrackSession s) {
            n++;
            m += s.distanceM;
            steps += Math.max(0, s.steps);
        }
    }
}
