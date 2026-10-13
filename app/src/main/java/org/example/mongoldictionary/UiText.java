package org.example.mongoldictionary;
import android.content.Context;
import java.util.Locale;
import org.json.JSONObject;
public final class UiText {
    private static JSONObject english;
    public static String language(Context c){
        String value=c.getSharedPreferences("ui",0).getString("language","zh");
        if(value.equals("system"))value=c.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return value.equals("en")?"en":"zh";
    }
    public static String get(Context c,String text){
        String language=language(c);
        if(!language.equals("en"))return text;
        try {
            if(english==null)try(java.io.InputStream in=c.getAssets().open("ui-en.json")){english=new JSONObject(new String(DictionaryStore.read(in,200000),java.nio.charset.StandardCharsets.UTF_8));}
            return english.optString(text,text);
        }catch(Exception e){return text;}
    }
}
