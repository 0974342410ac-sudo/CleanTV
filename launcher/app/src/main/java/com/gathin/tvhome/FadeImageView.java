package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * look "google": the hero art. The picture itself fades to transparent towards the left and the bottom, so it melts
 * into whatever is behind it (background colour, ambient glow, wallpaper) instead of ending in an edge.
 */
public class FadeImageView extends ImageView {

    private final Paint left = new Paint();
    private final Paint bottom = new Paint();
    private final Paint top = new Paint();

    public FadeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        PorterDuffXfermode keep = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        left.setXfermode(keep);
        bottom.setXfermode(keep);
        top.setXfermode(keep);
        // Drawn into its own texture: DST_IN then only erases the picture, and the spring of the focus ring
        // over it does not replay the masks.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        left.setShader(new LinearGradient(0, 0, w * 0.55f, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        bottom.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        // Faint under the top bar, so Wi-Fi, Settings and the clock stay readable over a white banner
        top.setShader(new LinearGradient(0, h * 0.08f, 0, h * 0.4f, 0x1A000000, 0xFF000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), left);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bottom);
        canvas.drawRect(0, 0, getWidth(), getHeight(), top);
    }
}
