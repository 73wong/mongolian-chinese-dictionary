package org.example.mongoldictionary;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Runtime regression coverage for search controls and localized layout. */
final class SearchUiChecks {
    interface Work {void run()throws Exception;}
    static void ui(Instrumentation i,Work work)throws Exception{Throwable[] error={null};i.runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);i.waitForIdleSync();}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{return NavigationChecks.field(object,name);}
    static Object call(Object object,String name)throws Exception{Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(object);}
    static void put(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static TextView text(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++){TextView t=text(g.getChildAt(n),label);if(t!=null)return t;}}return null;}
    static void click(View root,String label){TextView view=text(root,label);check(view!=null,"Missing control: "+label);check(view.performClick(),"Unclickable: "+label);}
    static void screenshot(Activity a,View view,String name)throws Exception{check(view.getWidth()>0&&view.getHeight()>0,"Unlaid-out screenshot");Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));try(java.io.FileOutputStream out=a.openFileOutput(name,0)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    static void bounds(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof Button){TextView t=(TextView)v;check(t.getHeight()>=40*v.getResources().getDisplayMetrics().density,"Small tap target: "+t.getText()+" height="+t.getHeight()+" density="+v.getResources().getDisplayMetrics().density);if(t.getLayout()!=null)check(t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom(),"Clipped button: "+t.getText());}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)bounds(g.getChildAt(n));}}
    static String run(Instrumentation i)throws Exception{
        SharedPreferences prefs=i.getTargetContext().getSharedPreferences("ui",0);Map<String,?> before=new HashMap<>(prefs.getAll());MainActivity[] host={null};Dialog[] dialog={null};
        try{
            prefs.edit().putString("language","zh").putInt("scope",0).putInt("match",0).commit();
            host[0]=(MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity a=host[0];
            ui(i,()->{View root=(View)field(a,"root");check(text(root,"Monggol Hitad Toli")!=null,"Home must render before dictionary loading");check(text(root,"蒙汉词典")==null,"Old loading title still visible");((EditText)field(a,"search")).setText("kereg");});
            NavigationChecks.await(i,()->(Boolean)field(a,"loaded"));
            ui(i,()->{View root=(View)field(a,"root");check(text(root,"Monggol Hitad Toli")!=null,"Title missing");check(text(root,"蒙汉词典")==null,"Redundant heading");check((Integer)field(a,"matchMode")==3,"Legacy settings not normalized");});
            NavigationChecks.await(i,()->!((List<?>)field(a,"visible")).isEmpty());
            ui(i,()->{check("kereg".equals(((EditText)field(a,"search")).getText().toString()),"Startup lost pending query");((EditText)field(a,"search")).setText("");});
            ui(i,()->screenshot(a,a.getWindow().getDecorView(),"search-home-zh.png"));
            ui(i,()->dialog[0]=(Dialog)call(a,"filters"));
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();check(text(root,"匹配方式")==null,"Full text exposes incompatible modes");click(root,"转写字母");check((Integer)field(a,"scope")==2,"Scope not applied immediately");check(text(root,"匹配方式")!=null,"Modes missing for limited field");});
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();click(root,"精确");check((Integer)field(a,"matchMode")==1,"Exact mode not applied");check(prefs.getInt("match",-1)==1,"Mode not persisted");});
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();screenshot(a,root,"search-filter-zh.png");bounds(root);dialog[0].dismiss();});
            ui(i,()->{TextView tag=(TextView)field(a,"filterTag");check(tag.getTextSize()/a.getResources().getDisplayMetrics().scaledDensity<=12.1,"Tag text too large");((View)tag.getParent()).performClick();check((Integer)field(a,"scope")==0&&(Integer)field(a,"matchMode")==3,"Tag did not restore full text");check(((View)tag.getParent()).getVisibility()==View.GONE,"Tag not hidden");((EditText)field(a,"search")).requestFocus();((EditText)field(a,"search")).setText("kereg");});
            NavigationChecks.await(i,()->!((List<?>)field(a,"visible")).isEmpty());
            ui(i,()->{check((Boolean)field(a,"searching"),"Focus did not enter search");screenshot(a,a.getWindow().getDecorView(),"search-results-zh.png");dialog[0]=(Dialog)call(a,"filters");});
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();click(root,"中文释义");check(text(root,"转写模糊查询")==null,"Fuzzy control shown for Chinese");click(root,"恢复默认");check((Integer)field(a,"scope")==0&&(Integer)field(a,"matchMode")==3,"Reset lost full-text binding");dialog[0].dismiss();prefs.edit().putString("language","en").commit();dialog[0]=(Dialog)call(a,"filters");});
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();check(text(root,"Search filters")!=null,"English title missing");click(root,UiText.get(a,"西里尔蒙古文"));click(root,"More options  +");});
            ui(i,()->{View root=dialog[0].getWindow().getDecorView();bounds(root);screenshot(a,root,"search-filter-en.png");dialog[0].dismiss();});
            SearchChecks.run();
            return "PASS: real activity startup, legacy defaults, immediate scope/mode persistence, full-text binding, compact tag clearing, real kereg results, conditional fuzzy controls, reset, Chinese/English button bounds, existing search rules; four rendered screenshots saved.";
        }finally{
            if(host[0]!=null)ui(i,()->{if(dialog[0]!=null)dialog[0].dismiss();host[0].finish();});
            SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:before.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)e.putString(key,(String)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else if(value instanceof Integer)e.putInt(key,(Integer)value);else if(value instanceof Long)e.putLong(key,(Long)value);else if(value instanceof Float)e.putFloat(key,(Float)value);else if(value instanceof Set)e.putStringSet(key,(Set<String>)value);}e.commit();
        }
    }
}
