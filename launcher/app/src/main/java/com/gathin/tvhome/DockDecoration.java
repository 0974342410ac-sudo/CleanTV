package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. With a title (look "google")
 * there is no band: the title sits above the row instead, as Google TV's "Your apps".
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final String title;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this(dockSize, density, null);
    }

    DockDecoration(int dockSize, float density, String title) {
        this.dockSize = dockSize;
        this.density = density;
        this.title = title;
        band.setColor(0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x1FFFFFFF);
        titlePaint.setColor(0xFFE8EAED);
        titlePaint.setTextSize(17 * density);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        if (title != null) {
            // Left-aligned with the first card, clear of the focused card's zoom and ring.
            c.drawText(title, rect.left, rect.top - 18 * density, titlePaint);
            return;
        }
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 26 * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
