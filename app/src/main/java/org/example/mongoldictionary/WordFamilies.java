package org.example.mongoldictionary;

import org.json.JSONObject;
import java.util.*;

/** Explicit grouping can override the conservative transliteration word-boundary fallback. */
public final class WordFamilies {
    public static String family(JSONObject row) {
        String explicit=row.optString("family_id","").trim();if(!explicit.isEmpty())return "explicit:"+explicit;
        String value=row.optString("transliteration","").trim();
        int end=value.length();for(int i=0;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))||value.charAt(i)=='-'){end=i;break;}
        return end==0?"entry:"+row.optString("id"):"head:"+value.substring(0,end);
    }
    public static LinkedHashMap<String,List<String>> groups(Collection<JSONObject> rows) {
        LinkedHashMap<String,List<String>> result=new LinkedHashMap<>();
        for(JSONObject row:rows)result.computeIfAbsent(family(row),k->new ArrayList<>()).add(row.optString("id"));return result;
    }
    public static List<String> expand(Collection<String> selected,Collection<JSONObject> rows) {
        Set<String> ids=new HashSet<>(selected),families=new HashSet<>();for(JSONObject row:rows)if(ids.contains(row.optString("id")))families.add(family(row));
        List<String> result=new ArrayList<>();for(JSONObject row:rows)if(families.contains(family(row)))result.add(row.optString("id"));return result;
    }
    public static List<String> random(Collection<JSONObject> rows,Set<String> enrolled,int count,Random random) {
        List<List<String>> candidates=new ArrayList<>();for(List<String> group:groups(rows).values())if(!enrolled.containsAll(group))candidates.add(group);
        Collections.shuffle(candidates,random);List<String> result=new ArrayList<>();for(int i=0;i<Math.min(count,candidates.size());i++)result.addAll(candidates.get(i));return result;
    }
}
