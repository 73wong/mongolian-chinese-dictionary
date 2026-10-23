package org.example.mongoldictionary;
import org.json.*;
import java.util.*;
final class EntryAliasesChecks {
 static void run()throws Exception{
  JSONObject old=new JSONObject().put("id",EntryAliases.OLD),keep=new JSONObject().put("id",EntryAliases.KEEP).put("mongolian","ᠭᠣᠷᠢᠶ᠎ᠠ(᠊ᠨ)").put("audio_file","original.mp3");
  List<JSONObject> rows=new ArrayList<>(Arrays.asList(old,keep));EntryAliases.apply(rows);EntryAliases.apply(rows);
  if(rows.size()!=1||rows.get(0)!=keep)throw new AssertionError("Wrong duplicate retained");
  JSONObject data=new JSONObject().put("cards",new JSONObject().put(EntryAliases.OLD,new JSONObject().put("last",200).put("seen",3).put("lapses",1)).put(EntryAliases.KEEP,new JSONObject().put("last",100).put("seen",2).put("lapses",0)))
   .put("history",new JSONArray().put(new JSONObject().put("id",EntryAliases.OLD)))
   .put("round",new JSONObject().put("ids",new JSONArray().put(EntryAliases.OLD).put(EntryAliases.KEEP).put("other")).put("index",2).put("results",new JSONArray().put(new JSONObject().put("id",EntryAliases.OLD)).put(new JSONObject().put("id",EntryAliases.KEEP))));
  if(!EntryAliases.migrate(data)||EntryAliases.migrate(data))throw new AssertionError("Migration not idempotent");
  JSONObject card=data.getJSONObject("cards").getJSONObject(EntryAliases.KEEP);
  if(card.getLong("last")!=200||card.getInt("seen")!=5||data.getJSONObject("cards").has(EntryAliases.OLD))throw new AssertionError("Progress lost");
  if(!EntryAliases.KEEP.equals(data.getJSONArray("history").getJSONObject(0).getString("id"))||data.getJSONObject("round").getJSONArray("ids").length()!=2||data.getJSONObject("round").getInt("index")!=1||data.getJSONObject("round").getJSONArray("results").length()!=1)throw new AssertionError("History or round migration failed");
 }
}
