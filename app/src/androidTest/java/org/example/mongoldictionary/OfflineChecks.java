package org.example.mongoldictionary;
import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;
public final class OfflineChecks {
 public static String run(Context context)throws Exception {
  File dir=new File(context.getCacheDir(),"offline-validation");ImportChecks.delete(dir);dir.mkdirs();
  Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return dir;}};
  try {
   DictionaryStore store=new DictionaryStore(isolated);List<JSONObject> rows=store.load();
   ImportChecks.require(rows.size()==53412,"bundled dictionary count");
   int mand=0,mend=0,audio=0;JSONObject sample=null;
   for(JSONObject row:rows){
    if(SearchRules.matches(row,"mand",2,0,false,false))mand++;
    if(SearchRules.matches(row,"mend",2,0,false,false))mend++;
    if(!row.optString("audio_file").isEmpty()){ImportChecks.require(store.hasAudio(row),"missing bundled recording");audio++;if(sample==null)sample=row;}
   }
   ImportChecks.require(mand==14&&mend==11,"prefix search missing entries");
   ImportChecks.require(!new File(dir,"audio").exists(),"audio inventory must not copy files");
   File recording=store.audio(sample);ImportChecks.require(recording!=null&&recording.length()>0,"offline audio extraction");
   android.media.MediaPlayer player=new android.media.MediaPlayer();
   try{player.setDataSource(recording.getAbsolutePath());player.prepare();ImportChecks.require(player.getDuration()>0,"offline audio decode");}finally{player.release();}
   return rows.size()+" entries; mand="+mand+", mend="+mend+"; "+audio+" bundled audio entries; offline playback decode passed";
  }finally{ImportChecks.delete(dir);}
 }
}
