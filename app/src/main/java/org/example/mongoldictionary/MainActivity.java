package org.example.mongoldictionary;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private final List<JSONObject> entries = new ArrayList<>(), visible = new ArrayList<>();
    private Set<String> favorites;
    private SharedPreferences prefs;
    private DictionaryStore store;
    private android.media.MediaPlayer player;
    private static final int IMPORT=41, EXPORT=42, RESTORE_STUDY=43;
    private LinearLayout root;
    private EditText search;
    private TextView count;
    private ListView list;
    private boolean onlyFavorites = false, details = false;
    private String query = "";
    private boolean searching=false,settingsOpen=false,ignoreCase=false,fuzzy=true;
    private int scope=0,matchMode=0,resultPosition=0,resultTop=0;
    private Button filterButton;
    private String[] scopes(){return new String[]{UiText.get(MainActivity.this,"全部字段"),UiText.get(MainActivity.this,"传统蒙古文"),UiText.get(MainActivity.this,"转写字母"),UiText.get(MainActivity.this,"西里尔蒙古文"),UiText.get(MainActivity.this,"中文释义")};}
    private String[] modes(){return new String[]{UiText.get(MainActivity.this,"前段一致"),UiText.get(MainActivity.this,"完全一致"),UiText.get(MainActivity.this,"后段一致"),UiText.get(MainActivity.this,"部分一致")};}
    private Button action(String title,Runnable work){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.rgb(25,57,53));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),Color.rgb(215,225,220));b.setBackground(bg);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(54));bp.setMargins(0,dp(5),0,dp(5));root.addView(b,bp);b.setOnClickListener(v->work.run());return b;}
    private void hideKeyboard(){if(root==null)return;((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private void navigation(){LinearLayout bar=new LinearLayout(this);root.addView(bar);String[] names={UiText.get(MainActivity.this,"词典"),UiText.get(MainActivity.this,"学习"),UiText.get(MainActivity.this,"设置")};for(int i=0;i<3;i++){final int tab=i;Button b=new Button(this);b.setText(names[i]);b.setAllCaps(false);b.setBackgroundColor(Color.TRANSPARENT);b.setTextColor(Color.rgb(25,57,53));bar.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));b.setOnClickListener(v->{if(tab==0){searching=false;home();}else if(tab==1)startActivity(new android.content.Intent(this,StudyActivity.class));else settingsPage();});}}

    private final android.os.Handler searchHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService searchWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile int searchGeneration;
    private int audioCount=-1;
    private WordAudioView wordAudio;
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private String field(JSONObject e, String name) { return name.equals("definition")?ReferenceDefinitions.text(e):e.optString(name, ""); }
    private String key(JSONObject e) { return field(e,"id"); }
    private String legacyKey(JSONObject e) {
        return field(e,"mongolian") + "\u0001" + field(e,"transliteration") + "\u0001" + field(e,"source_page") + "\u0001" + e.optString("definition");
    }
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);if("settings".equals(intent.getStringExtra("destination")))settingsPage();else{searching=false;home();}}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("favorites", MODE_PRIVATE);
        favorites = new HashSet<>(prefs.getStringSet("entries", Collections.emptySet()));
        if (state != null) { query=state.getString("query", ""); onlyFavorites=state.getBoolean("favorites"); }
        SharedPreferences options=getSharedPreferences("ui",MODE_PRIVATE);
        scope=options.getInt("scope",0);matchMode=options.getInt("match",0);ignoreCase=options.getBoolean("ignoreCase",false);fuzzy=options.getBoolean("fuzzyTransliteration",true);
        if(state!=null){searching=state.getBoolean("searching");settingsOpen=state.getBoolean("settingsOpen");resultPosition=state.getInt("resultPosition");resultTop=state.getInt("resultTop");}
        store=new DictionaryStore(this);
        shell();label(UiText.get(this,"蒙汉词典"),28);root.addView(new ProgressBar(this));
        new Thread(()->{
          try {
            List<JSONObject> loaded=store.load();
            runOnUiThread(()->{
              if(isFinishing()||isDestroyed())return;
              try {
            entries.addAll(loaded);
            try (InputStream migration=getAssets().open("favorite-migrations.json")) {
                ByteArrayOutputStream migratedBytes=new ByteArrayOutputStream();
                byte[] buf=new byte[4096]; int length;
                while((length=migration.read(buf))!=-1) migratedBytes.write(buf,0,length);
                JSONObject mapping=new JSONObject(migratedBytes.toString("UTF-8"));
                for(Iterator<String> keys=mapping.keys();keys.hasNext();) {
                    String previous=keys.next();
                    if(favorites.remove(previous)) favorites.add(mapping.getString(previous));
                }
                prefs.edit().putStringSet("entries",new HashSet<>(favorites)).apply();
            }
            for(JSONObject entry:entries) if(favorites.remove(legacyKey(entry))) favorites.add(key(entry));
            prefs.edit().putStringSet("entries",new HashSet<>(favorites)).apply();
            if(settingsOpen||"settings".equals(getIntent().getStringExtra("destination")))settingsPage();else home();
              }catch(Exception e){loadError();}
            });
          }catch(Exception e){runOnUiThread(()->loadError());}
        },"dictionary-loader").start();
    }
    private void loadError(){if(isFinishing()||isDestroyed())return;shell();label(UiText.get(this,"词库未能加载。请保留应用数据，检查存储空间后重试。"),20);}

    private void shell() {
        searchGeneration++;searchHandler.removeCallbacksAndMessages(null);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(8)); root.setBackgroundColor(Color.rgb(248,247,242));
        setContentView(root);
    }
    private TextView label(String text,int size) {
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.rgb(25,57,53));
        t.setTextIsSelectable(true);
        t.setPadding(0,dp(6),0,dp(6)); root.addView(t); return t;
    }
    private void home() {
        stopAudio();details=false;settingsOpen=false;shell();
        if(!searching){
            View top=new View(this);root.addView(top,new LinearLayout.LayoutParams(1,0,1));
            TextView title=label("Monggol Hitad Toli",32);title.setGravity(android.view.Gravity.CENTER);title.setTypeface(android.graphics.Typeface.createFromAsset(getAssets(),"fonts/LiberationSerif-Regular.ttf"));
            TextView subtitle=label(UiText.get(MainActivity.this,"蒙汉词典"),16);subtitle.setGravity(android.view.Gravity.CENTER);
            action(UiText.get(MainActivity.this,"搜索词典"),()->{searching=true;query="";resultPosition=0;home();search.requestFocus();search.post(()->((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));});
            TextView guide=label(UiText.get(this,"输入指南 ↗"),14);guide.setTextIsSelectable(false);guide.setGravity(android.view.Gravity.END);guide.setMinHeight(dp(48));guide.setOnClickListener(v->openInputGuide());
            root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));navigation();return;
        }
        LinearLayout searchBar=new LinearLayout(this);root.addView(searchBar);
        Button back=new Button(this);back.setText("‹");back.setContentDescription(UiText.get(MainActivity.this,"返回首页"));searchBar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));back.setOnClickListener(v->{hideKeyboard();searching=false;home();});
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(18);search.setHint(UiText.get(MainActivity.this,"搜索词典"));search.setText(query);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);searchBar.addView(search,new LinearLayout.LayoutParams(0,dp(52),1));
        Button clear=new Button(this);clear.setText("×");clear.setContentDescription(UiText.get(MainActivity.this,"清空搜索"));searchBar.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(52)));clear.setOnClickListener(v->search.setText(""));
        LinearLayout tools=new LinearLayout(this);tools.setGravity(android.view.Gravity.CENTER_VERTICAL);root.addView(tools);
        filterButton=new Button(this);filterButton.setTextSize(13);filterButton.setAllCaps(false);tools.addView(filterButton,new LinearLayout.LayoutParams(0,-2,1));filterButton.setOnClickListener(v->filters());
        TextView help=new TextView(this);help.setText(UiText.get(this,"输入指南 ↗"));help.setTextSize(14);help.setTextColor(Color.rgb(25,57,53));help.setGravity(android.view.Gravity.CENTER);help.setMinHeight(dp(48));help.setPadding(dp(10),0,0,0);tools.addView(help);help.setOnClickListener(v->openInputGuide());
        count=label("",14);list=new ListView(this);list.setDividerHeight(dp(1));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{resultPosition=list.getFirstVisiblePosition();resultTop=list.getChildAt(0)==null?0:list.getChildAt(0).getTop();hideKeyboard();detail(visible.get(position));});
        list.setOnItemLongClickListener((parent,view,position,id)->{
            JSONObject e=visible.get(position);
            new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"复制词条"))
                .setItems(new String[]{UiText.get(MainActivity.this,"蒙古文"),UiText.get(MainActivity.this,"转写"),UiText.get(MainActivity.this,"释义与例句"),UiText.get(MainActivity.this,"整个词条")},(d,w)->{
                    String value=w==0?field(e,"mongolian"):w==1?field(e,"transliteration"):w==2?field(e,"definition"):field(e,"mongolian")+"\n"+field(e,"transliteration")+"\n"+field(e,"definition");TextCopy.copy(this,value);
                }).setNegativeButton(UiText.get(MainActivity.this,"取消"),null).show();return true;
        });
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int before,int c){query=s.toString();resultPosition=0;resultTop=0;refresh();}});
        search.setOnEditorActionListener((v,id,event)->{hideKeyboard();return true;});refresh();list.setSelectionFromTop(resultPosition,resultTop);
    }
    private void openInputGuide(){hideKeyboard();startActivity(new android.content.Intent(this,InputGuideActivity.class));}
    private void filters(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView fieldLabel=new TextView(this);fieldLabel.setText(UiText.get(MainActivity.this,"搜索范围"));box.addView(fieldLabel);
        Spinner fields=new Spinner(this);fields.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,scopes()));fields.setSelection(scope);box.addView(fields);
        TextView modeLabel=new TextView(this);modeLabel.setText(UiText.get(MainActivity.this,"匹配方式"));box.addView(modeLabel);
        Spinner modesView=new Spinner(this);modesView.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes()));modesView.setSelection(matchMode);box.addView(modesView);
        CheckBox fuzzyBox=new CheckBox(this);fuzzyBox.setText(UiText.get(this,"转写模糊查询"));fuzzyBox.setChecked(fuzzy);box.addView(fuzzyBox);
        CheckBox caseBox=new CheckBox(this);caseBox.setText(UiText.get(MainActivity.this,"不分大小写"));caseBox.setChecked(ignoreCase);box.addView(caseBox);
        CheckBox stars=new CheckBox(this);stars.setText(UiText.get(MainActivity.this,"只看收藏"));stars.setChecked(onlyFavorites);box.addView(stars);
        new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"搜索条件")).setView(box).setNegativeButton(UiText.get(MainActivity.this,"取消"),null).setPositiveButton(UiText.get(MainActivity.this,"应用"),(d,w)->{scope=fields.getSelectedItemPosition();matchMode=modesView.getSelectedItemPosition();ignoreCase=caseBox.isChecked();fuzzy=fuzzyBox.isChecked();onlyFavorites=stars.isChecked();getSharedPreferences("ui",MODE_PRIVATE).edit().putInt("scope",scope).putInt("match",matchMode).putBoolean("ignoreCase",ignoreCase).putBoolean("fuzzyTransliteration",fuzzy).apply();resultPosition=0;resultTop=0;refresh();}).show();
    }
    private void refresh(){
        visible.clear();List<String> labels=new ArrayList<>();
        filterButton.setText(scopes()[scope]+" · "+modes()[matchMode]+(fuzzy?UiText.get(this," · 转写模糊"):"")+(ignoreCase?UiText.get(MainActivity.this," · 不分大小写"):"")+(onlyFavorites?UiText.get(MainActivity.this," · 只看收藏"):""));
        final int generation=++searchGeneration;
        searchHandler.removeCallbacksAndMessages(null);
        final String requested=query;final int selectedScope=scope,selectedMode=matchMode;
        final boolean folded=ignoreCase,ambiguous=fuzzy,starsOnly=onlyFavorites;
        final Set<String> stars=new HashSet<>(favorites);
        final List<JSONObject> snapshot=new ArrayList<>(entries);
        list.setAdapter(null);
        count.setText(requested.trim().isEmpty()?UiText.get(this,"输入关键词开始搜索"):"…");
        if(requested.trim().isEmpty())return;
        searchHandler.postDelayed(()->searchWorker.execute(()->{
            long started=android.os.SystemClock.elapsedRealtime();
            List<JSONObject> matches=new ArrayList<>();
            for(JSONObject e:snapshot){
                if(generation!=searchGeneration||Thread.currentThread().isInterrupted())return;
                if((!starsOnly||stars.contains(key(e)))&&SearchRules.matches(e,requested,selectedScope,selectedMode,folded,ambiguous)){
                    matches.add(e);labels.add((stars.contains(key(e))?"★ ":"")+field(e,"transliteration")+"\n"+field(e,"mongolian")+"\n"+field(e,"definition"));
                }
            }
            android.util.Log.i("DictionaryPerf","search_ms="+(android.os.SystemClock.elapsedRealtime()-started));
            runOnUiThread(()->{
                if(generation!=searchGeneration||isFinishing()||isDestroyed())return;
                visible.clear();visible.addAll(matches);
        count.setText(requested.trim().isEmpty()?UiText.get(MainActivity.this,"输入关键词开始搜索"):visible.isEmpty()?UiText.get(MainActivity.this,"没有匹配词条。"):visible.size()+UiText.get(MainActivity.this," 条结果"));
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){@Override public View getView(int p,View recycled,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,recycled,parent);t.setTextSize(17);t.setText(DictionaryFont.apply(MainActivity.this,labels.get(p)));t.setMaxLines(4);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(4),dp(14),dp(4),dp(14));return t;}});
                list.setSelectionFromTop(resultPosition,resultTop);
            });
        }),200);
    }

    private void settingsPage(){
        stopAudio();details=false;settingsOpen=true;hideKeyboard();shell();label(UiText.get(MainActivity.this,"设置"),28);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout container=root;root=content;
        label(UiText.get(MainActivity.this,"偏好"),16);action(UiText.get(MainActivity.this,"界面语言"),()->languagePicker());action(UiText.get(MainActivity.this,"学习设置"),()->learningSettings());
        action(UiText.get(this,"输入与转写指南"),()->openInputGuide());
        label(UiText.get(MainActivity.this,"数据管理"),16);action(UiText.get(MainActivity.this,"导入 / 导出与备份"),()->packages());
        label(UiText.get(MainActivity.this,"词库信息"),16);label(UiText.get(MainActivity.this,"词条数量：")+entries.size(),18);TextView audioLabel=label(UiText.get(this,"本地语音：")+(audioCount<0?"…":audioCount),18);
        if(audioCount<0){List<JSONObject> snapshot=new ArrayList<>(entries);new Thread(()->{int n=0;for(JSONObject e:snapshot)if(store.hasAudio(e))n++;final int total=n;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;audioCount=total;audioLabel.setText(UiText.get(this,"本地语音：")+total);});},"audio-count").start();}
        label(UiText.get(this,"数据来源"),14);
        final String sourceUrl="http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12";
        TextView sourceLink=label(sourceUrl,14);
        sourceLink.setTextIsSelectable(false);
        sourceLink.setTextColor(Color.rgb(24,100,122));
        sourceLink.setPaintFlags(sourceLink.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        sourceLink.setOnClickListener(v->{
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(sourceUrl))); }
            catch(android.content.ActivityNotFoundException e){ TextCopy.copy(this,sourceUrl); }
        });
        sourceLink.setOnLongClickListener(v->{TextCopy.copy(this,sourceUrl);return true;});
        root=container;navigation();
    }
    private void languagePicker(){String[] names={"跟随系统 / System","简体中文","English"};String selected=getSharedPreferences("ui",MODE_PRIVATE).getString("language","zh");int index=selected.equals("zh")?1:selected.equals("en")?2:0;
        new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"界面语言")).setSingleChoiceItems(names,index,(d,w)->{getSharedPreferences("ui",MODE_PRIVATE).edit().putString("language",new String[]{"system","zh","en"}[w]).apply();d.dismiss();settingsPage();}).setNegativeButton(UiText.get(MainActivity.this,"取消"),null).show();
    }
    private void learningSettings(){try{
        StudyStore study=new StudyStore(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),0,dp(20),0);
        EditText number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);number.setText(String.valueOf(study.limit()));number.setHint(UiText.get(MainActivity.this,"每日新词 1—50"));box.addView(number);
        CheckBox paused=new CheckBox(this);paused.setText(UiText.get(MainActivity.this,"暂停学习新词"));paused.setChecked(study.pauseNew());box.addView(paused);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"学习设置")).setView(box).setNegativeButton(UiText.get(MainActivity.this,"取消"),null).setPositiveButton(UiText.get(MainActivity.this,"保存"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{study.settings(Integer.parseInt(number.getText().toString()),paused.isChecked());dialog.dismiss();}catch(Exception e){number.setError(UiText.get(MainActivity.this,"请输入 1—50 的整数"));}}));dialog.show();
    }catch(Exception e){Toast.makeText(this,UiText.get(MainActivity.this,"学习记录未能加载"),Toast.LENGTH_LONG).show();}}
    private void vertical(String text, int sizeSp, int heightDp) {
        HorizontalScrollView panel = new HorizontalScrollView(this);
        panel.setHorizontalScrollBarEnabled(true);
        panel.addView(new VerticalTextView(this, text, sizeSp, heightDp),
                new android.view.ViewGroup.LayoutParams(-2, -2));
        root.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }
    private void detail(JSONObject e) {
        details=true; shell(); Button back=new Button(this); back.setText(UiText.get(MainActivity.this,"‹ 返回搜索")); root.addView(back);back.setOnClickListener(v->home());
        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.addView(content);
        LinearLayout original=root;root=content;
        label(UiText.get(MainActivity.this,"转写字母：")+field(e,"transliteration"),27);
        wordAudio=new WordAudioView(this,field(e,"mongolian"),34,160,store.audio(e),null);root.addView(wordAudio);
        label(UiText.get(MainActivity.this,"国际音标：")+(field(e,"pronunciation").isEmpty()?UiText.get(MainActivity.this,"本条采集记录暂无"):field(e,"pronunciation")),18);
        label(UiText.get(MainActivity.this,"西里尔蒙古文：")+(field(e,"pronunciation_extra").isEmpty()?UiText.get(MainActivity.this,"本条采集记录暂无"):field(e,"pronunciation_extra")),18);
        Button star=new Button(this);root.addView(star);setStar(star,e);
        star.setOnClickListener(v->{String k=key(e);if(!favorites.remove(k))favorites.add(k);prefs.edit().putStringSet("entries",new HashSet<>(favorites)).apply();setStar(star,e);});
        Button learn=new Button(this);root.addView(learn);
        try {StudyStore study=new StudyStore(this);learn.setText(study.contains(key(e))?UiText.get(MainActivity.this,"已加入学习 · 查看任务"):UiText.get(MainActivity.this,"＋ 加入学习"));}
        catch(Exception error){learn.setText(UiText.get(MainActivity.this,"查看学习记录"));}
        learn.setOnClickListener(v->{try {StudyStore study=new StudyStore(this);if(WordFamilies.expand(Collections.singleton(key(e)),entries).stream().anyMatch(id->!study.contains(id))){List<String> group=WordFamilies.expand(Collections.singleton(key(e)),entries);int added=0;for(String id:group)if(!study.contains(id))added++;
                final int countAdded=added;
                new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"连同相关短语加入学习"))
                    .setMessage(UiText.get(MainActivity.this,"本组共 ")+group.size()+UiText.get(MainActivity.this," 条，将新增 ")+countAdded+UiText.get(MainActivity.this," 条。已加入的词保留进度；每日按新词上限逐步学习。"))
                    .setNegativeButton(UiText.get(MainActivity.this,"取消"),null).setPositiveButton(UiText.get(MainActivity.this,"加入整组"),(d,w)->{try{study.add(group);learn.setText(UiText.get(MainActivity.this,"已加入学习 · 查看任务"));Toast.makeText(this,UiText.get(MainActivity.this,"已加入整组"),Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}}).show();}else startActivity(new android.content.Intent(this,StudyActivity.class));}
            catch(Exception error){Toast.makeText(this,UiText.get(MainActivity.this,"学习记录未能保存：")+error.getMessage(),Toast.LENGTH_LONG).show();}});
        label(UiText.get(MainActivity.this,"释义与例句 · 向左滑动查看更多"),17);
        if(e.has("definition_reference"))label((UiText.language(this).equals("en")?"Definition from: ":"释义参见：")+e.optString("definition_reference"),14);
        vertical(field(e,"definition"),22,360);
        if(e.optJSONArray("references")!=null && e.optJSONArray("references").length()>0)label(UiText.get(MainActivity.this,"此条含参见词条；目标可能不在本次样本中。"),14);
        label(UiText.get(MainActivity.this,"来源页／段：")+(field(e,"source_page").isEmpty()?UiText.get(MainActivity.this,"未填写"):field(e,"source_page")),14);
        root=original;
    }
    private void packages() {
        new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"词库管理"))
          .setItems(new String[]{UiText.get(MainActivity.this,"导出词库、语音和学习进度 ZIP"),UiText.get(MainActivity.this,"导入词库 ZIP（先预览）"),UiText.get(MainActivity.this,"从 ZIP 恢复学习进度")},(dialog,which)->{
            android.content.Intent intent=new android.content.Intent(which==0?android.content.Intent.ACTION_CREATE_DOCUMENT:android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
            if(which==0)intent.putExtra(android.content.Intent.EXTRA_TITLE,"mongol-dictionary.zip");
            startActivityForResult(intent,which==0?EXPORT:(which==1?IMPORT:RESTORE_STUDY));
          }).setNegativeButton(UiText.get(MainActivity.this,"返回"),null).show();
    }
    interface Work { Runnable run() throws Exception; }
    private void background(String title,Work work) {
        android.app.ProgressDialog progress=new android.app.ProgressDialog(this);progress.setMessage(title);progress.setCancelable(false);progress.show();
        new Thread(()->{
            try {Runnable done=work.run();runOnUiThread(()->{if(!isDestroyed()){progress.dismiss();done.run();}});}
            catch(Exception error){runOnUiThread(()->{if(!isDestroyed()){progress.dismiss();new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"未完成"))
              .setMessage(error.getMessage()==null?UiText.get(MainActivity.this,"文件无法读取，请检查导入格式。"):error.getMessage()).setPositiveButton(UiText.get(MainActivity.this,"知道了"),null).show();}});}
        }).start();
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        if(request==EXPORT) background(UiText.get(MainActivity.this,"正在导出…"),()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException(UiText.get(MainActivity.this,"无法写入文件"));store.export(out,entries);}
            return ()->Toast.makeText(this,UiText.get(MainActivity.this,"词库、语音和学习进度已导出"),Toast.LENGTH_LONG).show();
        });
        if(request==RESTORE_STUDY) background(UiText.get(MainActivity.this,"正在检查学习备份…"),()->{
            JSONObject backup;
            try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(UiText.get(MainActivity.this,"无法读取文件"));backup=StudyStore.readBackup(in);}
            Set<String> available=new HashSet<>();for(JSONObject e:entries)available.add(key(e));
            for(Iterator<String> i=backup.getJSONObject("cards").keys();i.hasNext();)if(!available.contains(i.next()))throw new IOException(UiText.get(MainActivity.this,"请先导入备份中的词库，再恢复学习进度"));
            return ()->new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"恢复学习进度"))
                .setMessage(UiText.get(MainActivity.this,"此备份含 ")+backup.optJSONObject("cards").length()+UiText.get(MainActivity.this," 个学习词条。恢复将替换当前学习进度和设置；词库与收藏不变。"))
                .setNegativeButton(UiText.get(MainActivity.this,"取消"),null).setPositiveButton(UiText.get(MainActivity.this,"恢复"),(d,w)->background(UiText.get(MainActivity.this,"正在恢复…"),()->{
                    new StudyStore(this).restore(backup,available);return ()->Toast.makeText(this,UiText.get(MainActivity.this,"学习进度已恢复"),Toast.LENGTH_LONG).show();
                })).show();
        });
        if(request==IMPORT) background(UiText.get(MainActivity.this,"正在检查导入包…"),()->{
            DictionaryStore.Plan plan;
            try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(UiText.get(MainActivity.this,"无法读取文件"));plan=store.preview(in,entries);}
            return ()->new android.app.AlertDialog.Builder(this).setTitle(UiText.get(MainActivity.this,"确认导入变更")).setMessage(plan.summary())
              .setNegativeButton(UiText.get(MainActivity.this,"取消"),null).setPositiveButton(UiText.get(MainActivity.this,"导入"),(dialog,which)->background(UiText.get(MainActivity.this,"正在保存…"),()->{
                  store.commit(plan);ReferenceDefinitions.apply(this,plan.rows);return ()->{audioCount=-1;entries.clear();entries.addAll(plan.rows);home();Toast.makeText(this,UiText.get(MainActivity.this,"导入完成"),Toast.LENGTH_LONG).show();};
              })).show();
        });
    }
    private void playAudio(File file,Button button) {
        stopAudio();player=new android.media.MediaPlayer();button.setEnabled(false);
        player.setOnPreparedListener(p->{button.setEnabled(true);p.start();});
        player.setOnCompletionListener(p->stopAudio());
        player.setOnErrorListener((p,what,extra)->{button.setEnabled(true);stopAudio();Toast.makeText(this,UiText.get(MainActivity.this,"语音无法播放，请检查音频格式"),Toast.LENGTH_LONG).show();return true;});
        try {player.setDataSource(file.getAbsolutePath());player.prepareAsync();}
        catch(Exception error){button.setEnabled(true);stopAudio();Toast.makeText(this,UiText.get(MainActivity.this,"无法读取语音"),Toast.LENGTH_LONG).show();}
    }
    private void stopAudio(){if(wordAudio!=null)wordAudio.stop();if(player!=null){player.release();player=null;}}
    @Override protected void onDestroy(){searchGeneration++;searchHandler.removeCallbacksAndMessages(null);searchWorker.shutdownNow();super.onDestroy();}
    @Override protected void onStop(){stopAudio();super.onStop();}
    private void setStar(Button b,JSONObject e){b.setText(favorites.contains(key(e))?UiText.get(MainActivity.this,"★ 已收藏 · 点击取消"):UiText.get(MainActivity.this,"☆ 收藏词条"));}
    @Override public void onBackPressed(){if(details)home();else if(searching||settingsOpen){hideKeyboard();searching=false;settingsOpen=false;home();}else super.onBackPressed();}
    @Override public void onSaveInstanceState(Bundle out){out.putBoolean("searching",searching);out.putBoolean("settingsOpen",settingsOpen);out.putInt("resultPosition",resultPosition);out.putInt("resultTop",resultTop);out.putString("query",query);out.putBoolean("favorites",onlyFavorites);super.onSaveInstanceState(out);}
}
