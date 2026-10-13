package org.example.mongoldictionary;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.Locale;
/** Optional transliteration equivalences; other fields and Mongolian controls remain literal. */
public final class SearchRules {
    public static final String[] FIELDS={"mongolian","transliteration","pronunciation_extra","definition"};
    public static String prepare(String s,boolean ignoreCase){String n=Normalizer.normalize(s.trim(),Normalizer.Form.NFC);return ignoreCase?n.toLowerCase(Locale.ROOT):n;}
    public static boolean matches(JSONObject row,String query,int scope,int mode,boolean ignoreCase){
        return matches(row,query,scope,mode,ignoreCase,false);
    }
    public static boolean matches(JSONObject row,String query,int scope,int mode,boolean ignoreCase,boolean fuzzy){
        String q=prepare(query,ignoreCase);if(q.isEmpty())return false;
        for(int i=0;i<FIELDS.length;i++){
            if(scope!=0&&scope!=i+1)continue;
            String value=prepare(row.optString(FIELDS[i]),ignoreCase);
            // Multiple Cyrillic variants are independent alternatives, not a joined word.
            String[] variants=i==2?value.split("\\n"):new String[]{value};
            for(String v:variants)if(fuzzy&&i==1?transliterationMatches(v,q,mode):(mode==0?v.startsWith(q):mode==1?v.equals(q):mode==2?v.endsWith(q):v.contains(q)))return true;
        }return false;
    }
    private static char equivalent(char c){
        if(c=='u')return 'o';if(c=='U')return 'O';
        if(c=='d')return 't';if(c=='g')return 'k';
        return c;
    }
    private static boolean transliterationMatches(String value,String query,int mode){
        int last=value.length()-query.length();if(last<0)return false;
        if(mode==1&&last!=0)return false;
        int first=mode==2?last:0,end=mode==0||mode==1?0:last;
        for(int offset=first;offset<=end;offset++){
            boolean matched=true;
            for(int i=0;i<query.length();i++){
                if(equivalent(value.charAt(offset+i))!=equivalent(query.charAt(i))){matched=false;break;}
            }
            if(matched)return true;
        }return false;
    }
}
