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
    private WordAudioView wordAudio;
    private String current,undoId;
    private JSONObject undo;
    private boolean revealed,hinted;
    private boolean calendarOpen;
    private boolean roundOpen;
    private String tr(String s){return UiText.get(this,s);}
    private java.time.YearMonth calendarMonth=java.time.YearMonth.now();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        shell(tr("学习"));ProgressBar loading=new ProgressBar(this);body.addView(loading);
        new Thread(()->{
            try {
                DictionaryStore loadedDictionary=new DictionaryStore(this);
                StudyStore loadedStudy=new StudyStore(this);
                LinkedHashMap<String,JSONObject> loadedEntries=new LinkedHashMap<>();
                for(JSONObject row:loadedDictionary.load())loadedEntries.put(row.getString("id"),row);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    dictionary=loadedDictionary;study=loadedStudy;entries.putAll(loadedEntries);
                    if(saved!=null){current=saved.getString("card");revealed=saved.getBoolean("revealed");hinted=saved.getBoolean("hinted");}
                    if(current!=null&&entries.containsKey(current)&&study.contains(current)&&!study.paused(current)&&study.round(entries.keySet(),System.currentTimeMillis())!=null)card();else dashboard();
                });
            }catch(Exception e){runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                new AlertDialog.Builder(this).setTitle(tr("学习记录未能加载")).setMessage(tr("请保留应用数据。")+e.getMessage()).setPositiveButton(tr("返回"),(d,w)->finish()).setCancelable(false).show();
            });}
        },"study-loader").start();
    }

    private void shell(String title){stop();root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(8));root.setBackgroundColor(Color.rgb(248,247,242));setContentView(root);
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(25);heading.setTextColor(Color.rgb(25,57,53));root.addView(heading);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(DictionaryFont.apply(this,value));t.setTextIsSelectable(true);t.setTextSize(size);t.setTextColor(Color.rgb(25,57,53));t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t;}
    private Button button(String title,Runnable action){return control(body,title,action,false);}
    private Button control(LinearLayout parent,String title,Runnable action,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:0xff193d37);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(primary?0xff193d37:0xffe6ede5);bg.setCornerRadius(dp(14));b.setBackground(bg);b.setMinHeight(dp(50));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout panel(){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(12));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xfffffefa);bg.setCornerRadius(dp(20));box.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(16));body.addView(box,lp);return box;}
    interface Change{void run()throws Exception;}
    private boolean change(Change operation){try{operation.run();return true;}catch(Exception e){new AlertDialog.Builder(this).setTitle(UiText.get(StudyActivity.this,"未完成")).setMessage(e.getMessage()).setPositiveButton(UiText.get(StudyActivity.this,"知道了"),null).show();return false;}}
    private void vertical(String value,int size,int height){HorizontalScrollView s=new HorizontalScrollView(this);s.addView(new VerticalTextView(this,value,size,height));body.addView(s);}
    private Button link(LinearLayout parent,String title,Runnable action){Button b=control(parent,title,action,false);b.setBackgroundColor(Color.TRANSPARENT);b.setElevation(0);return b;}
    private void studyNavigation(){LinearLayout bar=new LinearLayout(this);root.addView(bar);String[] titles={"词典","学习","设置"};for(int i=0;i<3;i++){final int tab=i;Button b=new Button(this);b.setText(tr(titles[i]));b.setAllCaps(false);b.setTextColor(0xff193d37);b.setBackgroundColor(Color.TRANSPARENT);b.setSelected(i==1);if(i==1)b.setTypeface(null,android.graphics.Typeface.BOLD);bar.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));b.setOnClickListener(v->{if(tab==1)return;android.content.Intent intent=new android.content.Intent(this,MainActivity.class);intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);intent.putExtra("destination",tab==2?"settings":"dictionary");startActivity(intent);finish();});}}
    private void dashboard(){calendarOpen=false;roundOpen=false;current=null;revealed=false;hinted=false;shell(tr("学习"));long now=System.currentTimeMillis();int[] counts=study.counts(entries.keySet(),now);
        int fresh=study.pauseNew()?0:Math.min(counts[2],Math.max(0,study.limit()-study.introducedToday(now)));
        LinearLayout original=body;body=panel();
        text(tr("待复习 ")+counts[1]+"   ·   "+tr("新词 ")+fresh,24);
        JSONObject r=study.round(entries.keySet(),now);boolean pending=r!=null&&r.optInt("index")<r.optJSONArray("ids").length();
        String title=pending?tr("继续学习"):counts[1]>0?tr("开始复习")+" · "+counts[1]+tr("词"):fresh>0?tr("开始学习")+" · "+fresh+tr("词"):tr("暂无待学任务");
        control(body,title,()->{if(!pending&&!change(()->study.startRound(entries.keySet(),System.currentTimeMillis())))return;next();},true).setEnabled(pending||counts[1]+fresh>0);
        body=original;if(counts[0]==0)text(tr("先在我的词表中加入想学的词"),16);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);body.addView(heading);TextView caption=new TextView(this);caption.setText(tr("学习记录"));caption.setTextSize(19);caption.setTextColor(0xff193d37);heading.addView(caption,new LinearLayout.LayoutParams(0,-2,1));Button all=link(heading,tr("查看全部")+" ›",()->calendar());all.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        Map<String,StudyStore.DayStats> stats=study.calendar();java.time.LocalDate today=java.time.LocalDate.now();
        java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern(UiText.language(this).equals("en")?"MMM d":"M月d日",UiText.language(this).equals("en")?java.util.Locale.ENGLISH:java.util.Locale.CHINESE);
        text(today.minusDays(6).format(format)+" — "+today.format(format),13);
        LinearLayout week=new LinearLayout(this);body.addView(week);String[] weekdays=UiText.language(this).equals("en")?new String[]{"M","T","W","T","F","S","S"}:new String[]{"一","二","三","四","五","六","日"};
        for(int i=6;i>=0;i--){java.time.LocalDate date=today.minusDays(i);StudyStore.DayStats value=stats.get(date.toString());int n=value==null?0:value.words.size();TextView tile=new TextView(this);tile.setGravity(17);tile.setTextSize(12);tile.setText(weekdays[date.getDayOfWeek().getValue()-1]+"\n"+date.getDayOfMonth()+"\n"+(n==0?"—":n));tile.setContentDescription(date+" · "+n);tile.setTextColor(n>0?Color.WHITE:0xff193d37);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(n>0?0xff409477:0xffe6ede5);bg.setCornerRadius(dp(8));if(date.equals(today))bg.setStroke(dp(2),0xff193d37);tile.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(74),1);lp.setMargins(dp(2),dp(4),dp(2),dp(4));week.addView(tile,lp);tile.setOnClickListener(v->dayDetails(date.toString(),value));}
        text(tr("每日完成词数 · 描边为今天"),13);Button words=link(body,tr("我的词表")+"  ›",()->wordList());words.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        if(undo!=null)link(body,tr("撤销上一次评分"),()->undo());studyNavigation();
    }
    private void wordList(){calendarOpen=true;current=null;shell(tr("我的词表"));button(tr("‹ 返回今日任务"),()->dashboard());button(tr("随机选词 · 连同相关短语"),()->randomOptions());button(tr("手动选择词条（连同相关短语）"),()->choose());button(tr("学习词表 · 暂停 / 恢复"),()->manage());button(tr("学习设置"),()->settings());}
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
    private void next(){JSONObject r=study.round(entries.keySet(),System.currentTimeMillis());if(r==null){dashboard();return;}JSONArray ids=r.optJSONArray("ids");int index=r.optInt("index");if(index>=ids.length()){finished();return;}current=ids.optString(index);if(study.paused(current)){if(change(()->study.defer(current,System.currentTimeMillis())))next();return;}revealed=false;hinted=false;roundOpen=true;card();}
    private static String summary(JSONObject row){String s=ReferenceDefinitions.text(row);int end=s.length();for(String mark:new String[]{"：",":","②","；"}){int i=s.indexOf(mark);if(i>0)end=Math.min(end,i);}return s.substring(0,end);}
    private void finished(){current=null;roundOpen=true;shell(tr("这一轮完成了"));JSONObject r=study.round(entries.keySet(),System.currentTimeMillis());JSONArray results=r==null?new JSONArray():r.optJSONArray("results");int fresh=0,review=0,hard=0,skipped=0;
        for(int i=0;i<results.length();i++){JSONObject item=results.optJSONObject(i);int rating=item.optInt("rating");if(rating<0){skipped++;continue;}if(item.optBoolean("fresh"))fresh++;else review++;if(rating<2&&!item.optBoolean("fresh"))hard++;}
        text((fresh+review)+tr(" 个词"),36);text(tr("新学 ")+fresh+" · "+tr("复习 ")+review,20);text(tr("需要巩固：")+hard,18);text(tr("稍后再学：")+skipped,18);text(tr("完成一轮不代表永久掌握，后续按计划复习。"),14);
        if(undo!=null)button(tr("撤销上一次评分"),()->undo());control(root,tr("结束，返回学习"),()->dashboard(),true);control(root,tr("再学一轮"),()->{if(change(()->study.startRound(entries.keySet(),System.currentTimeMillis())))next();},false).setEnabled(!study.queue(entries.keySet(),System.currentTimeMillis()).isEmpty());
    }
    private void card(){JSONObject row=entries.get(current);if(row==null){dashboard();return;}roundOpen=true;boolean fresh=study.fresh(current);shell(tr(fresh?"认识新词":"回忆含义"));JSONObject r=study.round(entries.keySet(),System.currentTimeMillis());
        button(tr("‹ 暂停，返回学习"),()->dashboard());if(r!=null){text((r.optInt("index")+1)+" / "+r.optJSONArray("ids").length(),14);ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(r.optJSONArray("ids").length());progress.setProgress(r.optInt("index"));body.addView(progress);}
        boolean answer=revealed||fresh;text(tr(fresh?"先认识词形与含义，再开始练习":answer?"核对含义后，按实际回忆情况评分":"先回忆含义，再显示答案"),14);wordAudio=new WordAudioView(this,row.optString("mongolian"),38,answer?150:220,dictionary.audio(row),()->{if(!answer)hinted=true;});body.addView(wordAudio);
        wordAudio.setUndoSwipe(()->{if(undo!=null){undo();Toast.makeText(this,UiText.language(this).equals("en")?"Last rating undone":"已撤销上一次评分",Toast.LENGTH_SHORT).show();}});
        android.content.SharedPreferences gesturePrefs=getSharedPreferences("study-gestures",MODE_PRIVATE);
        if(!gesturePrefs.getBoolean("word-swipe-shown",false)){
            text(UiText.language(this).equals("en")?"Swipe right on the Mongolian headword to undo the last rating.":"在上方蒙古文词头区域右滑，撤销上一次评分。",13);
            gesturePrefs.edit().putBoolean("word-swipe-shown",true).apply();
        }
        File audio=dictionary.audio(row);
        if(answer){text(row.optString("transliteration"),23);if(row.has("definition_reference"))text((UiText.language(this).equals("en")?"Definition from: ":"释义参见：")+row.optString("definition_reference"),13);vertical(summary(row),22,170);
            button(tr("展开完整释义与例句"),()->{HorizontalScrollView full=new HorizontalScrollView(this);full.addView(new VerticalTextView(this,ReferenceDefinitions.text(row),22,300));new AlertDialog.Builder(this).setTitle(row.optString("transliteration")).setView(full).setPositiveButton(tr("返回"),null).show();});
            if(!row.optString("pronunciation").isEmpty())text(row.optString("pronunciation"),16);if(!row.optString("pronunciation_extra").isEmpty())text(row.optString("pronunciation_extra"),16);
        }else{if(hinted)text(row.optString("transliteration"),22);else button(tr("查看转写提示"),()->{hinted=true;card();});}

        if(!answer){control(root,tr("显示答案"),()->{revealed=true;card();},true);return;}
        if(fresh){control(root,tr("已认识，稍后练习"),()->complete(0),true);return;}
        if(hinted)text(tr("已用提示：请选择忘记或费力想起"),13);
        String[] names={"忘记了","费力想起","记得","很轻松"};for(int line=0;line<2;line++){LinearLayout pair=new LinearLayout(this);root.addView(pair);for(int j=0;j<2;j++){final int rating=line*2+j;String delay=rating==0?tr("1 分钟后"):study.interval(current,rating)+tr(" 天后");Button b=control(pair,tr(names[rating])+" · "+delay,()->complete(rating),rating==2);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));b.setLayoutParams(lp);b.setTextSize(14);b.setEnabled(!hinted||rating<2);}}
    }
    private void complete(int rating){String id=current;try{JSONObject before=study.snapshot();if(rating<0)study.defer(id,System.currentTimeMillis());else study.rate(id,rating,System.currentTimeMillis(),hinted);undo=before;undoId=id;next();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton(tr("知道了"),null).show();}}
    private void undo(){if(undo==null)return;if(change(()->study.restore(undo,entries.keySet()))){current=undoId;undo=null;revealed=false;hinted=false;roundOpen=true;if(current!=null)card();else dashboard();}}
    private void play(File file){stop();player=new android.media.MediaPlayer();player.setOnPreparedListener(p->p.start());player.setOnCompletionListener(p->stop());player.setOnErrorListener((p,w,e)->{stop();Toast.makeText(this,UiText.get(StudyActivity.this,"语音无法播放"),Toast.LENGTH_LONG).show();return true;});try{player.setDataSource(file.getAbsolutePath());player.prepareAsync();}catch(Exception e){stop();Toast.makeText(this,UiText.get(StudyActivity.this,"语音无法读取"),Toast.LENGTH_LONG).show();}}
    private void stop(){if(wordAudio!=null)wordAudio.stop();if(player!=null){player.release();player=null;}}
    @Override public void onBackPressed(){if(current!=null||calendarOpen||roundOpen)dashboard();else super.onBackPressed();}
    @Override protected void onStop(){stop();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("card",current);out.putBoolean("revealed",revealed);out.putBoolean("hinted",hinted);super.onSaveInstanceState(out);}
}
