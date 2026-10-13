package org.example.mongoldictionary;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class StudyActivity extends Activity {
    private StudyStore study;
    private DictionaryStore dictionary;
    private final LinkedHashMap<String,JSONObject> entries=new LinkedHashMap<>();
    private LinearLayout root,body;
    private android.media.MediaPlayer player;
    private String current,undoId;
    private JSONObject undo;
    private boolean revealed,hinted;
    private boolean calendarOpen;
    private java.time.YearMonth calendarMonth=java.time.YearMonth.now();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        try {
            dictionary=new DictionaryStore(this);study=new StudyStore(this);
            for(JSONObject row:dictionary.load())entries.put(row.getString("id"),row);
            if(saved!=null){current=saved.getString("card");revealed=saved.getBoolean("revealed");hinted=saved.getBoolean("hinted");}
            if(current!=null&&entries.containsKey(current)&&study.contains(current)&&!study.paused(current))card();else dashboard();
        } catch(Exception e){new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"学习记录未能加载")).setMessage(UiText.get(StudyActivity.this,"请保留应用数据。")+e.getMessage()).setPositiveButton(UiText.get(StudyActivity.this,"返回"),(d,w)->finish()).setCancelable(false).show();}
    }
    private void shell(String title){stop();root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(8));root.setBackgroundColor(Color.rgb(248,247,242));setContentView(root);
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(25);heading.setTextColor(Color.rgb(25,57,53));root.addView(heading);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(DictionaryFont.apply(this,value));t.setTextIsSelectable(true);t.setTextSize(size);t.setTextColor(Color.rgb(25,57,53));t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);body.addView(b);b.setOnClickListener(v->action.run());return b;}
    interface Change{void run()throws Exception;}
    private boolean change(Change operation){try{operation.run();return true;}catch(Exception e){new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"未完成")).setMessage(e.getMessage()).setPositiveButton(UiText.get(StudyActivity.this,"知道了"),null).show();return false;}}
    private void vertical(String value,int size,int height){HorizontalScrollView s=new HorizontalScrollView(this);s.addView(new VerticalTextView(this,value,size,height));body.addView(s);}
    private void dashboard(){calendarOpen=false;current=null;revealed=false;hinted=false;shell(UiText.get(StudyActivity.this,"今日学习"));long now=System.currentTimeMillis();int[] counts=study.counts(entries.keySet(),now);int availableNew=study.pauseNew()?0:Math.min(counts[2],Math.max(0,study.limit()-study.introducedToday(now)));
        text(UiText.get(StudyActivity.this,"待复习 ")+counts[1]+UiText.get(StudyActivity.this,"  · 今日可学新词 ")+availableNew,21);
        text(UiText.get(StudyActivity.this,"学习中 ")+counts[0]+UiText.get(StudyActivity.this,"  · 暂停 ")+counts[3]+UiText.get(StudyActivity.this,"\n今日已开始新词 ")+study.introducedToday(now)+" / "+study.limit(),15);
        button(UiText.get(StudyActivity.this,"开始学习"),()->next());
        if(counts[0]==0)text(UiText.get(StudyActivity.this,"从词典详情加入学习，或在下面选择词条。收藏与学习互相独立。"),16);
        long due=study.nextDue(entries.keySet(),now);if(due!=Long.MAX_VALUE)text(UiText.get(StudyActivity.this,"最近一次复习：")+android.text.format.DateFormat.format("MM-dd HH:mm",due),15);
        if(study.pauseNew())text(UiText.get(StudyActivity.this,"已暂停新词，仍可复习旧词。"),15);
        button(UiText.get(StudyActivity.this,"学习日历 · 颜色记录"),()->calendar());
        button(UiText.get(StudyActivity.this,"随机选词 · 连同相关短语"),()->randomOptions());
        button(UiText.get(StudyActivity.this,"手动选择词条（连同相关短语）"),()->choose());
        button(UiText.get(StudyActivity.this,"学习词表 · 暂停 / 恢复"),()->manage());
        if(undo!=null)button(UiText.get(StudyActivity.this,"撤销上一次评分"),()->undo());
        button(UiText.get(StudyActivity.this,"返回词典"),()->finish());
        text(UiText.get(StudyActivity.this,"先回忆，再看答案。使用提示后只能选择忘记或费力想起。\n备份：设置 → 导入 / 导出与备份，完整 ZIP 会包含学习进度。"),14);
    }
    private void choose(){List<String> ids=new ArrayList<>(),labels=new ArrayList<>();for(JSONObject row:entries.values())if(!study.contains(row.optString("id"))){ids.add(row.optString("id"));labels.add(row.optString("transliteration")+" · "+summary(row));}
        if(ids.isEmpty()){text(UiText.get(StudyActivity.this,"所有词条都已加入学习。"),16);return;}boolean[] selected=new boolean[ids.size()];
        new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"选择要学习的词")) .setMultiChoiceItems(labels.toArray(new String[0]),selected,(d,w,b)->selected[w]=b)
            .setNegativeButton(UiText.get(StudyActivity.this,"取消"),null).setPositiveButton(UiText.get(StudyActivity.this,"加入"),(d,w)->{List<String> chosen=new ArrayList<>();for(int i=0;i<ids.size();i++)if(selected[i])chosen.add(ids.get(i));confirmGroup(WordFamilies.expand(chosen,entries.values()),false,0);}).show();
    }
    private void randomOptions(){
        new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"随机抽取多少组？"))
            .setItems(new String[]{UiText.get(StudyActivity.this,"1 组"),UiText.get(StudyActivity.this,"3 组"),UiText.get(StudyActivity.this,"5 组")},(d,w)->randomGroup(new int[]{1,3,5}[w])).setNegativeButton(UiText.get(StudyActivity.this,"取消"),null).show();
    }
    private void randomGroup(int count){Set<String> enrolled=new HashSet<>();for(String id:entries.keySet())if(study.contains(id))enrolled.add(id);
        List<String> selected=WordFamilies.random(entries.values(),enrolled,count,new Random());
        if(selected.isEmpty()){new AlertDialog.Builder(this).setMessage(UiText.get(StudyActivity.this,"所有词组都已加入学习。已暂停的词可在学习词表恢复。")).setPositiveButton(UiText.get(StudyActivity.this,"知道了"),null).show();return;}
        confirmGroup(selected,true,count);
    }
    private void confirmGroup(List<String> ids,boolean random,int requested){
        if(ids.isEmpty())return;int added=0;StringBuilder preview=new StringBuilder();Set<String> families=new HashSet<>();
        for(String id:ids){JSONObject row=entries.get(id);families.add(WordFamilies.family(row));if(!study.contains(id)){added++;preview.append("\n").append(row.optString("transliteration"));}}
        if(added==0){Toast.makeText(this,UiText.get(StudyActivity.this,"所选词组已全部加入"),Toast.LENGTH_SHORT).show();return;}
        String message=UiText.get(StudyActivity.this,"本次 ")+families.size()+UiText.get(StudyActivity.this," 组，共 ")+ids.size()+UiText.get(StudyActivity.this," 条；新增 ")+added+" 条。\n相关短语一起加入，已有进度不变。每天仍按新词上限学习。\n\n将新增："+preview;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"确认整组加入")).setMessage(message)
            .setNegativeButton(UiText.get(StudyActivity.this,"取消"),null).setPositiveButton(UiText.get(StudyActivity.this,"加入整组"),(d,w)->{if(change(()->study.add(ids))){undo=null;dashboard();}});
        if(random)dialog.setNeutralButton(UiText.get(StudyActivity.this,"换一批"),(d,w)->randomGroup(requested));dialog.show();
    }
    private void calendar(){calendarOpen=true;current=null;shell(UiText.get(StudyActivity.this,"学习日历"));
        button(UiText.get(StudyActivity.this,"‹ 返回今日任务"),()->dashboard());
        LinearLayout navigation=new LinearLayout(this);body.addView(navigation);
        Button previous=new Button(this);previous.setText("‹");navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));previous.setOnClickListener(v->{calendarMonth=calendarMonth.minusMonths(1);calendar();});
        TextView month=new TextView(this);month.setText(calendarMonth.getYear()+UiText.get(StudyActivity.this," 年 ")+calendarMonth.getMonthValue()+UiText.get(StudyActivity.this," 月"));month.setTextSize(19);month.setGravity(android.view.Gravity.CENTER);navigation.addView(month,new LinearLayout.LayoutParams(0,-1,3));
        Button next=new Button(this);next.setText("›");next.setEnabled(calendarMonth.isBefore(java.time.YearMonth.now()));navigation.addView(next,new LinearLayout.LayoutParams(0,-2,1));next.setOnClickListener(v->{calendarMonth=calendarMonth.plusMonths(1);calendar();});
        Map<String,StudyStore.DayStats> stats=study.calendar();int activeDays=0,total=0;for(Map.Entry<String,StudyStore.DayStats> e:stats.entrySet())if(e.getKey().startsWith(calendarMonth.toString())){activeDays++;total+=e.getValue().words.size();}
        text(UiText.get(StudyActivity.this,"本月学习 ")+activeDays+UiText.get(StudyActivity.this," 天 · 累计 ")+total+UiText.get(StudyActivity.this," 词次\n每天按不同词条计数，同一天反复背一个词只算 1 条。"),14);
        LinearLayout weekdays=new LinearLayout(this);body.addView(weekdays);for(String day:new String[]{UiText.get(StudyActivity.this,"一"),UiText.get(StudyActivity.this,"二"),UiText.get(StudyActivity.this,"三"),UiText.get(StudyActivity.this,"四"),UiText.get(StudyActivity.this,"五"),UiText.get(StudyActivity.this,"六"),UiText.get(StudyActivity.this,"日")}){TextView t=new TextView(this);t.setText(day);t.setGravity(17);weekdays.addView(t,new LinearLayout.LayoutParams(0,dp(28),1));}
        java.time.LocalDate start=calendarMonth.atDay(1);int offset=start.getDayOfWeek().getValue()-1,slots=((offset+calendarMonth.lengthOfMonth()+6)/7)*7;
        LinearLayout week=null;
        for(int i=0;i<slots;i++){
            if(i%7==0){week=new LinearLayout(this);body.addView(week);}
            int day=i-offset+1;TextView tile=new TextView(this);tile.setGravity(17);tile.setTextSize(13);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));week.addView(tile,lp);
            if(day<1||day>calendarMonth.lengthOfMonth())continue;
            java.time.LocalDate date=calendarMonth.atDay(day);StudyStore.DayStats value=stats.get(date.toString());int count=value==null?0:value.words.size();
            tile.setText(day+"\n"+(count==0?"—":count+UiText.get(StudyActivity.this,"词")));int level=count==0?0:count<5?1:count<10?2:count<20?3:4;
            int[] colors={0xffe8ebe7,0xffc3e3d4,0xff8ac7ac,0xff409477,0xff185c48};
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(colors[level]);background.setCornerRadius(dp(4));if(date.equals(java.time.LocalDate.now()))background.setStroke(dp(2),0xff185c48);tile.setBackground(background);tile.setTextColor(level>=3?Color.WHITE:0xff193935);
            tile.setContentDescription(date+UiText.get(StudyActivity.this,"，学习 ")+count+UiText.get(StudyActivity.this," 条"));tile.setOnClickListener(v->dayDetails(date.toString(),value));
        }
        text(UiText.get(StudyActivity.this,"颜色由浅到深：0、1—4、5—9、10—19、20+ 条\n边框标记今天。点击日期查看新学、复习和练习次数。"),14);
        text(UiText.get(StudyActivity.this,"完成一次评分才计入，加入词表或只翻看不计入。此处记录学习量，不代表已经掌握。"),14);
    }
    private void dayDetails(String day,StudyStore.DayStats stats){
        if(stats==null){new AlertDialog.Builder(this).setTitle(day).setMessage(UiText.get(StudyActivity.this,"当天没有已完成的学习记录。")).setPositiveButton(UiText.get(StudyActivity.this,"返回"),null).show();return;}
        StringBuilder details=new StringBuilder(UiText.get(StudyActivity.this,"学习 ")+stats.words.size()+UiText.get(StudyActivity.this," 条（去重）\n新学 ")+stats.newWords.size()+UiText.get(StudyActivity.this," 条 · 复习 ")+stats.reviewWords.size()+UiText.get(StudyActivity.this," 条\n共完成 ")+stats.repetitions+UiText.get(StudyActivity.this," 次练习\n新学后当天再复习可同时计入两类。\n"));
        for(String id:entries.keySet())if(stats.words.contains(id))details.append("\n").append(entries.get(id).optString("transliteration"));
        new AlertDialog.Builder(this).setTitle(day).setMessage(details.toString()).setPositiveButton(UiText.get(StudyActivity.this,"返回"),null).show();
    }
    private void manage(){List<String> ids=new ArrayList<>(),labels=new ArrayList<>();for(JSONObject row:entries.values())if(study.contains(row.optString("id"))){String id=row.optString("id");ids.add(id);labels.add((study.paused(id)?UiText.get(StudyActivity.this,"已暂停 · "):UiText.get(StudyActivity.this,"学习中 · "))+row.optString("transliteration"));}
        new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"点击暂停或恢复（保留进度）")).setItems(labels.toArray(new String[0]),(d,w)->{String id=ids.get(w);if(change(()->study.pause(id,!study.paused(id)))){undo=null;dashboard();}}).setNegativeButton(UiText.get(StudyActivity.this,"返回"),null).show();
    }
    private void settings(){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),0,dp(20),0);EditText number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);number.setText(String.valueOf(study.limit()));number.setHint(UiText.get(StudyActivity.this,"每日新词 1—50"));box.addView(number);CheckBox paused=new CheckBox(this);paused.setText(UiText.get(StudyActivity.this,"暂停学习新词"));paused.setChecked(study.pauseNew());box.addView(paused);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"每日新词 1—50")).setView(box).setNegativeButton(UiText.get(StudyActivity.this,"取消"),null).setPositiveButton(UiText.get(StudyActivity.this,"保存"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(change(()->study.settings(Integer.parseInt(number.getText().toString()),paused.isChecked()))){undo=null;dialog.dismiss();dashboard();}}));dialog.show();
    }
    private void next(){List<String> queue=study.queue(entries.keySet(),System.currentTimeMillis());if(queue.isEmpty()){dashboard();text(UiText.get(StudyActivity.this,"当前任务已完成。稍后到期的词会按计划再次出现。"),18);return;}current=queue.get(0);revealed=false;hinted=false;card();}
    private static String summary(JSONObject row){String s=row.optString("definition");int end=s.length();for(String mark:new String[]{"：",":","②","；"}){int i=s.indexOf(mark);if(i>0)end=Math.min(end,i);}return s.substring(0,end);}
    private void card(){JSONObject row=entries.get(current);shell(revealed?UiText.get(StudyActivity.this,"核对答案"):UiText.get(StudyActivity.this,"看文回忆"));
        button(UiText.get(StudyActivity.this,"‹ 返回今日任务"),()->dashboard());
        text(revealed?(hinted?UiText.get(StudyActivity.this,"已用提示：请选择忘记或费力想起"):UiText.get(StudyActivity.this,"核对含义后，按实际回忆情况评分")):UiText.get(StudyActivity.this,"先回忆含义，再显示答案"),15);vertical(row.optString("mongolian"),34,revealed?95:160);
        File audio=dictionary.audio(row);
        if(!revealed){
            if(hinted)text(UiText.get(StudyActivity.this,"转写提示：")+row.optString("transliteration"),21);
            button(hinted?UiText.get(StudyActivity.this,"已使用提示"):UiText.get(StudyActivity.this,"查看转写提示"),()->{hinted=true;card();}).setEnabled(!hinted);
            if(audio!=null&&audio.isFile())button(UiText.get(StudyActivity.this,"听发音提示"),()->{hinted=true;play(audio);});
            button(UiText.get(StudyActivity.this,"显示答案"),()->{revealed=true;card();});
        } else {
            text(UiText.get(StudyActivity.this,"转写字母：")+row.optString("transliteration"),23);
            text(UiText.get(StudyActivity.this,"国际音标：")+(row.optString("pronunciation").isEmpty()?UiText.get(StudyActivity.this,"本条采集记录暂无"):row.optString("pronunciation")),18);
            text(UiText.get(StudyActivity.this,"西里尔蒙古文：")+(row.optString("pronunciation_extra").isEmpty()?UiText.get(StudyActivity.this,"本条采集记录暂无"):row.optString("pronunciation_extra")),18);
            vertical(summary(row),22,150);
            Button expand=button(UiText.get(StudyActivity.this,"展开完整释义与例句"),()->{});
            expand.setOnClickListener(v->{expand.setEnabled(false);vertical(row.optString("definition"),22,300);});
            if(audio!=null&&audio.isFile())button(UiText.get(StudyActivity.this,"▶ 播放语音"),()->play(audio));else text(UiText.get(StudyActivity.this,"此词语音待补"),14);

            LinearLayout grades=new LinearLayout(this);grades.setOrientation(1);root.addView(grades);
            String[] names={UiText.get(StudyActivity.this,"忘记了"),UiText.get(StudyActivity.this,"费力想起"),UiText.get(StudyActivity.this,"记得"),UiText.get(StudyActivity.this,"很轻松")};
            for(int line=0;line<2;line++){LinearLayout pair=new LinearLayout(this);grades.addView(pair);for(int j=0;j<2;j++){final int rating=line*2+j;Button b=new Button(this);b.setText(names[rating]+" · "+study.delay(current,rating));b.setTextSize(14);b.setEnabled(!hinted||rating<2);pair.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{String id=current;try{JSONObject previous=study.snapshot();study.rate(id,rating,System.currentTimeMillis(),hinted);undo=previous;undoId=id;next();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton(UiText.get(StudyActivity.this,"知道了"),null).show();}});}}
        }
        if(undo!=null)button(UiText.get(StudyActivity.this,"撤销上一次评分"),()->undo());
    }
    private void undo(){if(undo==null)return;if(change(()->study.restore(undo,entries.keySet()))){current=undoId;undo=null;revealed=false;hinted=false;if(current!=null)card();else dashboard();}}
    private void play(File file){stop();player=new android.media.MediaPlayer();player.setOnPreparedListener(p->p.start());player.setOnCompletionListener(p->stop());player.setOnErrorListener((p,w,e)->{stop();Toast.makeText(this,UiText.get(StudyActivity.this,"语音无法播放"),Toast.LENGTH_LONG).show();return true;});try{player.setDataSource(file.getAbsolutePath());player.prepareAsync();}catch(Exception e){stop();Toast.makeText(this,UiText.get(StudyActivity.this,"语音无法读取"),Toast.LENGTH_LONG).show();}}
    private void stop(){if(player!=null){player.release();player=null;}}
    @Override public void onBackPressed(){if(current!=null||calendarOpen)dashboard();else super.onBackPressed();}
    @Override protected void onStop(){stop();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("card",current);out.putBoolean("revealed",revealed);out.putBoolean("hinted",hinted);super.onSaveInstanceState(out);}
}
