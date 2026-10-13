package org.example.mongoldictionary;
import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class StorageScaleChecks {
 public static String run(Context app)throws Exception {
  File dir=new File(app.getCacheDir(),"storage-scale-"+UUID.randomUUID());if(!dir.mkdirs())throw new IOException("test directory");
  Context isolated=new ContextWrapper(app){@Override public File getFilesDir(){return dir;}};
  int count=70000;String definition="释义：ᠬᠡᠷᠡᠭ\u202fᠢᠶᠡᠨ；例句\n"+String.join("",Collections.nCopies(100,"测试"));
  try {
   File file=new File(dir,"dictionary-v1.json");
   try(Writer writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8))){
    writer.write('[');
    for(int i=0;i<count;i++){
     JSONObject row=new JSONObject().put("id","scale-"+i).put("mongolian","ᠬᠡᠷᠡᠭ\u202fᠢᠶᠡᠨ").put("transliteration","kereg-"+i).put("definition",definition).put("source_page","622b").put("pronunciation","kʰereg").put("pronunciation_extra","хэрэг").put("audio_urls",new JSONArray().put("http://example.test/audio/"+i)).put("references",new JSONArray()).put("nullable",JSONObject.NULL).put("number",i).put("flag",true);
     if(i>0)writer.write(',');writer.write(row.toString());
    }writer.write(']');
   }
   long bytes=file.length();ImportChecks.require(bytes>20*1024*1024,"fixture must cross old size limit");
   DictionaryStore store=new DictionaryStore(isolated);List<JSONObject> rows=store.load();
   ImportChecks.require(rows.size()==count,"large load count");
   ImportChecks.require(rows.get(count-1).getInt("number")==count-1&&rows.get(0).isNull("nullable"),"JSON types preserved");
   for(int round=0;round<3;round++){
    DictionaryStore.Plan plan=ImportChecks.preview(store,rows,"id,definition\nscale-0,updated-"+round+"\n");
    ImportChecks.require(plan.updated==1&&rows.get(0).getString("definition").equals(round==0?definition:"updated-"+(round-1)),"preview must not mutate original");
    ImportChecks.require(plan.rows.get(1)==rows.get(1),"unchanged row should not be cloned");
    store.commit(plan);rows=plan.rows;
   }
   rows=null;System.gc();
   List<JSONObject> reread=store.load();ImportChecks.require(reread.size()==count&&reread.get(0).getString("definition").equals("updated-2"),"large commit reload");
   ImportChecks.require(reread.get(count-1).getString("definition").equals(definition),"Unicode roundtrip");
   return "70000 rows, "+bytes+" bytes, 3 commits, Unicode preserved, max heap="+Runtime.getRuntime().maxMemory();
  }finally{ImportChecks.delete(dir);}
 }
}
