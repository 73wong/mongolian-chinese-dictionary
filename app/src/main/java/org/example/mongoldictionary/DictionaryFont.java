package org.example.mongoldictionary;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

final class DictionaryFont {
    private static Typeface mongolian;
    static Typeface get(Context context) {
        if (mongolian == null) mongolian = Typeface.createFromAsset(context.getAssets(), "fonts/MongolianUniversalWhite.ttf");
        return mongolian;
    }
    private static boolean part(char c) {
        return (c >= '\u1800' && c <= '\u18af') || c == '\u202f' || c == '\u200c' || c == '\u200d';
    }
    static CharSequence apply(Context context, String text) {
        SpannableString result = new SpannableString(text);
        for (int i=0; i<text.length();) {
            if (!part(text.charAt(i))) { i++; continue; }
            int start=i++;
            while (i<text.length() && part(text.charAt(i))) i++;
            result.setSpan(new FontSpan(get(context)),start,i,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }
    private static final class FontSpan extends MetricAffectingSpan {
        private final Typeface face;
        FontSpan(Typeface face) { this.face=face; }
        @Override public void updateDrawState(TextPaint paint) { paint.setTypeface(face); }
        @Override public void updateMeasureState(TextPaint paint) { paint.setTypeface(face); }
    }
}
