package org.example.mongoldictionary;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Disk-backed rows: no full JSON graph is needed to open or search the dictionary. */
final class IndexedDictionary extends AbstractList<JSONObject> {
    private final SQLiteDatabase db;
    private final int count;
    private final LruCache<Integer,JSONObject> cache=new LruCache<>(256);
    private static IndexedDictionary shared;
    private static String sharedVersion;
    static synchronized IndexedDictionary open(Context context)throws Exception {
        String version;
        try(InputStream in=context.getAssets().open("dictionary-db-version.txt")){version=new String(DictionaryStore.read(in,1024),java.nio.charset.StandardCharsets.UTF_8).trim();}
        String identity=context.getFilesDir().getAbsolutePath()+":"+version;
        if(shared!=null&&identity.equals(sharedVersion))return shared;
        File directory=new File(context.getFilesDir(),"indexed-dictionary");directory.mkdirs();
        File target=new File(directory,version+".db");
        if(!target.isFile()){
            File temporary=new File(directory,version+".tmp");
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(InputStream in=context.getAssets().open("dictionary.db");FileOutputStream out=new FileOutputStream(temporary)){
                byte[] buffer=new byte[65536];int length;while((length=in.read(buffer))!=-1){out.write(buffer,0,length);digest.update(buffer,0,length);}out.getFD().sync();
            }
            if(!DictionaryStore.hex(digest.digest()).equals(version)){temporary.delete();throw new IOException("Dictionary database checksum mismatch");}
            if(!temporary.renameTo(target))throw new IOException("Cannot install dictionary database");
        }
        shared=new IndexedDictionary(SQLiteDatabase.openDatabase(target.getPath(),null,SQLiteDatabase.OPEN_READONLY));sharedVersion=identity;return shared;
    }
    interface Loader { List<JSONObject> load() throws Exception; }
    private static String importedKey;
    private static IndexedDictionary imported;
    static synchronized IndexedDictionary imported(Context context,File source,Loader loader)throws Exception {
        String version;
        try(InputStream in=context.getAssets().open("dictionary-db-version.txt")){version=new String(DictionaryStore.read(in,1024),java.nio.charset.StandardCharsets.UTF_8).trim();}
        String key=source.getAbsolutePath()+":"+source.lastModified()+":"+source.length()+":"+version;
        if(key.equals(importedKey)&&imported!=null)return imported;
        String digest=DictionaryStore.hex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        File directory=new File(context.getFilesDir(),"indexed-dictionary");directory.mkdirs();
        File target=new File(directory,"import-"+digest+".db");
        if(!target.isFile()){
            List<JSONObject> rows=loader.load();
            File temporary=new File(directory,"import-"+digest+".tmp");temporary.delete();
            SQLiteDatabase output=SQLiteDatabase.openOrCreateDatabase(temporary,null);
            try {
                output.execSQL("CREATE TABLE entries(pos INTEGER PRIMARY KEY,id TEXT UNIQUE NOT NULL,payload TEXT NOT NULL)");
                output.execSQL("CREATE TABLE search_values(pos INTEGER,field INTEGER,n TEXT,c TEXT,f TEXT,fc TEXT)");
                output.beginTransaction();
                try(android.database.sqlite.SQLiteStatement entry=output.compileStatement("INSERT INTO entries VALUES(?,?,?)");android.database.sqlite.SQLiteStatement value=output.compileStatement("INSERT INTO search_values VALUES(?,?,?,?,?,?)")){
                    for(int pos=0;pos<rows.size();pos++){
                        JSONObject row=rows.get(pos);entry.bindLong(1,pos);entry.bindString(2,row.getString("id"));entry.bindString(3,row.toString());entry.executeInsert();
                        for(int field=0;field<SearchRules.FIELDS.length;field++){
                            String n=SearchRules.prepare(row.optString(SearchRules.FIELDS[field]),false);
                            for(String variant:field==2?n.split("\\n"):new String[]{n}){
                                String folded=variant.toLowerCase(java.util.Locale.ROOT);value.clearBindings();value.bindLong(1,pos);value.bindLong(2,field+1);value.bindString(3,variant);value.bindString(4,folded);
                                if(field==1){value.bindString(5,fuzzy(variant));value.bindString(6,fuzzy(folded));}value.executeInsert();
                            }
                        }
                    }
                    for(String column:new String[]{"n","c","f","fc"})output.execSQL("CREATE INDEX search_"+column+" ON search_values("+column+",field,pos)"+(column.startsWith("f")?" WHERE field=2":""));
                    output.setTransactionSuccessful();
                }finally{output.endTransaction();}
            }finally{output.close();}
            if(!temporary.renameTo(target))throw new IOException("Cannot install imported index");
        }
        imported=new IndexedDictionary(SQLiteDatabase.openDatabase(target.getPath(),null,SQLiteDatabase.OPEN_READONLY));importedKey=key;return imported;
    }
    static synchronized void invalidateImported(){importedKey=null;}
    private IndexedDictionary(SQLiteDatabase database){db=database;try(Cursor c=db.rawQuery("SELECT count(*) FROM entries",null)){c.moveToFirst();count=c.getInt(0);}}
    @Override public int size(){return count;}
    @Override public JSONObject get(int position){
        if(position<0||position>=count)throw new IndexOutOfBoundsException();
        JSONObject row=cache.get(position);if(row!=null)return row;
        try(Cursor c=db.rawQuery("SELECT payload FROM entries WHERE pos=?",new String[]{String.valueOf(position)})){
            if(!c.moveToFirst())throw new IllegalStateException("Missing dictionary row");row=new JSONObject(c.getString(0));cache.put(position,row);return row;
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    private static String fuzzy(String value){return value.replace('u','o').replace('U','O').replace('d','t').replace('g','k');}
    List<JSONObject> search(String query,int scope,int mode,boolean folded,boolean ambiguous,Set<String> favorites,boolean starsOnly,java.util.function.BooleanSupplier cancelled){
        String q=SearchRules.prepare(query,folded);List<JSONObject> result=new ArrayList<>();if(q.isEmpty())return result;
        String column=ambiguous?(folded?"fc":"f"):(folded?"c":"n");
        // Transliteration equivalences apply only to field 2. Keep other fields literal.
        String predicate=predicate(column,mode);String where=ambiguous?"((field=2 AND "+predicate+") OR (field<>2 AND "+predicate(folded?"c":"n",mode)+"))":predicate;
        List<String> args=new ArrayList<>();addArgs(args,ambiguous?fuzzy(q):q,mode);if(ambiguous)addArgs(args,q,mode);
        if(scope!=0){where="field=? AND ("+where+")";args.add(0,String.valueOf(scope));}
        try(Cursor cursor=db.rawQuery("SELECT DISTINCT pos FROM search_values WHERE "+where+" ORDER BY pos",args.toArray(new String[0]))){
            while(cursor.moveToNext()){
                if(cancelled.getAsBoolean())return Collections.emptyList();
                JSONObject row=get(cursor.getInt(0));
                if((!starsOnly||favorites.contains(row.optString("id")))&&SearchRules.matches(row,query,scope,mode,folded,ambiguous))result.add(row);
            }
        }return result;
    }
    private static String predicate(String column,int mode){
        if(mode==1)return column+"=?";
        if(mode==0)return column+">=? AND "+column+"<?";
        if(mode==2)return "substr("+column+",-length(?))=?";
        return "instr("+column+",?)>0";
    }
    private static void addArgs(List<String> args,String query,int mode){args.add(query);if(mode==0)args.add(query+"\uDBFF\uDFFF");else if(mode==2)args.add(query);}
    Map<String,JSONObject> byId(){
        LinkedHashMap<String,Integer> positions=new LinkedHashMap<>();
        try(Cursor c=db.rawQuery("SELECT id,pos FROM entries ORDER BY pos",null)){while(c.moveToNext())positions.put(c.getString(0),c.getInt(1));}
        return new AbstractMap<String,JSONObject>(){
            @Override public int size(){return positions.size();}
            @Override public boolean containsKey(Object key){return positions.containsKey(key);}
            @Override public Set<String> keySet(){return Collections.unmodifiableSet(positions.keySet());}
            @Override public JSONObject get(Object key){Integer p=positions.get(key);return p==null?null:IndexedDictionary.this.get(p);}
            @Override public Set<Map.Entry<String,JSONObject>> entrySet(){return new AbstractSet<Map.Entry<String,JSONObject>>(){
                public int size(){return positions.size();}
                public Iterator<Map.Entry<String,JSONObject>> iterator(){Iterator<String> keys=positions.keySet().iterator();return new Iterator<Map.Entry<String,JSONObject>>(){public boolean hasNext(){return keys.hasNext();}public Map.Entry<String,JSONObject> next(){String key=keys.next();return new SimpleImmutableEntry<>(key,get(key));}};}
            };}
        };
    }
}
