package org.example.mongoldictionary;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.io.*;
import org.json.*;

/** Local USB debugging helper, shipped only in the test APK. */
public class LocalImport extends Instrumentation {
    private String token;
    @Override public void onCreate(Bundle args){super.onCreate(args);token=args.getString("token","");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(!token.matches("[a-f0-9]{32}"))throw new IOException("Invalid run token");
            DictionaryStore store=new DictionaryStore(getTargetContext());
            File input=new File(getTargetContext().getFilesDir(),"local-import.zip");
            DictionaryStore.Plan plan;
            try(InputStream in=new FileInputStream(input)){plan=store.preview(in,store.load());}
            if(plan.possibleDuplicates>0)throw new IOException("Possible duplicate IDs; use manual preview");
            store.commit(plan);
            JSONObject report=new JSONObject().put("token",token).put("ok",true).put("added",plan.added).put("updated",plan.updated).put("unchanged",plan.unchanged).put("total",plan.rows.size());
            try(FileOutputStream out=getTargetContext().openFileOutput("local-import-result.json",0)){out.write(report.toString().getBytes("UTF-8"));}
            input.delete();result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
        }catch(Exception error){result.putString("stream","IMPORT FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
    }
}
