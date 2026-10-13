package org.example.mongoldictionary;
import android.content.*;
import android.content.res.Configuration;
import org.json.*;
import java.util.*;
public final class GuideChecks {
 static void check(boolean yes){if(!yes)throw new AssertionError("guide/language check failed");}
 public static void run(Context app)throws Exception{
  String name="guide-check-"+UUID.randomUUID();
  Context isolated=new ContextWrapper(app){@Override public SharedPreferences getSharedPreferences(String n,int mode){return app.getSharedPreferences(name,mode);}};
  SharedPreferences prefs=isolated.getSharedPreferences("ui",0);
  try{
   prefs.edit().putString("language","en").commit();check(UiText.language(isolated).equals("en"));check(InputGuideActivity.chartFile(isolated).equals("guide-english.png"));check(UiText.get(isolated,"输入与转写指南").equals("Input & transliteration guide"));
   prefs.edit().putString("language","zh").commit();check(UiText.language(isolated).equals("zh"));check(InputGuideActivity.chartFile(isolated).equals("guide-chinese.png"));
   prefs.edit().putString("language","system").commit();
   for(String tag:new String[]{"en","zh"}){
    Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.setLocale(Locale.forLanguageTag(tag));Context configured=app.createConfigurationContext(cfg);
    Context test=new ContextWrapper(configured){@Override public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}};
    check(UiText.language(test).equals(tag));check(InputGuideActivity.chartFile(test).equals(tag.equals("en")?"guide-english.png":"guide-chinese.png"));
   }
   try(java.io.InputStream in=app.getAssets().open("input-guide.json")){
    JSONObject data=new JSONObject(new String(DictionaryStore.read(in,200000),"UTF-8"));JSONArray sections=data.getJSONArray("sections");check(sections.length()==1);
    for(int i=0;i<sections.length();i++)for(String key:new String[]{"title_zh","title_en","zh","en"})check(!sections.getJSONObject(i).getString(key).isEmpty());
    check(data.getString("source").equals("http://hkuri.cneas.tohoku.ac.jp/guide/p01/"));
    for(String imageName:new String[]{"guide-chinese.png","guide-english.png"})try(java.io.InputStream image=app.getAssets().open("input-guide/"+imageName)){check(android.graphics.BitmapFactory.decodeStream(image)!=null);}
   }
  }finally{app.deleteSharedPreferences(name);}
 }
}
