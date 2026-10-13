package org.example.mongoldictionary;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import org.json.*;

/** Localized keyboard charts and source website instructions. */
public class InputGuideActivity extends Activity {
    private LinearLayout body;
    private ScrollView scroll;
    private String language;
    private int savedY;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String tr(String zh,String en){return language.equals("en")?en:zh;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(25,57,53));t.setTextIsSelectable(true);t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t;}
    private void button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);body.addView(b);b.setOnClickListener(v->action.run());}
    @Override public void onCreate(Bundle state){super.onCreate(state);savedY=state==null?0:state.getInt("guideY");render();}
    @Override public void onResume(){super.onResume();if(!UiText.language(this).equals(language)){savedY=scroll.getScrollY();render();}}
    @Override public void onSaveInstanceState(Bundle out){out.putInt("guideY",scroll.getScrollY());super.onSaveInstanceState(out);}
    static String chartFile(android.content.Context context){return UiText.language(context).equals("en")?"guide-english.png":"guide-chinese.png";}
    private void chart(String file,String description)throws Exception{
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(description);
        try(java.io.InputStream in=getAssets().open("input-guide/"+file)){image.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(in,null));}
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(12),0,dp(16));body.addView(image,params);
        image.setOnClickListener(v->{
            android.webkit.WebView zoom=new android.webkit.WebView(this);zoom.getSettings().setJavaScriptEnabled(false);zoom.getSettings().setBuiltInZoomControls(true);zoom.getSettings().setDisplayZoomControls(false);zoom.getSettings().setSupportZoom(true);zoom.getSettings().setUseWideViewPort(true);zoom.getSettings().setLoadWithOverviewMode(true);
            zoom.loadDataWithBaseURL("file:///android_asset/input-guide/","<html><head><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=5'></head><body style='margin:0;background:white'><img alt='Keyboard chart' style='width:100%' src='"+file+"'></body></html>","text/html","UTF-8",null);
            android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle(description).setView(zoom).setPositiveButton(tr("关闭","Close"),null).create();d.setOnDismissListener(x->zoom.destroy());d.show();d.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.85));
        });
    }
    private void openBrowser(String url){
        android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url));intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
        try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,tr("没有可用浏览器，请复制链接后打开。","No browser available. Copy the link to open it elsewhere."),Toast.LENGTH_LONG).show();}
    }
    private void render(){
        language=UiText.language(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(12),dp(18),dp(8));root.setBackgroundColor(Color.rgb(248,247,242));setContentView(root);
        Button back=new Button(this);back.setText(tr("‹ 返回","‹ Back"));root.addView(back);back.setOnClickListener(v->finish());
        scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        text(tr("输入与转写指南","Input & transliteration guide"),26);
        try(java.io.InputStream in=getAssets().open("input-guide.json")){
            JSONObject guide=new JSONObject(new String(DictionaryStore.read(in,200000),java.nio.charset.StandardCharsets.UTF_8));
            text(guide.getString("note_"+language),16);
            JSONArray sections=guide.getJSONArray("sections");for(int i=0;i<sections.length();i++){JSONObject s=sections.getJSONObject(i);text(s.getString("title_"+language),21);text(s.getString(language),18);}
            chart(chartFile(this),tr("蒙古文字、转写与按键 · 点击放大","Letters, transliteration and keys · Tap to zoom"));
            text(guide.getString("fuzzy_"+language),16);
            text(tr("原站完整说明","Full source guide"),21);String url=guide.getString("source");
            TextView link=text(url,15);link.setTextIsSelectable(false);link.setTextColor(Color.rgb(24,100,122));link.setPaintFlags(link.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);link.setContentDescription(tr("原站链接：点击打开，长按复制","Source link: tap to open, long-press to copy"));
            link.setOnClickListener(v->openBrowser(url));
            link.setOnLongClickListener(v->{TextCopy.copy(this,url);Toast.makeText(this,tr("链接已复制","Link copied"),Toast.LENGTH_SHORT).show();return true;});
            text(tr("点击链接用浏览器打开，长按复制链接。","Tap the link to open it in your browser; long-press to copy."),14);

        }catch(Exception e){text(tr("指南暂时无法读取。","Could not load the guide."),18);}
        scroll.post(()->scroll.scrollTo(0,savedY));
    }
}
