package org.example.mongoldictionary;

import android.content.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

public class StudyChecks {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void run(Context context)throws Exception {
        File dir=new File(context.getCacheDir(),"study-check-"+UUID.randomUUID());check(dir.mkdirs(),"test directory");
        Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return dir;}};
        try {
            StudyStore study=new StudyStore(isolated);
            long now=LocalDate.of(2026,10,8).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            LinkedHashSet<String> ids=new LinkedHashSet<>(Arrays.asList("a","b","c","d","e","f"));study.add(ids);study.add(ids);
            check(study.snapshot().getJSONObject("cards").length()==6,"idempotent enrollment");
            check(study.queue(ids,now).size()==5,"daily new limit");
            JSONObject before=study.snapshot();study.rate("a",0,now,false);
            check(!study.queue(ids,now+59999).contains("a"),"no immediate repeat");
            check(study.queue(ids,now+60000).get(0).equals("a"),"due reviews before fresh cards");
            study.restore(before,ids);check(study.introducedToday(now)==0,"undo restores daily allowance");
            for(String id:Arrays.asList("a","b","c","d","e"))study.rate(id,2,now,false);
            check(study.queue(ids,now).isEmpty(),"five new words exhaust allowance");
            check(study.queue(ids,now+StudyStore.DAY).contains("f"),"new day renews allowance");
            study.settings(5,true);check(!study.queue(ids,now+StudyStore.DAY).contains("f"),"pause new retains review");
            check(study.queue(ids,now+StudyStore.DAY).contains("a"),"old reviews still scheduled");
            study.pause("a",true);check(!study.queue(ids,now+StudyStore.DAY).contains("a"),"paused card excluded");
            study.pause("a",false);check(study.queue(ids,now+StudyStore.DAY).contains("a"),"resume retains due date");
            check(new StudyStore(isolated).snapshot().toString().equals(study.snapshot().toString()),"restart persistence");
            String unchanged=study.snapshot().toString();boolean blocked=false;try{study.rate("b",3,now,true);}catch(Exception e){blocked=true;}
            check(blocked&&unchanged.equals(study.snapshot().toString()),"hint cannot get high rating");
            JSONObject calendarBefore=study.snapshot();
            study.rate("b",2,now+60000,false);
            StudyStore.DayStats today=study.calendar().get(StudyStore.date(now));
            check(today.words.size()==5&&today.repetitions==6&&today.newWords.size()==5&&today.reviewWords.size()==1,"daily unique words vs repetitions");
            study.rate("b",2,now+StudyStore.DAY,false);
            StudyStore.DayStats tomorrow=study.calendar().get(StudyStore.date(now+StudyStore.DAY));
            check(tomorrow.words.size()==1&&tomorrow.newWords.isEmpty()&&tomorrow.reviewWords.size()==1,"review day classification");
            study.restore(calendarBefore,ids);check(study.calendar().get(StudyStore.date(now)).repetitions==5,"undo removes calendar event");
            DictionaryStore dictionary=new DictionaryStore(isolated);List<JSONObject> entries=dictionary.load();
            // Use real permanent IDs for a complete dictionary + study backup round trip.
            List<String> kereg=new ArrayList<>();String keregId="",phraseId="",keregeId="";
            for(JSONObject row:entries){String word=row.getString("transliteration");if(word.equals("kereg"))keregId=row.getString("id");if(word.equals("kereg e kereg"))phraseId=row.getString("id");if(word.equals("kerege"))keregeId=row.getString("id");}
            kereg=WordFamilies.expand(Collections.singleton(keregId),entries);
            check(kereg.contains(phraseId)&&!kereg.contains(keregeId),"whole headword grouping, no prefix collision");
            List<String> random=WordFamilies.random(entries,Collections.emptySet(),3,new Random(42));
            check(new HashSet<>(random).size()==random.size(),"random groups no duplicates");
            check(new HashSet<>(WordFamilies.expand(random,entries)).equals(new HashSet<>(random)),"random includes full families");
            check(WordFamilies.random(entries,new HashSet<>(random),3,new Random(42)).stream().noneMatch(random::contains),"random skips fully enrolled groups");
            JSONObject customA=new JSONObject().put("id","x").put("transliteration","alpha").put("family_id","custom");
            JSONObject customB=new JSONObject().put("id","y").put("transliteration","beta phrase").put("family_id","custom");
            check(WordFamilies.expand(Collections.singleton("x"),Arrays.asList(customA,customB)).size()==2,"explicit family override");
            Set<String> realIds=new HashSet<>();for(JSONObject e:entries)realIds.add(e.getString("id"));
            JSONObject fresh=new JSONObject().put("version",1).put("limit",7).put("pauseNew",false).put("cards",new JSONObject());study.restore(fresh,realIds);
            String id=entries.get(0).getString("id");study.add(Collections.singleton(id));study.rate(id,2,now,false);
            ByteArrayOutputStream zip=new ByteArrayOutputStream();dictionary.export(zip,entries);
            JSONObject backup=StudyStore.readBackup(new ByteArrayInputStream(zip.toByteArray()));
            check(backup.getJSONObject("cards").has(id)&&backup.getJSONArray("history").length()==1,"ZIP contains state and review history");
            study.restore(fresh,realIds);study.restore(backup,realIds);check(study.limit()==7&&study.contains(id),"restore settings and schedule");
            JSONObject edited=new JSONObject(entries.get(0).toString());edited.put("definition","edited definition");check(study.contains(edited.getString("id")),"text update retains progress");
            String saved=study.snapshot().toString();blocked=false;try{study.restore(backup,Collections.emptySet());}catch(Exception e){blocked=true;}
            check(blocked&&saved.equals(study.snapshot().toString()),"unknown IDs cannot replace progress");
            blocked=false;try{StudyStore.parse("{\"version\":999}");}catch(Exception e){blocked=true;}check(blocked,"unknown version rejected");
        } finally {ImportChecks.delete(dir);}
    }
}
