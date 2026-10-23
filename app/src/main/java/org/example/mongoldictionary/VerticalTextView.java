package org.example.mongoldictionary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.TextPaint;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Shape complete Mongolian words horizontally, then rotate the shaped result. */
public final class VerticalTextView extends View {
    private final TextPaint paint;
    private final List<Piece> pieces = new ArrayList<>();
    private final float inset, gap;
    private float contentWidth, contentHeight;

    static final class Piece {
        final String text;
        final boolean sideways;
        final Layout layout;
        final float advance, breadth;
        float x, y;
        Piece(Context context, String text, boolean sideways, TextPaint paint) {
            this.text = text;
            this.sideways = sideways;
            CharSequence styled = DictionaryFont.apply(context, verticalPunctuation(text));
            int width = Math.max(1, (int)Math.ceil(Layout.getDesiredWidth(styled, paint)) + 1);
            TextView nativeText = new TextView(context);
            nativeText.getPaint().set(paint);
            nativeText.setTextColor(paint.getColor());
            nativeText.setText(styled);
            nativeText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            nativeText.layout(0, 0, width, nativeText.getMeasuredHeight());
            layout = nativeText.getLayout();
            advance = sideways ? width : layout.getHeight();
            breadth = sideways ? layout.getHeight() : width;
        }
    }

    public VerticalTextView(Context context, String text, int sizeSp, int heightDp) {
        super(context);
        // Match the native list's default typeface and Android shaping engine.
        TextView reference = new TextView(context);
        reference.setTextSize(sizeSp);
        paint = new TextPaint(reference.getPaint());
        paint.setColor(Color.rgb(25, 57, 53));
        float density = getResources().getDisplayMetrics().density;
        inset = 12 * density;
        gap = 10 * density;
        for (String token : tokens(text)) {
            int cp = token.codePointAt(0);
            pieces.add(new Piece(context, token, isSideways(cp), paint));
        }
        contentHeight = heightDp * density;
        for (Piece piece : pieces) contentHeight = Math.max(contentHeight, piece.advance + 2 * inset);
        for(int i=0;i<pieces.size();){int end=groupEnd(i);float height=0;for(int j=i;j<end;j++)height+=pieces.get(j).advance;contentHeight=Math.max(contentHeight,height+2*inset);i=end;}
        arrange();
        setOnLongClickListener(v->{TextCopy.copy(context,text);return true;});
        setContentDescription(text);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    // Presentation only: keep the original characters in the dictionary and accessibility text.
    static String verticalPunctuation(String text) {
        String horizontal = "()（）〔〕【】〈〉《》「」『』［］｛｝";
        String vertical =   "︵︶︵︶︹︺︻︼︿﹀︽︾﹁﹂﹃﹄﹇﹈︷︸";
        if (text.length() == 1) {
            int index = horizontal.indexOf(text.charAt(0));
            if (index >= 0) return String.valueOf(vertical.charAt(index));
        }
        return text;
    }

    static boolean isSideways(int cp) {
        // Circled sense numbers are read upright; brackets use vertical presentation glyphs.
        if ((cp >= 0x2460 && cp <= 0x24ff) || "()（）〔〕【】〈〉《》「」『』［］｛｝".indexOf(cp) >= 0) return false;
        return (cp >= 0x1800 && cp <= 0x18af) || cp == 0x202f
                || (cp < 0x2e80 && !Character.isWhitespace(cp));
    }

    /** ASCII spaces allow wrapping; NNBSP, MVS and selectors stay with the word. */
    static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int cp = text.codePointAt(start);
            int end = start + Character.charCount(cp);
            if (isSideways(cp)) {
                while (end < text.length()) {
                    int next = text.codePointAt(end);
                    if (!isSideways(next) || next == '\n' || next == ' ') break;
                    end += Character.charCount(next);
                }
            } else {
                while (end < text.length()) {
                    int next = text.codePointAt(end);
                    int type = Character.getType(next);
                    if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                            && next != 0xfe0f) break;
                    end += Character.charCount(next);
                }
            }
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    private static boolean mongolian(String value){
        boolean letter=false;for(int i=0;i<value.length();){int cp=value.codePointAt(i);if(cp>=0x1800&&cp<=0x18af)letter=true;else if(cp!=0x202f)return false;i+=Character.charCount(cp);}return letter;
    }
    // Optional Mongolian endings are inseparable from their brackets and headword.
    private int groupEnd(int start){
        int opening=start;
        if(mongolian(pieces.get(start).text))opening++;
        if(opening+2<pieces.size()){
            String left=pieces.get(opening).text,right=pieces.get(opening+2).text;
            if(((left.equals("(")&&right.equals(")"))||(left.equals("（")&&right.equals("）")))&&mongolian(pieces.get(opening+1).text))return opening+3;
        }
        return start+1;
    }
    private void arrange() {
        float x = inset, y = inset, breadth = 0;
        List<Piece> column = new ArrayList<>();
        int groupLimit=0;
        for (int i=0;i<pieces.size();i++) {
            Piece piece=pieces.get(i);boolean beginning=i>=groupLimit;float required=piece.advance;
            if(beginning){groupLimit=groupEnd(i);required=0;for(int j=i;j<groupLimit;j++)required+=pieces.get(j).advance;}
            boolean newline = piece.text.equals("\n");
            if (newline || (beginning && y > inset && y + required > contentHeight - inset)) {
                center(column, x, breadth);
                x += Math.max(breadth, paint.getTextSize()) + gap;
                y = inset;
                breadth = 0;
                column.clear();
            }
            if (newline) continue;
            if (piece.text.equals(" ") || piece.text.equals("\t")) {
                if (y > inset) y += paint.measureText(" ");
                continue;
            }
            piece.y = y;
            y += piece.advance;
            breadth = Math.max(breadth, piece.breadth);
            column.add(piece);
        }
        center(column, x, breadth);
        contentWidth = x + Math.max(breadth, paint.getTextSize()) + inset;
    }

    private static void center(List<Piece> column, float x, float width) {
        for (Piece piece : column) piece.x = x + (width - piece.breadth) / 2;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int)Math.ceil(contentWidth), widthSpec),
                resolveSize((int)Math.ceil(contentHeight), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Piece piece : pieces) {
            if (piece.text.trim().isEmpty()) continue;
            canvas.save();
            if (piece.sideways) {
                canvas.translate(piece.x + piece.breadth, piece.y);
                canvas.rotate(90);
            } else canvas.translate(piece.x, piece.y);
            piece.layout.draw(canvas);
            canvas.restore();
        }
    }
}
