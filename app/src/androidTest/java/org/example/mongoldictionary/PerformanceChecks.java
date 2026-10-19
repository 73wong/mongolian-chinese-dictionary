package org.example.mongoldictionary;
import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.List;
public final class PerformanceChecks {
 public static String run(Context context)throws Exception {
  DictionaryStore store=new DictionaryStore(context);
  long start=SystemClock.elapsedRealtime();List<JSONObject> first=store.load();long cold=SystemClock.elapsedRealtime()-start;
  start=SystemClock.elapsedRealtime();List<JSONObject> second=new DictionaryStore(context).load();long warm=SystemClock.elapsedRealtime()-start;
  if(first.size()!=second.size()||first.isEmpty()||first.get(0)!=second.get(0))throw new AssertionError("Shared snapshot not reused");
  second.clear();if(store.load().size()!=first.size())throw new AssertionError("Caller changed cached list");
  JSONObject reference=null;int expanded=0;
  for(JSONObject row:first){if(row.has("resolved_definition"))expanded++;if(row.optString("id").equals("entry-1b99bb94-7b3d-526b-81b2-9657f0014505"))reference=row;}
  if(reference==null||!ReferenceDefinitions.text(reference).contains("寺院外围")||!reference.optString("definition").startsWith("⇒")||reference.has("audio_file"))throw new AssertionError("Reference overlay/original/audio");
  try(java.io.InputStream in=context.getAssets().open("definition-corrections.json")){
   org.json.JSONArray approved=new org.json.JSONArray(new String(DictionaryStore.read(in,1024*1024),java.nio.charset.StandardCharsets.UTF_8));
   java.util.Map<String,JSONObject> ids=new java.util.HashMap<>();for(JSONObject row:first)if(ids.put(row.optString("id"),row)!=null)throw new AssertionError("Duplicate ID");
   if(approved.length()!=77)throw new AssertionError("Review count");
   for(int pass=0;pass<2;pass++){
    for(int i=0;i<approved.length();i++){JSONObject c=approved.getJSONObject(i),row=ids.get(c.getString("id"));if(row==null||!ReferenceDefinitions.text(row).equals(c.getString("definition")))throw new AssertionError("Review failed: "+c.optInt("review_number"));}
    ReferenceDefinitions.apply(context,first);
   }
   if(!ids.get("entry-e735e141-9b6e-5a62-af6e-c14e91cee321").optString("transliteration").equals("tentUr tentUr"))throw new AssertionError("Headword correction");
  }
  SearchChecks.run();
  start=SystemClock.elapsedRealtime();int matches=0;for(JSONObject row:first)if(SearchRules.matches(row,"kereg",2,0,false,true))matches++;long search=SystemClock.elapsedRealtime()-start;
  if(matches==0)throw new AssertionError("Missing search results");
  java.io.File dir=new java.io.File(context.getCacheDir(),"perf-"+java.util.UUID.randomUUID());dir.mkdirs();
  Context isolated=new android.content.ContextWrapper(context){@Override public java.io.File getFilesDir(){return dir;}};
  try {
   DictionaryStore local=new DictionaryStore(isolated);DictionaryStore.Plan plan=new DictionaryStore.Plan();
   plan.rows.add(new JSONObject("{\"id\":\"perf\",\"definition\":\"old\"}"));local.commit(plan);
   if(!local.load().get(0).getString("definition").equals("old"))throw new AssertionError("Initial import");
   DictionaryStore.Plan update=new DictionaryStore.Plan();update.rows.add(new JSONObject("{\"id\":\"perf\",\"definition\":\"new\"}"));local.commit(update);
   if(!new DictionaryStore(isolated).load().get(0).getString("definition").equals("new"))throw new AssertionError("Stale import cache");
  }finally {for(java.io.File f:dir.listFiles())f.delete();dir.delete();}
  return "reference overlays="+expanded+"; cache invalidation passed; rows="+first.size()+", initial_load_ms="+cold+", shared_load_ms="+warm+", search_ms="+search+", matches="+matches;
 }
}
