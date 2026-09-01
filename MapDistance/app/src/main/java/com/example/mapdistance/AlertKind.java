package com.example.mapdistance;

/** 五项提醒：到达公里 / 到达步数 / 间隔公里 / 间隔步数 / 今日步数。 */
public enum AlertKind {
    TRIP_KM("trip_km", "这次公里", "这次走到多少公里", "选这次公里"),
    TRIP_STEPS("trip_steps", "这次步数", "这次走到多少步", "选这次步数"),
    EVERY_KM("every_km", "每N公里", "每走多少公里提醒一次", "选每公里"),
    EVERY_STEPS("every_steps", "每N步", "每走多少步提醒一次", "选每步"),
    TODAY("today", "今日步数", "今日芯片步数到多少", "选今日步数");

    public final String id;
    public final String title;
    public final String setting;
    public final String pick;

    AlertKind(String id, String title, String setting, String pick) {
        this.id = id;
        this.title = title;
        this.setting = setting;
        this.pick = pick;
    }

    String ringKey() {
        return "ring_" + id;
    }

    String voiceKey() {
        return "voice_" + id;
    }

    String uriKey() {
        return "ring_uri_" + id;
    }

    String nameKey() {
        return "ring_name_" + id;
    }
}
