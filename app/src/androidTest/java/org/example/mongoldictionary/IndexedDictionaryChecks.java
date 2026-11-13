package org.example.mongoldictionary;
import android.content.Context;
import android.os.SystemClock;
import java.util.*;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Compare compiled data and indexed search against the unchanged legacy rules. */
final class IndexedDictionaryChecks {
 static String run(Context context)throws Exception{
  long start=SystemClock.elapsedRealtime();IndexedDictionary indexed=IndexedDictionary.open(context);long open=SystemClock.elapsedRealtime()-start;
  Method m=DictionaryStore.class.getDeclaredMethod("loadUncached");m.setAccessible(true);
  start=SystemClock.elapsedRealtime();List<JSONObject> legacy=(List<JSONObject>)m.invoke(new DictionaryStore(context));long legacyTime=SystemClock.elapsedRealtime()-start;
  if(indexed.size()!=legacy.size())throw new AssertionError("Count mismatch "+indexed.size()+" / "+legacy.size());
  for(int n=0;n<legacy.size();n++){
   JSONObject a=legacy.get(n),b=indexed.get(n);if(a.length()!=b.length())throw new AssertionError("Column mismatch "+a.optString("id"));
   for(Iterator<String> keys=a.keys();keys.hasNext();){String k=keys.next();if(!a.optString(k).equals(b.optString(k)))throw new AssertionError("Data mismatch "+a.optString("id")+" "+k);}
  }
  String[] queries={"kereg","o","U","马","хэрэг","ᠮᠣ","%","_","a","mend","mand","Goriy_a(n)"};
  long sqlMs=0,scanMs=0;int cases=0;
  for(int n=0;n<queries.length;n++)for(int variant=0;variant<2;variant++){
   String query=queries[n];int scope=n%5,mode=n%4;boolean folded=variant==1,fuzzy=n%2==0;
   start=SystemClock.elapsedRealtime();List<JSONObject> actual=indexed.search(query,scope,mode,folded,fuzzy,Collections.emptySet(),false,()->false);sqlMs+=SystemClock.elapsedRealtime()-start;
   List<String> expected=new ArrayList<>();start=SystemClock.elapsedRealtime();for(JSONObject row:legacy)if(SearchRules.matches(row,query,scope,mode,folded,fuzzy))expected.add(row.optString("id"));scanMs+=SystemClock.elapsedRealtime()-start;
   List<String> ids=new ArrayList<>();for(JSONObject row:actual)ids.add(row.optString("id"));if(!ids.equals(expected))throw new AssertionError("Search mismatch "+query+" scope="+scope+" mode="+mode+" expected="+expected.size()+" actual="+ids.size());cases++;
  }
  Map<String,JSONObject> map=indexed.byId();if(map.size()!=legacy.size()||!map.get(legacy.get(0).getString("id")).optString("id").equals(legacy.get(0).getString("id")))throw new AssertionError("Lazy study map mismatch");
  java.io.File dir=new java.io.File(context.getCacheDir(),"indexed-import-"+UUID.randomUUID());dir.mkdirs();
  Context isolated=new android.content.ContextWrapper(context){@Override public java.io.File getFilesDir(){return dir;}};
  try {
   DictionaryStore store=new DictionaryStore(isolated);DictionaryStore.Plan plan=new DictionaryStore.Plan();
   JSONObject sample=new JSONObject(legacy.get(0).toString());sample.remove("audio_file");sample.put("id","indexed-import-sample");sample.put("definition","import-old");plan.rows.add(sample);store.commit(plan);
   List<JSONObject> first=store.load();if(!(first instanceof IndexedDictionary))throw new AssertionError("Import not indexed");
   List<JSONObject> hits=((IndexedDictionary)first).search("import-old",4,1,false,false,Collections.emptySet(),false,()->false);if(hits.size()!=1)throw new AssertionError("Imported search missing");
   sample.put("definition","import-new");store.commit(plan);List<JSONObject> second=store.load();
   if(((IndexedDictionary)second).search("import-new",4,1,false,false,Collections.emptySet(),false,()->false).size()!=1||!((IndexedDictionary)second).search("import-old",4,1,false,false,Collections.emptySet(),false,()->false).isEmpty())throw new AssertionError("Stale import index");
   String csv="id,definition\nindexed-import-sample,import-csv\n";
   DictionaryStore.Plan preview=ImportChecks.preview(store,second,csv);if(preview.updated!=1)throw new AssertionError("CSV preview failed");store.commit(preview);
   if(((IndexedDictionary)store.load()).search("import-csv",4,1,false,false,Collections.emptySet(),false,()->false).size()!=1)throw new AssertionError("CSV index refresh failed");
  }finally{ImportChecks.delete(dir);}
  return "PASS: "+legacy.size()+" complete rows identical; "+cases+" search comparisons; lazy study map and imported index invalidation/CSV update. db_open_ms="+open+" legacy_load_ms="+legacyTime+" indexed_queries_ms="+sqlMs+" legacy_queries_ms="+scanMs;
 }
}
