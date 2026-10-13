package org.example.mongoldictionary;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** UTF-8 CSV packages. A blank cell preserves an existing value; IDs never derive from edited text. */
public final class DictionaryStore {
    static final String[] COLUMNS={"id","mongolian","transliteration","definition","source_page","pronunciation","pronunciation_extra","audio_file","audio_source","family_id","saved_page","references","audio_urls"};
    private final Context context;
    private final AtomicFile database;
    public DictionaryStore(Context c) { context=c; database=new AtomicFile(new File(c.getFilesDir(),"dictionary-v1.json")); }
    static byte[] read(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
        while((n=in.read(b))!=-1) { if(out.size()+n>limit) throw new IOException("文件过大");out.write(b,0,n); } return out.toByteArray();
    }
    public List<JSONObject> load() throws Exception {
        InputStream source;
        try { source=database.openRead(); } catch(FileNotFoundException e) { source=context.getAssets().open("dictionary.json"); }
        try(android.util.JsonReader reader=new android.util.JsonReader(new InputStreamReader(source,StandardCharsets.UTF_8))) {
            List<JSONObject> rows=new ArrayList<>();reader.beginArray();
            while(reader.hasNext())rows.add((JSONObject)readValue(reader));
            reader.endArray();
            if(reader.peek()!=android.util.JsonToken.END_DOCUMENT)throw new IOException("Trailing dictionary data");
            return rows;
        }
    }
    private static Object readValue(android.util.JsonReader reader)throws Exception {
        switch(reader.peek()) {
            case BEGIN_OBJECT:
                JSONObject object=new JSONObject();reader.beginObject();
                while(reader.hasNext()){String key=reader.nextName();object.put(key,readValue(reader));}
                reader.endObject();return object;
            case BEGIN_ARRAY:
                JSONArray array=new JSONArray();reader.beginArray();
                while(reader.hasNext())array.put(readValue(reader));reader.endArray();return array;
            case STRING:return reader.nextString();
            case BOOLEAN:return reader.nextBoolean();
            case NULL:reader.nextNull();return JSONObject.NULL;
            case NUMBER:return new JSONTokener(reader.nextString()).nextValue();
            default:throw new IOException("Invalid dictionary JSON");
        }
    }
    public static class Plan {
        final List<JSONObject> rows=new ArrayList<>(); final Map<String,byte[]> audio=new LinkedHashMap<>();
        public int added,updated,unchanged,audioAdded,possibleDuplicates;
        final StringBuilder changes=new StringBuilder();
        public String summary(){return "新增 "+added+" 条\n更新 "+updated+" 条\n未变化 "+unchanged+" 条\n附带语音 "+audioAdded+" 个\n疑似同词不同 ID："+possibleDuplicates+" 条\n"+changes+"\n相同 ID 更新已有词条；空白单元格保留旧内容。收藏不受影响。";}
    }
    public Plan preview(InputStream source,List<JSONObject> current) throws Exception {
        Map<String,byte[]> files=new LinkedHashMap<>();int total=0;
        try(ZipInputStream zip=new ZipInputStream(source,StandardCharsets.UTF_8)) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName(); if(entry.isDirectory())continue;
                if(name.startsWith("/")||name.contains("\\")||Arrays.asList(name.split("/")).contains(".."))throw new IOException("不安全的文件路径");
                if(files.size()>=300 || files.containsKey(name))throw new IOException("文件数量过多或文件名重复");
                byte[] bytes=read(zip,20*1024*1024);total+=bytes.length;
                if(total>64*1024*1024)throw new IOException("导入包解压后超过 64 MB，请分批导入");files.put(name,bytes);
            }
        }
        if(!files.containsKey("entries.csv"))throw new IOException("ZIP 根目录缺少 entries.csv");
        String csv=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(files.get("entries.csv"))).toString();
        List<List<String>> table=parseCsv(csv);if(table.size()<2)throw new IOException("词条表为空");
        List<String> header=table.get(0);if(new HashSet<>(header).size()!=header.size())throw new IOException("表头重复");
        for(String name:header)if(!Arrays.asList(COLUMNS).contains(name))throw new IOException("未知列："+name);
        if(!header.contains("id"))throw new IOException("缺少 id 列");
        LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
        for(JSONObject row:current)merged.put(row.getString("id"),row);
        Plan plan=new Plan();Set<String> seen=new HashSet<>();
        for(int r=1;r<table.size();r++) {
            List<String> values=table.get(r);if(values.size()==1&&values.get(0).isEmpty())continue;
            if(values.size()!=header.size())throw new IOException("第 "+(r+1)+" 行列数不正确");
            String id=values.get(header.indexOf("id"));
            if(!id.matches("[A-Za-z0-9_-]{1,100}")||!seen.add(id))throw new IOException("第 "+(r+1)+" 行 ID 无效或重复");
            JSONObject old=merged.get(id), row=old==null?new JSONObject():new JSONObject(old.toString());row.put("id",id);
            for(int c=0;c<header.size();c++) {
                String name=header.get(c),value=values.get(c);if(value.isEmpty()||name.equals("id"))continue;
                if(value.length()>100000)throw new IOException("单元格过长");
                if(name.equals("audio_file")) {
                    if(!value.matches("audio/[A-Za-z0-9_.-]+\\.(mp3|wav|m4a|ogg)"))throw new IOException("语音路径须为 audio/文件名.mp3、wav、m4a 或 ogg");
                    byte[] bytes=files.get(value);if(bytes==null||bytes.length==0)throw new IOException("缺少语音文件："+value);
                    String local=hex(MessageDigest.getInstance("SHA-256").digest(bytes))+value.substring(value.lastIndexOf('.'));
                    plan.audio.put(local,bytes);row.put("audio_file",local);
                } else if(name.equals("references")||name.equals("audio_urls"))row.put(name,new JSONArray(value));
                else row.put(name,value);
            }
            if(row.optString("mongolian").trim().isEmpty()||row.optString("transliteration").trim().isEmpty()||row.optString("definition").trim().isEmpty())throw new IOException("第 "+(r+1)+" 行缺少蒙古文、转写或释义");
            if(old==null) {
                plan.added++;
                for(JSONObject existing:merged.values())if(existing.optString("mongolian").equals(row.optString("mongolian"))&&existing.optString("transliteration").equals(row.optString("transliteration"))){plan.possibleDuplicates++;break;}
            } else if(same(old,row))plan.unchanged++;else plan.updated++;
            if((old==null||!same(old,row))&&plan.added+plan.updated<=8)plan.changes.append("\n").append(old==null?"新增：":"更新：").append(row.optString("transliteration")).append(" · ").append(id);
            merged.put(id,row);
        }
        plan.rows.addAll(merged.values());plan.audioAdded=plan.audio.size();return plan;
    }
    static boolean same(JSONObject a,JSONObject b) {return a.toString().equals(b.toString());}
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private Set<String> bundledAudio;
    public boolean hasAudio(JSONObject row) {
        String name=row.optString("audio_file");
        if(!name.matches("[a-f0-9]{64}\\.(mp3|wav|m4a|ogg)"))return false;
        if(new File(context.getFilesDir(),"audio/"+name).isFile())return true;
        if(bundledAudio==null) {
            try {bundledAudio=new HashSet<>(Arrays.asList(context.getAssets().list("audio")));}
            catch(IOException e){bundledAudio=Collections.emptySet();}
        }
        return bundledAudio.contains(name);
    }
    public File audio(JSONObject row) {
        if(!hasAudio(row))return null;
        String name=row.optString("audio_file");File target=new File(context.getFilesDir(),"audio/"+name);
        if(target.isFile())return target;
        // Materialize only the requested bundled recording, so startup stays lightweight.
        File dir=target.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())return null;
        AtomicFile file=new AtomicFile(target);FileOutputStream out=null;
        try(InputStream in=context.getAssets().open("audio/"+name)) {
            out=file.startWrite();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            file.finishWrite(out);return target;
        } catch(IOException e){if(out!=null)file.failWrite(out);return null;}
    }
    public void commit(Plan plan) throws Exception {
        File dir=new File(context.getFilesDir(),"audio");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法保存语音");
        for(Map.Entry<String,byte[]> item:plan.audio.entrySet()) {
            AtomicFile file=new AtomicFile(new File(dir,item.getKey()));FileOutputStream out=null;
            try {out=file.startWrite();out.write(item.getValue());file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        }
        FileOutputStream out=null;
        try {
            out=database.startWrite();
            Writer writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
            writer.write('[');boolean first=true;
            for(JSONObject row:plan.rows){if(!first)writer.write(',');writer.write(row.toString());first=false;}
            writer.write(']');writer.flush();database.finishWrite(out);
        }
        catch(Exception e){if(out!=null)database.failWrite(out);throw e;}
    }
    public void export(OutputStream target,List<JSONObject> rows) throws Exception {
        try(ZipOutputStream zip=new ZipOutputStream(target,StandardCharsets.UTF_8)) {
            StringBuilder csv=new StringBuilder("\ufeff");appendRow(csv,Arrays.asList(COLUMNS));Set<String> audio=new HashSet<>();
            for(JSONObject row:rows) {
                List<String> values=new ArrayList<>();
                for(String name:COLUMNS){String value=row.optString(name,"");if(name.equals("audio_file")&&!value.isEmpty()){File file=audio(row);if(file==null||!file.isFile())throw new IOException("词条语音文件缺失");audio.add(value);value="audio/"+value;}values.add(value);}appendRow(csv,values);
            }
            zip.putNextEntry(new ZipEntry("study.json"));zip.write(new StudyStore(context).snapshot().toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("entries.csv"));zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            for(String name:audio){zip.putNextEntry(new ZipEntry("audio/"+name));try(InputStream in=new FileInputStream(new File(context.getFilesDir(),"audio/"+name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();}
        }
    }
    static void appendRow(StringBuilder out,List<String> fields){for(int i=0;i<fields.size();i++){if(i>0)out.append(',');out.append('"').append(fields.get(i).replace("\"","\"\"")).append('"');}out.append("\r\n");}
    static List<List<String>> parseCsv(String text) throws IOException {
        if(text.startsWith("\ufeff"))text=text.substring(1);List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(c);continue;}
            if(c==','||c=='\n'||c=='\r'){row.add(cell.toString());cell.setLength(0);closed=false;if(c!=','){rows.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;}continue;}
            if(closed)throw new IOException("CSV 引号后存在多余字符");
            if(c=='"'){if(cell.length()!=0)throw new IOException("CSV 引号格式错误");quoted=true;}else cell.append(c);
        }
        if(quoted)throw new IOException("CSV 引号未闭合");if(cell.length()>0||closed||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
    }
}
