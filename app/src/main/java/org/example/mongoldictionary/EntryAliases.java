package org.example.mongoldictionary;

import org.json.*;
import java.util.*;

/** User-confirmed duplicates: keep the optional-n headwords. */
final class EntryAliases {
 static final String OLD="entry-1b99bb94-7b3d-526b-81b2-9657f0014505";
 static final String KEEP="entry-7a0b1cc0-12ee-5b61-a9c7-da654c9c8eee";
 static final String MORI_OLD="entry-34f37f2a-a4d8-5577-bdf5-79dd1684271c";
 static final String MORI_KEEP="entry-f655db43-3670-55ae-b3f6-3307a1a9dee7";
 static final String[][] PAIRS={{OLD,KEEP},{MORI_OLD,MORI_KEEP}};
 static void migrateFavorites(Set<String> favorites){for(String[] pair:PAIRS)if(favorites.remove(pair[0]))favorites.add(pair[1]);}
 static void apply(List<JSONObject> rows){
  for(String[] pair:PAIRS)apply(rows,pair[0],pair[1]);
 }
 private static void apply(List<JSONObject> rows,String OLD,String KEEP){
  boolean present=false;for(JSONObject row:rows)if(KEEP.equals(row.optString("id")))present=true;
  if(present)rows.removeIf(row->OLD.equals(row.optString("id")));
 }
 static boolean migrate(JSONObject data)throws Exception{
  boolean changed=false;for(String[] pair:PAIRS)changed=migrate(data,pair[0],pair[1])||changed;return changed;
 }
 private static boolean migrate(JSONObject data,String OLD,String KEEP)throws Exception{
  JSONObject cards=data.getJSONObject("cards"),old=cards.optJSONObject(OLD);
  if(old==null)return false;
  JSONObject keep=cards.optJSONObject(KEEP);
  if(keep==null)cards.put(KEEP,old);
  else{
   JSONObject merged=new JSONObject((old.optLong("last")>keep.optLong("last")?old:keep).toString());
   merged.put("seen",old.optLong("seen")+keep.optLong("seen"));
   merged.put("lapses",old.optLong("lapses")+keep.optLong("lapses"));
   cards.put(KEEP,merged);
  }
  cards.remove(OLD);
  JSONArray history=data.optJSONArray("history");
  if(history!=null)for(int i=0;i<history.length();i++){JSONObject event=history.getJSONObject(i);if(OLD.equals(event.optString("id")))event.put("id",KEEP);}
  JSONObject round=data.optJSONObject("round");
  if(round!=null){
   JSONArray ids=round.optJSONArray("ids"),revised=new JSONArray(),results=round.optJSONArray("results"),revisedResults=new JSONArray();Set<String> seen=new HashSet<>();int index=0;
   if(ids!=null)for(int i=0;i<ids.length();i++){String id=ids.getString(i);if(OLD.equals(id))id=KEEP;if(seen.add(id)){revised.put(id);if(i<round.optInt("index")){index++;JSONObject event=results==null?null:results.optJSONObject(i);if(event!=null){event.put("id",id);revisedResults.put(event);}}}}
   round.put("ids",revised).put("index",index).put("results",revisedResults);
  }
  return true;
 }
}
