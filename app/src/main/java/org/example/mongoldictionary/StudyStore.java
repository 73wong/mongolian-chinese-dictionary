package org.example.mongoldictionary;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Local reading-recall schedule. Ratings are independent of dictionary text and favorites. */
public final class StudyStore {
    private final AtomicFile file;
    private JSONObject data;
    static final long DAY=86400000L;
    public StudyStore(Context context) throws Exception {
        file=new AtomicFile(new File(context.getFilesDir(),"study-v1.json"));
        try(InputStream in=file.openRead()){data=parse(new String(DictionaryStore.read(in,10*1024*1024),StandardCharsets.UTF_8));}
        catch(FileNotFoundException e){data=new JSONObject().put("version",1).put("limit",5).put("pauseNew",false).put("cards",new JSONObject());}
    }
    static JSONObject parse(String json) throws Exception {
        JSONObject d=new JSONObject(json);
        if(d.getInt("version")!=1)throw new IOException("不支持的学习备份版本");
        int limit=d.getInt("limit");if(limit<1||limit>50)throw new IOException("每日新词数量无效");
        d.getBoolean("pauseNew");JSONObject cards=d.getJSONObject("cards");
        if(cards.length()>50000)throw new IOException("学习记录过多");
        for(Iterator<String> it=cards.keys();it.hasNext();) {
            String id=it.next();if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("学习记录 ID 无效");
            JSONObject c=cards.getJSONObject(id);c.getBoolean("paused");
            for(String key:new String[]{"due","last","seen","lapses","interval"})if(c.getLong(key)<0)throw new IOException("学习时间或次数无效");
            if(c.getLong("interval")>36500||c.getLong("due")>32503680000000L)throw new IOException("学习间隔超出范围");
            String date=c.optString("introduced","");if(!date.isEmpty())LocalDate.parse(date);
        }
        if(d.has("history")) {
            JSONArray history=d.getJSONArray("history");
            if(history.length()>100000)throw new IOException("评分历史过多");
            for(int i=0;i<history.length();i++) {
                JSONObject event=history.getJSONObject(i);String id=event.getString("id");long time=event.getLong("time");int rating=event.getInt("rating");
                if(!cards.has(id)||time<0||time>32503680000000L||rating<0||rating>3)throw new IOException("评分历史无效");
                if(!event.has("day"))event.put("day",date(time));
                LocalDate.parse(event.getString("day"));
            }
        }
        // Undo is session-local and must never come from external backups.
        d.remove("undo");return d;
    }
    private JSONObject cards(){return data.optJSONObject("cards");}
    public JSONObject snapshot() throws Exception {return new JSONObject(data.toString());}
    private void save(JSONObject next) throws Exception {
        FileOutputStream out=null;
        try {out=file.startWrite();out.write(next.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);data=next;}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    public boolean contains(String id){return cards().has(id);}
    public boolean paused(String id){JSONObject c=cards().optJSONObject(id);return c!=null&&c.optBoolean("paused");}
    public int limit(){return data.optInt("limit",5);}
    public boolean pauseNew(){return data.optBoolean("pauseNew");}
    public void settings(int limit,boolean pause) throws Exception {if(limit<1||limit>50)throw new IOException("新词数量须为 1—50");JSONObject n=snapshot();n.put("limit",limit).put("pauseNew",pause);save(n);}
    public void add(Collection<String> ids) throws Exception {
        JSONObject n=snapshot(),cs=n.getJSONObject("cards");
        for(String id:ids)if(!cs.has(id))cs.put(id,new JSONObject().put("paused",false).put("seen",0).put("due",0).put("last",0).put("interval",0).put("lapses",0).put("introduced",""));save(n);
    }
    public void pause(String id,boolean paused) throws Exception {JSONObject n=snapshot();n.getJSONObject("cards").getJSONObject(id).put("paused",paused);save(n);}
    static String date(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    public int introducedToday(long now){int count=0;for(Iterator<String> i=cards().keys();i.hasNext();)if(date(now).equals(cards().optJSONObject(i.next()).optString("introduced")))count++;return count;}
    public List<String> queue(Set<String> available,long now) {
        List<String> due=new ArrayList<>(),fresh=new ArrayList<>();
        for(Iterator<String> it=cards().keys();it.hasNext();) {String id=it.next();JSONObject c=cards().optJSONObject(id);if(!available.contains(id)||c.optBoolean("paused"))continue;
            if(c.optInt("seen")==0)fresh.add(id);else if(c.optLong("due")<=now)due.add(id);
        }
        due.sort(Comparator.comparingLong(id->cards().optJSONObject(id).optLong("due")));
        int allowance=pauseNew()?0:Math.max(0,limit()-introducedToday(now));
        due.addAll(fresh.subList(0,Math.min(allowance,fresh.size())));return due;
    }
    public int[] counts(Set<String> available,long now){int active=0,due=0,fresh=0,paused=0;
        for(String id:available){JSONObject c=cards().optJSONObject(id);if(c==null)continue;if(c.optBoolean("paused")){paused++;continue;}active++;if(c.optInt("seen")==0)fresh++;else if(c.optLong("due")<=now)due++;}
        return new int[]{active,due,fresh,paused};
    }
    public long nextDue(Set<String> available,long now){long next=Long.MAX_VALUE;for(String id:available){JSONObject c=cards().optJSONObject(id);if(c!=null&&!c.optBoolean("paused")&&c.optInt("seen")>0&&c.optLong("due")>now)next=Math.min(next,c.optLong("due"));}return next;}
    public int interval(String id,int rating){JSONObject c=cards().optJSONObject(id);int previous=c==null?0:c.optInt("interval");
        if(rating==0)return 0;if(rating==1)return Math.min(365,Math.max(1,(int)Math.ceil(previous*1.2)));
        if(rating==2)return previous==0?1:Math.min(365,(int)Math.ceil(previous*2.0));
        return previous==0?3:Math.min(365,Math.max(previous+1,(int)Math.ceil(previous*2.7)));
    }
    public String delay(String id,int rating){return rating==0?"1 分钟后":interval(id,rating)+" 天后";}
    public void rate(String id,int rating,long now,boolean hinted) throws Exception {
        if(rating<0||rating>3)throw new IOException("评分无效");if(hinted&&rating>1)throw new IOException("使用提示后请选择忘记或费力想起");
        JSONObject n=snapshot(),c=n.getJSONObject("cards").getJSONObject(id);
        if(c.getBoolean("paused"))throw new IOException("此词已暂停");
        int days=interval(id,rating);
        if(c.getInt("seen")==0)c.put("introduced",date(now));
        c.put("seen",c.getInt("seen")+1).put("last",now).put("interval",days).put("due",now+(rating==0?60000L:days*DAY));
        if(rating==0)c.put("lapses",c.getInt("lapses")+1);
        JSONArray history=n.optJSONArray("history");if(history==null){history=new JSONArray();n.put("history",history);}
        history.put(new JSONObject().put("id",id).put("time",now).put("day",date(now)).put("rating",rating).put("hinted",hinted).put("interval",days));save(n);
    }
    public static final class DayStats {
        public final Set<String> words=new HashSet<>(),newWords=new HashSet<>(),reviewWords=new HashSet<>();
        public int repetitions;
    }
    public Map<String,DayStats> calendar() {
        Map<String,DayStats> result=new TreeMap<>();JSONArray history=data.optJSONArray("history");if(history==null)return result;
        Map<String,Long> first=new HashMap<>();
        for(int i=0;i<history.length();i++){JSONObject r=history.optJSONObject(i);if(r==null)continue;String id=r.optString("id");long time=r.optLong("time");first.put(id,Math.min(time,first.getOrDefault(id,Long.MAX_VALUE)));}
        for(int i=0;i<history.length();i++){
            JSONObject r=history.optJSONObject(i);if(r==null)continue;String id=r.optString("id");long time=r.optLong("time");String day=r.optString("day",date(time));
            DayStats stats=result.computeIfAbsent(day,k->new DayStats());stats.words.add(id);stats.repetitions++;
            if(time==first.get(id))stats.newWords.add(id);else stats.reviewWords.add(id);
        }
        return result;
    }
    public void restore(JSONObject backup,Set<String> available) throws Exception {
        JSONObject valid=parse(backup.toString());JSONObject cs=valid.getJSONObject("cards");
        for(Iterator<String> i=cs.keys();i.hasNext();)if(!available.contains(i.next()))throw new IOException("备份含当前词库没有的词条，请先导入对应词库");
        save(valid);
    }
    public static JSONObject readBackup(InputStream input) throws Exception {
        byte[] found=null;int total=0,count=0;
        try(ZipInputStream zip=new ZipInputStream(input,StandardCharsets.UTF_8)){ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.isDirectory())continue;if(++count>300)throw new IOException("备份文件过多");byte[] bytes=DictionaryStore.read(zip,20*1024*1024);total+=bytes.length;if(total>64*1024*1024)throw new IOException("备份过大");if(e.getName().equals("study.json")){if(found!=null)throw new IOException("重复学习记录");found=bytes;}}}
        if(found==null)throw new IOException("此 ZIP 不含学习进度，请使用新版完整导出包");return parse(new String(found,StandardCharsets.UTF_8));
    }
}
