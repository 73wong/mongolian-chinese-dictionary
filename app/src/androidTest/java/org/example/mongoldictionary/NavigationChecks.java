package org.example.mongoldictionary;

import android.app.*;
import android.os.*;
import android.view.View;
import android.widget.EditText;
import java.lang.reflect.*;

final class NavigationChecks {
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void call(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    interface Check {boolean ready()throws Exception;}
    static void await(Instrumentation i,Check check)throws Exception{long end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};Throwable[] error={null};i.runOnMainSync(()->{try{ready[0]=check.ready();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ready[0])return;SystemClock.sleep(25);}throw new AssertionError("Navigation timed out");}
    static String run(Instrumentation i)throws Exception{
        Application app=(Application)i.getTargetContext().getApplicationContext();
        Activity[] resumed={null};int[] studyCreated={0};
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){if(a instanceof StudyActivity)studyCreated[0]++;}
            public void onActivityResumed(Activity a){resumed[0]=a;}
            public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        try{
            MainActivity main=(MainActivity)i.startActivitySync(new android.content.Intent(i.getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            await(i,()->(Boolean)field(main,"loaded"));
            // A search result tree and its query must survive tab navigation intact.
            i.runOnMainSync(()->{try{Field s=MainActivity.class.getDeclaredField("searching");s.setAccessible(true);s.setBoolean(main,true);call(main,"home");((EditText)field(main,"search")).setText("kereg");}catch(Exception e){throw new RuntimeException(e);}});
            await(i,()->!((java.util.List<?>)field(main,"visible")).isEmpty());
            Object mainRoot=field(main,"root");
            i.runOnMainSync(()->AppNavigation.open(main,StudyActivity.class,null));
            await(i,()->resumed[0] instanceof StudyActivity&&field(resumed[0],"study")!=null&&!(Boolean)field(resumed[0],"loading"));
            StudyActivity study=(StudyActivity)resumed[0];Object studyRoot=field(study,"root");
            long total=0;
            for(int n=0;n<5;n++){
                long start=SystemClock.uptimeMillis();i.runOnMainSync(()->AppNavigation.open(study,MainActivity.class,"dictionary"));await(i,()->resumed[0]==main);total+=SystemClock.uptimeMillis()-start;
                if(field(main,"root")!=mainRoot||!"kereg".equals(field(main,"query")))throw new AssertionError("Dictionary state lost");
                start=SystemClock.uptimeMillis();i.runOnMainSync(()->AppNavigation.open(main,StudyActivity.class,null));await(i,()->resumed[0]==study);total+=SystemClock.uptimeMillis()-start;
                if(field(study,"root")!=studyRoot)throw new AssertionError("Study tree rebuilt on unchanged tab switch");
            }
            i.runOnMainSync(()->AppNavigation.open(study,MainActivity.class,"settings"));await(i,()->resumed[0]==main&&(Boolean)field(main,"settingsOpen"));
            i.runOnMainSync(main::onBackPressed);await(i,()->resumed[0]==study);
            if(field(study,"root")!=studyRoot)throw new AssertionError("Settings lost study state");
            i.runOnMainSync(()->AppNavigation.open(study,MainActivity.class,"dictionary"));await(i,()->resumed[0]==main);
            if(field(main,"root")!=mainRoot)throw new AssertionError("Settings lost dictionary state");
            if(studyCreated[0]!=1)throw new AssertionError("Repeated study activity creation: "+studyCreated[0]);
            return "PASS: 10 warm switches, same activity/view instances, preserved search, settings return; average resume latency="+(total/10)+" ms";
        }finally{app.unregisterActivityLifecycleCallbacks(callbacks);}
    }
}
