package org.example.mongoldictionary;

import android.content.Context;
import android.content.ContextWrapper;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class ImportChecks {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static byte[] zip(String csv, String path,byte[] audio) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("entries.csv"));zip.write(csv.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            if(path!=null){zip.putNextEntry(new ZipEntry(path));zip.write(audio);zip.closeEntry();}
        }return bytes.toByteArray();
    }
    static DictionaryStore.Plan preview(DictionaryStore store,List<JSONObject> rows,String csv) throws Exception {return store.preview(new ByteArrayInputStream(zip(csv,null,null)),rows);}
    static void reject(DictionaryStore store,List<JSONObject> rows,byte[] bytes) throws Exception {
        boolean failed=false;try{store.preview(new ByteArrayInputStream(bytes),rows);}catch(Exception expected){failed=true;}require(failed,"invalid package accepted");
    }
    public static void run(Context app) throws Exception {
        File directory=new File(app.getCacheDir(),"import-check-"+UUID.randomUUID());require(directory.mkdirs(),"test directory");
        Context isolated=new ContextWrapper(app){@Override public File getFilesDir(){return directory;}};
        try {
            DictionaryStore store=new DictionaryStore(isolated);List<JSONObject> original=store.load();require(original.size()==75,"asset count");
            Set<String> ids=new HashSet<>();for(JSONObject row:original)require(ids.add(row.getString("id")),"unique permanent IDs");
            ByteArrayOutputStream output=new ByteArrayOutputStream();store.export(output,original);
            DictionaryStore.Plan roundtrip=store.preview(new ByteArrayInputStream(output.toByteArray()),original);
            require(roundtrip.added==0&&roundtrip.updated==0&&roundtrip.unchanged==75,"export/import identity");
            JSONObject target=original.get(0);String id=target.getString("id");
            String definition="测试逗号,引号\"与换行\nᠳ\u180bᠡ ᠷ\u180eᠡ\u202fᠪᠡᠨ";
            StringBuilder csv=new StringBuilder();DictionaryStore.appendRow(csv,Arrays.asList("id","definition"));DictionaryStore.appendRow(csv,Arrays.asList(id,definition));
            DictionaryStore.Plan change=preview(store,original,csv.toString());require(change.updated==1,"update count");
            require(change.rows.get(0).getString("mongolian").equals(target.getString("mongolian")),"partial update retains source");
            require(change.rows.get(0).getString("id").equals(id),"update keeps ID");
            store.commit(change);require(new DictionaryStore(isolated).load().get(0).getString("definition").equals(definition),"durable Unicode/multiline update");
            DictionaryStore.Plan blank=preview(store,change.rows,"id,definition\n"+id+",\n");require(blank.unchanged==1,"blank preserves value");
            reject(store,original,zip("id,definition\n"+id+",x\n"+id+",y\n",null,null));
            reject(store,original,zip("id,mongolian\nnew,abc\n",null,null));
            reject(store,original,zip("id,audio_file\n"+id+",audio/missing.mp3\n",null,null));
            reject(store,original,zip("id,definition\n"+id+",x\n","../escape.mp3",new byte[]{1}));
            require(store.load().get(0).getString("definition").equals(definition),"rejected imports leave stored data unchanged");
            StringBuilder additions=new StringBuilder();DictionaryStore.appendRow(additions,Arrays.asList("id","mongolian","transliteration","definition"));
            DictionaryStore.appendRow(additions,Arrays.asList("test-new",target.getString("mongolian"),"import test","仅测试新增记录"));
            DictionaryStore.Plan add=preview(store,original,additions.toString());require(add.added==1&&add.rows.size()==76,"new entry");
            byte[] sound=new byte[]{82,73,70,70};
            DictionaryStore.Plan audio=store.preview(new ByteArrayInputStream(zip("id,audio_file,audio_source\n"+id+",audio/test.wav,test\n","audio/test.wav",sound)),original);
            require(audio.updated==1&&audio.audioAdded==1,"audio only update");store.commit(audio);
            require(Arrays.equals(sound,java.nio.file.Files.readAllBytes(store.audio(store.load().get(0)).toPath())),"audio persistence");
            ByteArrayOutputStream backed=new ByteArrayOutputStream();store.export(backed,store.load());
            DictionaryStore.Plan restore=store.preview(new ByteArrayInputStream(backed.toByteArray()),store.load());require(restore.updated==0&&restore.unchanged==75,"audio round trip");
        } finally {delete(directory);}
    }
    public static void checkDownloadedAudio(Context app, Context tests) throws Exception {
        File dir=new File(app.getCacheDir(),"audio-check-"+UUID.randomUUID());require(dir.mkdirs(),"audio test directory");
        Context isolated=new ContextWrapper(app){@Override public File getFilesDir(){return dir;}};
        try {
            DictionaryStore store=new DictionaryStore(isolated);
            DictionaryStore.Plan plan;
            try(InputStream in=tests.getAssets().open("sample-audio.zip")){plan=store.preview(in,store.load());}
            require(plan.updated==21&&plan.added==0&&plan.audioAdded==21,"21 recordings mapped to existing IDs");
            store.commit(plan);int count=0;
            for(JSONObject row:store.load()) {
                File file=store.audio(row);if(file==null)continue;
                android.media.MediaPlayer player=new android.media.MediaPlayer();
                try {player.setDataSource(file.getAbsolutePath());player.prepare();require(player.getDuration()>0,"empty audio: "+row.optString("transliteration"));count++;}
                finally {player.release();}
            }
            require(count==21,"all audio decoded");
        } finally {delete(dir);}
    }
    public static void checkExpansion(Context app, Context tests) throws Exception {
        File dir=new File(app.getCacheDir(),"expansion-check-"+UUID.randomUUID());require(dir.mkdirs(),"expansion directory");
        Context isolated=new ContextWrapper(app){@Override public File getFilesDir(){return dir;}};
        try {
            DictionaryStore store=new DictionaryStore(isolated);
            DictionaryStore.Plan plan;
            try(InputStream in=tests.getAssets().open("expansion-batch-01.zip")){plan=store.preview(in,store.load());}
            require(plan.added==286&&plan.updated==0&&plan.audioAdded==67,"expansion counts");
            store.commit(plan);List<JSONObject> rows=store.load();require(rows.size()==361,"persisted expanded dictionary");
            int audioCount=0,moriCount=0;String moriId=null;
            for(JSONObject row:rows){
                String word=row.optString("transliteration");
                if(word.equals("mori(n)"))moriId=row.getString("id");
                if(row.optString("family_id").equals("mori"))moriCount++;
                File file=store.audio(row);if(file==null)continue;
                android.media.MediaPlayer player=new android.media.MediaPlayer();
                try {player.setDataSource(file.getAbsolutePath());player.prepare();require(player.getDuration()>0,"empty expansion audio: "+word);audioCount++;}
                finally {player.release();}
            }
            require(audioCount==67,"67 expansion audio files decoded");
            require(moriId!=null&&moriCount>30,"optional-n group exists");
            require(WordFamilies.expand(Collections.singleton(moriId),rows).size()==moriCount,"optional-n phrases included");
            try(InputStream in=tests.getAssets().open("expansion-batch-01.zip")){
                DictionaryStore.Plan again=store.preview(in,rows);require(again.added==0&&again.updated==0&&again.unchanged==286,"repeat import idempotent");
            }
        } finally {delete(dir);}
    }
    static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
