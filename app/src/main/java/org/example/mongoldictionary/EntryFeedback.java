package org.example.mongoldictionary;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.text.InputFilter;
import android.widget.*;
import org.json.JSONObject;

/** User-reviewed handoff to GitHub; no credentials or automatic issue submission. */
final class EntryFeedback {
 static final String URL="https://github.com/73wong/mongolian-chinese-dictionary/issues/new";
 static String clip(String value,int count){int length=value.codePointCount(0,value.length());return length<=count?value:value.substring(0,value.offsetByCodePoints(0,count))+"…";}
 static Uri issueUri(String title,String body){return Uri.parse(URL).buildUpon().appendQueryParameter("title",title).appendQueryParameter("body",body).build();}
 static void show(Activity a,JSONObject entry){
  boolean en=UiText.language(a).equals("en");
  String[] types=en?new String[]{"Mongolian","Transliteration","Definition","IPA","Cyrillic","Audio","Other"}:new String[]{"传统蒙古文","转写","释义","国际音标","西里尔蒙古文","语音","其他"};
  SharedPreferences drafts=a.getSharedPreferences("entry-feedback-drafts",0);
  String key=entry.optString("id",entry.optString("transliteration"));
  LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*a.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
  TextView note=new TextView(a);note.setText(en?"Feedback is public on GitHub. A GitHub account is required. Review and submit in your browser. Your draft is saved on this device.":"反馈会在 GitHub 公开，需要 GitHub 账号。请在浏览器中确认提交。草稿会保存在本机。");form.addView(note);
  Spinner type=new Spinner(a);type.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,types));type.setSelection(Math.min(types.length-1,Math.max(0,drafts.getInt(key+".type",0))));form.addView(type);
  EditText reason=new EditText(a);reason.setHint(en?"What is wrong? (required, up to 400 characters)":"问题说明（必填，最多400字）");reason.setMinLines(3);reason.setGravity(android.view.Gravity.TOP);reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);reason.setFilters(new InputFilter[]{new InputFilter.LengthFilter(400)});reason.setText(drafts.getString(key+".reason",""));form.addView(reason);
  EditText correction=new EditText(a);correction.setHint(en?"Suggested correction (optional, up to 300 characters)":"建议修改（选填，最多300字）");correction.setMinLines(2);correction.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);correction.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});correction.setText(drafts.getString(key+".correction",""));form.addView(correction);
  Runnable save=()->drafts.edit().putInt(key+".type",type.getSelectedItemPosition()).putString(key+".reason",reason.getText().toString()).putString(key+".correction",correction.getText().toString()).apply();
  android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable value){save.run();}};
  reason.addTextChangedListener(watcher);correction.addTextChangedListener(watcher);
  type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id){save.run();}public void onNothingSelected(AdapterView<?> parent){}});
  ScrollView scroll=new ScrollView(a);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle(en?"Report an entry":"词条反馈").setView(scroll).setNegativeButton(en?"Close":"关闭",null).setPositiveButton(en?"Preview":"预览",null).create();
  dialog.setOnDismissListener(d->save.run());
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   save.run();if(reason.getText().toString().trim().isEmpty()){reason.setError(en?"Describe the issue":"请填写问题说明");return;}
   String version="";try{version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception ignored){}
   String title="["+types[type.getSelectedItemPosition()]+"] "+clip(entry.optString("transliteration"),80);
   String body=(en?"## Issue":"## 问题说明")+"\n"+reason.getText()+"\n\n"+(en?"## Suggested correction":"## 建议修改")+"\n"+correction.getText()+"\n\n## Entry / 词条\nID: "+key+"\nMongolian: "+clip(entry.optString("mongolian"),100)+"\nTransliteration: "+clip(entry.optString("transliteration"),100)+"\nIPA: "+clip(entry.optString("pronunciation"),100)+"\nCyrillic: "+clip(entry.optString("pronunciation_extra"),100)+"\nDefinition: "+clip(ReferenceDefinitions.text(entry),200)+"\nSource page: "+entry.optString("source_page")+"\nApp: "+version;
   Uri uri=issueUri(title,body);boolean copy=uri.toString().length()>7000;
   TextView preview=new TextView(a);preview.setPadding(pad,pad,pad,pad);preview.setText(body);preview.setTextIsSelectable(true);ScrollView previewScroll=new ScrollView(a);previewScroll.addView(preview);
   new AlertDialog.Builder(a).setTitle(en?"Preview public feedback":"预览公开反馈").setView(previewScroll).setNegativeButton(en?"Back":"返回",null).setPositiveButton(copy?(en?"Copy & open GitHub":"复制内容并打开 GitHub"):(en?"Open GitHub":"前往 GitHub"),(p,w)->{
    if(copy)TextCopy.copy(a,body);
    try{Intent intent=new Intent(Intent.ACTION_VIEW,copy?issueUri(title,""):uri);intent.addCategory(Intent.CATEGORY_BROWSABLE);a.startActivity(intent);Toast.makeText(a,copy?(en?"Paste the report, then submit on GitHub":"请粘贴反馈内容，再在 GitHub 提交"):(en?"Review and submit on GitHub":"请在 GitHub 页面确认提交"),Toast.LENGTH_LONG).show();}
    catch(ActivityNotFoundException error){Toast.makeText(a,en?"No browser available. Draft saved.":"未找到浏览器，草稿已保存。",Toast.LENGTH_LONG).show();}
   }).show();
  }));dialog.show();
 }
}
