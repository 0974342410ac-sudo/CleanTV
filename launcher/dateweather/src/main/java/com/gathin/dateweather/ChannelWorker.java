package com.gathin.dateweather;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.tvprovider.media.tv.PreviewChannel;
import androidx.tvprovider.media.tv.PreviewChannelHelper;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Builds and refreshes the "Ngay gio - Thoi tiet" preview channel. */
public class ChannelWorker extends Worker {

    private static final String PROVIDER_ID = "cleantv-dateweather";
    private static final Locale VI = new Locale("vi", "VN");

    public ChannelWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    /** Enqueue an immediate build plus an hourly refresh. */
    static void schedule(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx.getApplicationContext());
        // One serialized immediate build (REPLACE cancels any in-flight one), plus hourly refresh.
        wm.enqueueUniqueWork("dateweather-build", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ChannelWorker.class).build());
        PeriodicWorkRequest hourly = new PeriodicWorkRequest.Builder(
                ChannelWorker.class, 1, TimeUnit.HOURS).build();
        wm.enqueueUniquePeriodicWork("dateweather-refresh",
                ExistingPeriodicWorkPolicy.KEEP, hourly);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Weather w = null;
            try {
                w = Weather.fetch();
            } catch (Exception netErr) {
                // keep going with the date card even if the network failed
            }
            publish(w);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void publish(Weather w) throws Exception {
        Context ctx = getApplicationContext();
        PreviewChannelHelper helper = new PreviewChannelHelper(ctx);

        List<Long> mine = new ArrayList<>();
        for (PreviewChannel ch : helper.getAllChannels()) {
            if (PROVIDER_ID.equals(ch.getInternalProviderId())) mine.add(ch.getId());
        }

        Bitmap logo = logoBitmap();
        PreviewChannel channel = new PreviewChannel.Builder()
                .setDisplayName("Ngày giờ · Thời tiết")
                .setDescription(Weather.CITY)
                .setInternalProviderId(PROVIDER_ID)
                .setLogo(logo)
                .setAppLinkIntentUri(Uri.parse(launchUri()))
                .build();

        long channelId;
        List<Long> oldProgramIds = new ArrayList<>();
        if (mine.isEmpty()) {
            channelId = helper.publishChannel(channel);
            TvContractCompat.requestChannelBrowsable(ctx, channelId);
        } else {
            channelId = mine.get(0);
            // drop any duplicate channels from earlier races
            for (int i = 1; i < mine.size(); i++) {
                ctx.getContentResolver().delete(TvContractCompat.buildChannelUri(mine.get(i)), null, null);
            }
            helper.updatePreviewChannel(channelId, channel);
            // remember the current cards; they are removed AFTER the fresh ones are added,
            // so the channel is never momentarily empty (which could unpin/hide it).
            Uri progs = TvContractCompat.buildPreviewProgramsUriForChannel(channelId);
            try (android.database.Cursor cur = ctx.getContentResolver().query(
                    progs, new String[]{TvContractCompat.PreviewPrograms._ID}, null, null, null)) {
                if (cur != null) {
                    while (cur.moveToNext()) {
                        oldProgramIds.add(cur.getLong(0));
                    }
                }
            }
        }

        List<Card> cards = buildCards(w);
        String intentUri = launchUri();
        int weight = cards.size();
        for (Card c : cards) {
            Uri art = renderCard(c);
            PreviewProgram program = new PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(c.title)
                    .setDescription(c.subtitle)
                    .setPosterArtUri(art)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setIntentUri(Uri.parse(intentUri))
                    .setInternalProviderId(PROVIDER_ID + "-" + c.key)
                    .setWeight(weight--)
                    .build();
            helper.publishPreviewProgram(program);
        }

        // now that fresh cards are in, remove the previous ones
        for (long oldId : oldProgramIds) {
            helper.deletePreviewProgram(oldId);
        }
    }

    // ---- card model ----

    private static final class Card {
        String key, title, subtitle, big, emoji;
        int c1, c2;
    }

    private List<Card> buildCards(Weather w) {
        List<Card> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        // Date card
        Card d = new Card();
        d.key = "date";
        d.title = weekday(cal) + ", " + new SimpleDateFormat("dd/MM/yyyy", VI).format(cal.getTime());
        d.subtitle = "Hôm nay";
        d.big = new SimpleDateFormat("dd", VI).format(cal.getTime());
        d.emoji = "📅";
        d.c1 = Color.parseColor("#1e3a8a");
        d.c2 = Color.parseColor("#2563eb");
        list.add(d);

        if (w != null) {
            Card now = new Card();
            now.key = "now";
            now.title = Weather.CITY + " · " + Math.round(w.currentTemp) + "°";
            now.subtitle = Weather.codeText(w.currentCode);
            now.big = Math.round(w.currentTemp) + "°";
            now.emoji = Weather.codeEmoji(w.currentCode);
            now.c1 = Color.parseColor("#0f766e");
            now.c2 = Color.parseColor("#0ea5e9");
            list.add(now);

            // next days (skip index 0 = today, already implied by "now")
            String[] labels = {"Hôm nay", "Ngày mai", "Ngày kia", ""};
            for (int i = 1; i < w.dayDate.length && i < 4; i++) {
                Card f = new Card();
                f.key = "day" + i;
                String lbl = (i < labels.length && !labels[i].isEmpty()) ? labels[i] : dayLabel(w.dayDate[i]);
                f.title = lbl;
                f.subtitle = Weather.codeText(w.dayCode[i]) + " · "
                        + Math.round(w.dayMin[i]) + "°–" + Math.round(w.dayMax[i]) + "°";
                f.big = Math.round(w.dayMax[i]) + "°";
                f.emoji = Weather.codeEmoji(w.dayCode[i]);
                f.c1 = Color.parseColor("#3730a3");
                f.c2 = Color.parseColor("#6366f1");
                list.add(f);
            }
        }
        return list;
    }

    private String weekday(Calendar cal) {
        switch (cal.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY: return "Thứ Hai";
            case Calendar.TUESDAY: return "Thứ Ba";
            case Calendar.WEDNESDAY: return "Thứ Tư";
            case Calendar.THURSDAY: return "Thứ Năm";
            case Calendar.FRIDAY: return "Thứ Sáu";
            case Calendar.SATURDAY: return "Thứ Bảy";
            default: return "Chủ Nhật";
        }
    }

    private String dayLabel(String isoDate) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyy-MM-dd", VI).parse(isoDate));
            return weekday(c);
        } catch (Exception e) {
            return isoDate;
        }
    }

    // ---- drawing ----

    private Uri renderCard(Card c) throws Exception {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, w, h, c.c1, c.c2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);

        Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);
        emoji.setColor(Color.WHITE);
        emoji.setTextSize(96);
        canvas.drawText(c.emoji, 28, 150, emoji);

        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        big.setColor(Color.WHITE);
        big.setFakeBoldText(true);
        big.setTextSize(96);
        big.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(c.big, w - 28, 150, big);

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setFakeBoldText(true);
        t.setTextSize(34);
        canvas.drawText(ellipsize(c.title, t, w - 56), 28, 210, t);

        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setColor(Color.argb(220, 255, 255, 255));
        s.setTextSize(26);
        canvas.drawText(ellipsize(c.subtitle, s, w - 56), 28, 246, s);

        File dir = ImageProvider.imageDir(getApplicationContext());
        File f = new File(dir, c.key + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        // cache-bust so the launcher reloads the refreshed image
        return ImageProvider.uriFor(c.key + ".png").buildUpon()
                .appendQueryParameter("v", String.valueOf(System.currentTimeMillis())).build();
    }

    private String ellipsize(String text, Paint p, float maxW) {
        if (text == null) return "";
        if (p.measureText(text) <= maxW) return text;
        String ell = "…";
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 1 && p.measureText(sb + ell) > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + ell;
    }

    private Bitmap logoBitmap() {
        int s = 160;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, s, s,
                Color.parseColor("#2563eb"), Color.parseColor("#0ea5e9"), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, s, s, bg);
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setColor(Color.WHITE);
        e.setTextSize(90);
        e.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⛅", s / 2f, s / 2f + 32, e);
        return bmp;
    }

    private String launchUri() {
        Context ctx = getApplicationContext();
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage("com.apalon.weatherlive");
        if (i == null) {
            i = new Intent(ctx, TriggerActivity.class);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i.toUri(Intent.URI_INTENT_SCHEME);
    }
}
