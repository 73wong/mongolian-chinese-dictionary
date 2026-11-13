package org.example.mongoldictionary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Live Mongolian typography with optically balanced, center-aligned columns. */
final class HomeWordmark extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] words={"ᠮᠣᠩᠭᠣᠯ","ᠬᠢᠲᠠᠳ","ᠲᠣᠯᠢ"};
    private final float[] offsets={-76,-25,20};
    private final float density;
    HomeWordmark(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        paint.setTypeface(DictionaryFont.get(context));paint.setTextSize(38*density);paint.setColor(0xff193d37);
        setContentDescription(String.join(" ",words));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        for(int i=0;i<words.length;i++){
            canvas.save();
            canvas.translate(getWidth()/2f+offsets[i]*density,(getHeight()-paint.measureText(words[i]))/2f);
            canvas.rotate(90);canvas.drawText(words[i],0,0,paint);canvas.restore();
        }
    }
}
