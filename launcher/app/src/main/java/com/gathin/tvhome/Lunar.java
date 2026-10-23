package com.gathin.tvhome;

import java.util.Calendar;

/**
 * Vietnamese lunar date (âm lịch), without ICU: Hồ Ngọc Đức's algorithm (new moons and solar terms from
 * Meeus' formulas), at UTC+7 as the Vietnamese calendar is reckoned, whatever the TV's time zone.
 */
final class Lunar {

    private static final double TIME_ZONE = 7.0;
    private static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    private Lunar() {
    }

    /**
     * e.g. "Ngày 13/9 âm lịch · Bính Ngọ", "Mùng 1/1 âm lịch · Đinh Mùi", "Ngày 2/6 nhuận âm lịch · Ất Tỵ":
     * short enough for the line beside the clock.
     */
    static String today() {
        int[] l = todayLunar();
        return (l[0] <= 10 ? "Mùng " : "Ngày ") + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "") + " âm lịch · "
                + yearName(l);
    }

    /**
     * e.g. "Ngày 14 tháng 9 năm Bính Ngọ (âm lịch)", "Mùng 1 tháng 6 nhuận năm Ất Tỵ (âm lịch)": spelled out
     * for the line above the top buttons (lunar_top_right), which has room for it.
     */
    static String todayLong() {
        int[] l = todayLunar();
        return (l[0] <= 10 ? "Mùng " : "Ngày ") + l[0] + " tháng " + l[1] + (l[3] == 1 ? " nhuận" : "")
                + " năm " + yearName(l) + " (âm lịch)";
    }

    private static int[] todayLunar() {
        Calendar c = Calendar.getInstance();
        return fromSolar(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    private static String yearName(int[] l) {
        return CAN[(l[2] + 6) % 10] + " " + CHI[(l[2] + 8) % 12];
    }

    /** Solar day/month/year to {lunarDay, lunarMonth, lunarYear, leap (1 or 0)}. */
    static int[] fromSolar(int dd, int mm, int yy) {
        int dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = newMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = newMoonDay(k);
        int a11 = lunarMonth11(yy), b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = lunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (monthStart - a11) / 29;
        int leap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapDiff = leapMonthOffset(a11);
            if (diff >= leapDiff) {
                lunarMonth = diff + 10;
                if (diff == leapDiff) leap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new int[]{lunarDay, lunarMonth, lunarYear, leap};
    }

    private static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return jd;
    }

    /** Day number of the k-th new moon after 1900-01-01, local to UTC+7. */
    private static int newMoonDay(int k) {
        double t = k / 1236.85, t2 = t * t, t3 = t2 * t, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        c1 = c1 - 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * mpr);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 = c1 - 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 = c1 + 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;
        return (int) Math.floor(jd1 + c1 - deltaT + 0.5 + TIME_ZONE / 24);
    }

    /** Sun longitude at the start of that day, in 30-degree sectors (0..11). */
    private static int sunLongitude(int jdn) {
        double t = (jdn - 2451545.5 - TIME_ZONE / 24) / 36525, t2 = t * t, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        l = l - Math.PI * 2 * Math.floor(l / (Math.PI * 2));
        return (int) Math.floor(l / Math.PI * 6);
    }

    /** Start of the 11th lunar month (the one holding the winter solstice) of that year. */
    private static int lunarMonth11(int yy) {
        int off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = newMoonDay(k);
        if (sunLongitude(nm) >= 9) nm = newMoonDay(k - 1);
        return nm;
    }

    /** In a 13-month year, which month after the 11th is the leap one (no solar term starts in it). */
    private static int leapMonthOffset(int a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last, i = 1;
        int arc = sunLongitude(newMoonDay(k + i));
        do {
            last = arc;
            i++;
            arc = sunLongitude(newMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }
}
