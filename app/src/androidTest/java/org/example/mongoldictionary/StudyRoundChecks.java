package org.example.mongoldictionary;
import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;
public final class StudyRoundChecks {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void run(Context context)throws Exception{
  File dir=new File(context.getCacheDir(),"round-check-"+UUID.randomUUID());dir.mkdirs();
  Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return dir;}};
  try{
   StudyStore s=new StudyStore(isolated);Set<String> ids=new LinkedHashSet<>();for(int i=0;i<15;i++)ids.add("word"+i);
   long now=System.currentTimeMillis();s.add(ids);s.settings(15,false);s.startRound(ids,now);
   JSONObject round=s.round(ids,now);check(round.getJSONArray("ids").length()==10,"bounded round");String first=round.getJSONArray("ids").getString(0);
   JSONObject before=s.snapshot();s.defer(first,now);check(s.round(ids,now).getInt("index")==1,"defer advances round");
   check(!s.queue(ids,now).contains(first),"deferred new word excluded");check(s.introducedToday(now)==0&&s.calendar().isEmpty(),"defer does not create learning history");
   check(new StudyStore(isolated).round(ids,now).getInt("index")==1,"round survives process restart");
   check(s.queue(ids,now+StudyStore.DAY).contains(first),"deferred word returns next day");s.restore(before,ids);check(s.round(ids,now).getInt("index")==0&&s.queue(ids,now).contains(first),"undo defer restores queue and round");
   for(int i=0;i<10;i++){String id=s.round(ids,now).getJSONArray("ids").getString(i);s.rate(id,2,now,false);}
   check(s.round(ids,now).getInt("index")==10,"round completes");check(s.calendar().get(StudyStore.date(now)).words.size()==10,"calendar counts real ratings");
   check(s.round(ids,now+StudyStore.DAY)==null,"old round expires on new day");
   s.startRound(ids,now+StudyStore.DAY);String review=s.round(ids,now+StudyStore.DAY).getJSONArray("ids").getString(0);int seen=s.snapshot().getJSONObject("cards").getJSONObject(review).getInt("seen");s.defer(review,now+StudyStore.DAY);check(s.snapshot().getJSONObject("cards").getJSONObject(review).getInt("seen")==seen,"deferring review preserves seen count");
  }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
 }
}
