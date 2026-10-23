package org.example.mongoldictionary;
import android.content.Context;
import org.json.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Display overlay only. Original definitions, pronunciation and audio remain unchanged. */
final class ReferenceDefinitions {
 static String text(JSONObject row){return row.optString("resolved_definition",row.optString("definition"));}
 static void apply(Context context,List<JSONObject> rows)throws Exception {
  try(InputStream in=context.getAssets().open("supplemental-entries.json")){
   JSONArray additions=new JSONArray(new String(DictionaryStore.read(in,1024*1024),StandardCharsets.UTF_8));
   Set<String> ids=new HashSet<>(),heads=new HashSet<>();
   for(JSONObject row:rows){ids.add(row.optString("id"));heads.add(row.optString("mongolian")+"\u0001"+row.optString("transliteration"));}
   for(int i=0;i<additions.length();i++){
    JSONObject row=additions.getJSONObject(i);String head=row.optString("mongolian")+"\u0001"+row.optString("transliteration");
    if(!ids.contains(row.optString("id"))&&!heads.contains(head)){rows.add(row);ids.add(row.optString("id"));heads.add(head);}
   }
  }

  try(InputStream in=context.getAssets().open("headword-corrections.json")){
   JSONArray corrections=new JSONArray(new String(DictionaryStore.read(in,1024*1024),StandardCharsets.UTF_8));
   Map<String,JSONObject> heads=new HashMap<>();for(JSONObject row:rows)heads.put(row.optString("id"),row);
   for(int i=0;i<corrections.length();i++){
    JSONObject c=corrections.getJSONObject(i),row=heads.get(c.getString("id"));
    if(row!=null&&row.optString("mongolian").equals(c.getString("original_mongolian"))&&row.optString("transliteration").equals(c.getString("original_transliteration"))){
     row.put("mongolian",c.getString("mongolian"));row.put("transliteration",c.getString("transliteration"));
    }
   }
  }
  JSONArray mappings;
  try(InputStream in=context.getAssets().open("reference-resolutions.json")){mappings=new JSONArray(new String(DictionaryStore.read(in,8*1024*1024),StandardCharsets.UTF_8));}
  Map<String,JSONObject> byId=new HashMap<>();for(JSONObject row:rows){row.remove("resolved_definition");row.remove("definition_reference");byId.put(row.optString("id"),row);}
  for(int i=0;i<mappings.length();i++){
   JSONObject m=mappings.getJSONObject(i),row=byId.get(m.getString("id")),target=byId.get(m.getString("target_id"));
   if(row==null||target==null||!row.optString("definition").equals(m.getString("original"))||!target.optString("definition").equals(m.getString("target_definition")))continue;
   // An imported change to an intermediate link must also invalidate the overlay.
   JSONArray chain=m.getJSONArray("chain");boolean valid=true;
   for(int j=0;j<chain.length();j++)if(!byId.containsKey(chain.getString(j))||!byId.get(chain.getString(j)).optString("definition").equals(m.getJSONArray("chain_definitions").getString(j)))valid=false;
   if(!valid)continue;
   row.put("resolved_definition",target.getString("definition"));row.put("definition_reference",target.optString("transliteration"));
  }
  try(InputStream in=context.getAssets().open("definition-corrections.json")){
   JSONArray corrections=new JSONArray(new String(DictionaryStore.read(in,1024*1024),StandardCharsets.UTF_8));
   for(int i=0;i<corrections.length();i++){
    JSONObject c=corrections.getJSONObject(i),row=byId.get(c.getString("id"));
    if(row!=null&&row.optString("transliteration").equals(c.getString("transliteration"))&&row.optString("definition").equals(c.getString("original"))){
     row.put("resolved_definition",c.getString("definition"));row.remove("definition_reference");
    }
   }
  }
  EntryAliases.apply(rows);
 }
}
