package vn.cleantv.language;

import android.app.Activity;
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

import java.lang.reflect.Field;

/**
 * The HDMI control page of the XGIMI H2 firmware (build-firmware-h2-vi.ps1). The firmware has MStar's CEC and ARC
 * (TvCecManager, library com.mstar.android) with no switch in its Settings; this page changes the same CecSetting that
 * MStar's own player menu does. The msd838 firmware has no eARC. Reached by reflection, since the classes are only on the unit.
 */
public final class DieuKhienHdmi extends Activity {
    private static final String[][] ROWS = {
            {"cecStatus", "Điều khiển qua HDMI (CEC)"},
            {"arcStatus", "Âm thanh về dàn loa qua HDMI (ARC)"},
            {"autoStandby", "Tắt thiết bị HDMI khi tắt máy"},
            {"audioModeStatus", "Điều khiển âm lượng dàn loa"}};

    private final Button[] buttons = new Button[ROWS.length];
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackground(new ColorDrawable(0xFF101419));
        TextView title = new TextView(this);
        title.setText("Điều khiển HDMI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-2, -2));
        for (int i = 0; i < ROWS.length; i++) {
            final int row = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setTextSize(20);
            b.setTextColor(Color.WHITE);
            b.setBackground(rowBackground(dp));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    flip(ROWS[row][0]);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (620 * dp), (int) (60 * dp));
            lp.topMargin = (int) (14 * dp);
            page.addView(b, lp);
            buttons[i] = b;
        }
        status = new TextView(this);
        status.setTextColor(0xFFACB8C1);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams((int) (620 * dp), -2);
        sp.topMargin = (int) (20 * dp);
        page.addView(status, sp);
        setContentView(page);
        buttons[0].requestFocus();
        show();
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

    private static Object manager() throws ReflectiveOperationException {
        return Class.forName("com.mstar.android.tv.TvCecManager").getMethod("getInstance").invoke(null);
    }

    private static Object read(Object cec) throws ReflectiveOperationException {
        return cec.getClass().getMethod("getCecConfiguration").invoke(cec);
    }

    private void show() {
        try {
            Object setting = read(manager());
            for (int i = 0; i < ROWS.length; i++) {
                int value = ((Number) setting.getClass().getField(ROWS[i][0]).get(setting)).intValue();
                buttons[i].setText(ROWS[i][1] + ": " + (value != 0 ? "Bật" : "Tắt"));
            }
            status.setText("Cần bật CEC ở cả thiết bị HDMI. ARC chỉ có ở cổng HDMI ghi ARC. Máy này không có eARC.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            for (int i = 0; i < ROWS.length; i++) buttons[i].setText(ROWS[i][1]);
            status.setText("Không đọc được cài đặt HDMI của máy: " + e);
        }
    }

    private void flip(String name) {
        try {
            Object cec = manager();
            Object setting = read(cec);
            Field f = setting.getClass().getField(name);
            int next = ((Number) f.get(setting)).intValue() != 0 ? 0 : 1;
            if (f.getType() == short.class) f.setShort(setting, (short) next);
            else if (f.getType() == byte.class) f.setByte(setting, (byte) next);
            else f.setInt(setting, next);
            Object done = cec.getClass().getMethod("setCecConfiguration", setting.getClass()).invoke(cec, setting);
            show();
            if (Boolean.FALSE.equals(done)) status.setText("Máy từ chối thay đổi này.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            status.setText("Không đổi được cài đặt HDMI của máy: " + e);
        }
    }
}
