package vn.cleantv.language;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * The language page of the XGIMI H2 firmware (build-firmware-h2-vi.ps1), in place of XGIMI's, which has no Vietnamese. It
 * answers the action of the Settings row (com.xgimi.settings.language) at a higher priority than XGIMI's page and changes
 * the system language the same way that page does. Needs CHANGE_CONFIGURATION, so it lives in /system/priv-app.
 */
public final class NgonNgu extends Activity {
    private static final String[][] LANGUAGES = {{"Tiếng Việt", "vi", "VN"}, {"English", "en", "US"}};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackground(new ColorDrawable(0xFF101419));
        TextView title = new TextView(this);
        title.setText("Vietnamese".equals(Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)) ? "Ngôn ngữ" : "Language");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-2, -2));
        String current = getResources().getConfiguration().locale.getLanguage();
        Button first = null, chosen = null;
        for (String[] l : LANGUAGES) {
            Button b = new Button(this);
            boolean on = l[1].equals(current);
            b.setText(on ? "✓  " + l[0] : l[0]);
            b.setAllCaps(false);
            b.setTextSize(22);
            b.setTextColor(Color.WHITE);
            b.setBackground(rowBackground(dp));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choose(new Locale(l[1], l[2]));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (420 * dp), (int) (64 * dp));
            lp.topMargin = (int) (16 * dp);
            page.addView(b, lp);
            if (first == null) first = b;
            if (on) chosen = b;
        }
        setContentView(page);
        (chosen != null ? chosen : first).requestFocus();
    }

    private static StateListDrawable rowBackground(float dp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, row(0xFF3D7BFF, dp));
        s.addState(new int[]{android.R.attr.state_pressed}, row(0xFF3D7BFF, dp));
        s.addState(new int[]{}, row(0xFF262C33, dp));
        return s;
    }

    private static GradientDrawable row(int color, float dp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(12 * dp);
        return g;
    }

    /** Hidden API, reached by reflection as XGIMI's own page does it; Android saves the language in persist.sys.locale. */
    private void choose(Locale locale) {
        try {
            Object am = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
            Class<?> type = Class.forName("android.app.IActivityManager");
            Configuration c = (Configuration) type.getMethod("getConfiguration").invoke(am);
            c.setLocale(locale);
            Configuration.class.getField("userSetLocale").setBoolean(c, true);
            type.getMethod("updateConfiguration", Configuration.class).invoke(am, c);
            finish();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Toast.makeText(this, String.valueOf(e), Toast.LENGTH_LONG).show();
        }
    }
}
