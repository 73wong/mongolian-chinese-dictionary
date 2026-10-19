package org.example.mongoldictionary;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import java.util.List;

public class RenderingChecks extends LocalImport {
    private boolean performanceOnly;
    private boolean offlineOnly;
    private boolean studyOnly;
    private boolean importMode;
    private boolean guideOnly;
    private boolean storageOnly;
    private boolean searchOnly;
    @Override public void onCreate(Bundle arguments) { performanceOnly=arguments!=null&&"true".equals(arguments.getString("performanceOnly")); studyOnly=arguments!=null&&"true".equals(arguments.getString("studyOnly")); offlineOnly=arguments!=null&&"true".equals(arguments.getString("offlineOnly")); searchOnly=arguments!=null&&"true".equals(arguments.getString("searchOnly")); storageOnly=arguments!=null&&"true".equals(arguments.getString("storageOnly")); guideOnly=arguments!=null&&"true".equals(arguments.getString("guideOnly")); importMode=arguments!=null&&arguments.containsKey("token"); super.onCreate(arguments==null?new Bundle():arguments); }
    @Override public void onStart() {
        if(importMode){super.onStart();return;}
        Bundle result = new Bundle();
        try {
            if(performanceOnly){result.putString("stream",PerformanceChecks.run(getTargetContext())+"\n");finish(Activity.RESULT_OK,result);return;}
            if(studyOnly){final Throwable[] gestureError={null};runOnMainSync(()->{try{SwipeUndoChecks.run(getTargetContext());}catch(Throwable e){gestureError[0]=e;}});if(gestureError[0]!=null)throw new AssertionError(gestureError[0]);StudyRoundChecks.run(getTargetContext());StudyChecks.run(getTargetContext());result.putString("stream","Study rounds, defer, undo, persistence and existing study checks passed.\n");finish(Activity.RESULT_OK,result);return;}
            if(offlineOnly){result.putString("stream",OfflineChecks.run(getTargetContext())+"\n");finish(Activity.RESULT_OK,result);return;}
            if(searchOnly){SearchChecks.run();GuideChecks.run(getTargetContext());result.putString("stream","Fuzzy search and language checks passed.\n");finish(Activity.RESULT_OK,result);return;}
            if(storageOnly){ImportChecks.run(getTargetContext());String metrics=StorageScaleChecks.run(getTargetContext());result.putString("stream","Storage checks passed: "+metrics+"\n");finish(Activity.RESULT_OK,result);return;}
            GuideChecks.run(getTargetContext());
            if(guideOnly){result.putString("stream","Guide checks passed: Chinese, English, system language and both images.\n");finish(Activity.RESULT_OK,result);return;}
            SearchChecks.run();
            ImportChecks.checkExpansion(getTargetContext(),getContext());
            StudyChecks.run(getTargetContext());
            ImportChecks.run(getTargetContext());
            ImportChecks.checkDownloadedAudio(getTargetContext(),getContext());
            try (android.content.res.AssetFileDescriptor sound=getContext().getAssets().openFd("kereg.mp3")) {
                android.media.MediaPlayer audio=new android.media.MediaPlayer();
                try {
                    audio.setDataSource(sound.getFileDescriptor(),sound.getStartOffset(),sound.getLength());
                    audio.prepare();
                    if(audio.getDuration()<=0)throw new AssertionError("Audio has no duration");
                    audio.start();
                    if(!audio.isPlaying())throw new AssertionError("Audio did not start");
                } finally {audio.release();}
            }
            Activity copyHost=startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> {
                try {
                String original="〔名〕①ᠬᠡᠷᠡᠭ\u202fᠢᠶᠡᠨ ᠷ\u180eᠡ";
                VerticalTextView copyView=new VerticalTextView(copyHost,original,22,200);
                if(!copyView.performLongClick())throw new AssertionError("Long press not handled");
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)copyHost.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if(!original.contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()))throw new AssertionError("Copy changed original Unicode");
                String[] samples = {"ᠬᠡᠷᠡᠭ\u202fᠢᠶᠡᠨ","ᠬᠡᠷᠡᠭ\u202fᠦᠨ", "ᠶᠠᠭᠤᠮ\u180eᠠ", "ᠳ\u180bᠡ"};
                for (String sample : samples) {
                    List<String> tokens = VerticalTextView.tokens(sample);
                    if (tokens.size()!=1 || !sample.equals(tokens.get(0)))
                        throw new AssertionError("Split shaping context: " + sample);
                    checkPixels(sample);
                }
                String mixed = "非常：ᠬᠡᠷᠡᠭ\u202fᠦᠨ ᠳ\u180bᠡ!\n极其";
                if (!mixed.equals(String.join("", VerticalTextView.tokens(mixed))))
                    throw new AssertionError("Text changed during layout");
                } catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            result.putString("stream", "PASS: expansion 286 entries, 67 recordings decoded, optional-n families, repeated import; long-press exact Unicode copy, unique-word calendar counts, whole-family random selection; study enrollment, daily limits, due priority, hints, undo, pause, persistence and ZIP backup/restore; all 21 downloaded recordings imported by ID and decoded; real source MP3 decoded and playback started; package round trip, stable IDs, partial updates, Unicode CSV, persistence, audio and invalid-package rejection; four contextual forms using source dictionary font match rotated native TextView pixels; suffix/MVS/FVS and mixed text preserved.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void checkPixels(String text) {
        TextView nativeText = new TextView(getTargetContext());
        nativeText.setTextSize(34);
        nativeText.setTextColor(Color.rgb(25,57,53));
        nativeText.setText(DictionaryFont.apply(getTargetContext(), text));
        int width = (int)Math.ceil(Layout.getDesiredWidth(nativeText.getText(), nativeText.getPaint())) + 1;
        nativeText.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        nativeText.layout(0,0,nativeText.getMeasuredWidth(),nativeText.getMeasuredHeight());
        VerticalTextView vertical = new VerticalTextView(getTargetContext(),text,34,260);
        vertical.measure(0,0);
        vertical.layout(0,0,vertical.getMeasuredWidth(),vertical.getMeasuredHeight());
        Bitmap actual = Bitmap.createBitmap(vertical.getWidth(), vertical.getHeight(), Bitmap.Config.ARGB_8888);
        vertical.draw(new Canvas(actual));
        Bitmap expected = Bitmap.createBitmap(actual.getWidth(),actual.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(expected);
        float inset = 12*getTargetContext().getResources().getDisplayMetrics().density;
        canvas.translate(inset+nativeText.getLayout().getHeight(), inset);
        canvas.rotate(90);
        nativeText.draw(canvas);
        if (!actual.sameAs(expected)) {
            try {
                actual.compress(Bitmap.CompressFormat.PNG,100,getTargetContext().openFileOutput("actual.png",0));
                expected.compress(Bitmap.CompressFormat.PNG,100,getTargetContext().openFileOutput("expected.png",0));
            } catch (Exception error) { throw new RuntimeException(error); }
        }
        if (!actual.sameAs(expected)) throw new AssertionError("Native glyph pixel mismatch: "+text
                +" nativeHeight="+nativeText.getLayout().getHeight()+" verticalWidth="+vertical.getWidth());
        if (text.contains("ᠢᠶᠡᠨ")) {
            try (java.io.FileOutputStream out = getTargetContext().openFileOutput("iyen-source-font.png",0)) {
                Bitmap preview=Bitmap.createBitmap(actual.getWidth(),actual.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas background=new Canvas(preview); background.drawColor(Color.rgb(248,247,242));
                background.drawBitmap(actual,0,0,null); preview.compress(Bitmap.CompressFormat.PNG,100,out);
            } catch (Exception error) { throw new RuntimeException(error); }
        }
        actual.recycle(); expected.recycle();
    }
}
