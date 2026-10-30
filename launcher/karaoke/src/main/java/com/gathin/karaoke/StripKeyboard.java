package com.gathin.karaoke;

import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * The search strip's own keyboard, for the remote: three low rows inside the strip, so unlike the system keyboard it
 * never covers the lyrics at the bottom of the video. Letters only until the "123" key swaps the top row for digits;
 * no marks to type: YouTube finds "noi nay co anh" just the same. Long OK on the delete key clears the box.
 */
final class StripKeyboard {
    private StripKeyboard() { }

    private static final String TOP_LETTERS = "qwertyuiop", TOP_DIGITS = "1234567890";

    /** Fills into with the rows; keys type into target, the search key runs search. */
    static void build(LinearLayout into, EditText target, Runnable search) {
        LinearLayout row = row(into, 0);
        Button[] top = new Button[TOP_LETTERS.length()];
        for (int i = 0; i < top.length; i++) {
            top[i] = key(row, String.valueOf(TOP_LETTERS.charAt(i)), 1f, null);
            Button b = top[i];
            b.setOnClickListener(v -> type(target, b.getText().toString()));
        }
        Button delete = key(row, into.getContext().getString(R.string.key_delete), 2f, v -> {
            Editable text = target.getText();
            if (text.length() > 0) text.delete(text.length() - 1, text.length());
        });
        delete.setOnLongClickListener(v -> {
            target.setText("");
            return true;
        });
        row = row(into, 3);
        letters(row, "asdfghjkl", target);
        key(row, into.getContext().getString(R.string.key_search), 3f, v -> search.run());
        row = row(into, 3);
        letters(row, "zxcvbnm", target);
        Button digits = key(row, into.getContext().getString(R.string.key_digits), 1.5f, null);
        digits.setOnClickListener(v -> {
            boolean toDigits = top[0].getText().toString().equals(String.valueOf(TOP_LETTERS.charAt(0)));
            String chars = toDigits ? TOP_DIGITS : TOP_LETTERS;
            for (int i = 0; i < top.length; i++) top[i].setText(String.valueOf(chars.charAt(i)));
            digits.setText(toDigits ? R.string.key_letters : R.string.key_digits);
        });
        key(row, into.getContext().getString(R.string.key_space), 3.5f, v -> type(target, " "));
    }

    private static LinearLayout row(LinearLayout into, int topDp) {
        LinearLayout row = new LinearLayout(into.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(topDp * into.getResources().getDisplayMetrics().density);
        into.addView(row, lp);
        return row;
    }

    private static void letters(LinearLayout row, String chars, EditText target) {
        for (char c : chars.toCharArray()) {
            String s = String.valueOf(c);
            key(row, s, 1f, v -> type(target, s));
        }
    }

    private static Button key(LinearLayout row, String label, float weight, View.OnClickListener click) {
        Button b = (Button) LayoutInflater.from(row.getContext()).inflate(R.layout.item_key, row, false);
        b.setText(label);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.weight = weight;
        lp.setMarginEnd(Math.round(3 * row.getResources().getDisplayMetrics().density));
        b.setOnClickListener(click);
        row.addView(b);
        return b;
    }

    private static void type(EditText target, String s) {
        target.append(s);
        target.setSelection(target.length());
    }
}
