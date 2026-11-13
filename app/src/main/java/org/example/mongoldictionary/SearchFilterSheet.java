package org.example.mongoldictionary;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Immediate search controls, presented as a scrollable bottom sheet. */
final class SearchFilterSheet {
    interface Changed { void accept(int scope,int mode,boolean fuzzy,boolean ignoreCase); }
    private final Activity owner;
    private final Changed changed;
    private final Dialog dialog;
    private final LinearLayout content;
    private int scope,mode;
    private boolean fuzzy,ignoreCase,more;
    private static final int INK=0xff193d37,SOFT=0xffe8eee8,PAPER=0xfff8f7f2;
    static Dialog show(Activity a,int s,int m,boolean f,boolean c,Changed listener){SearchFilterSheet sheet=new SearchFilterSheet(a,s,m,f,c,listener);sheet.open();return sheet.dialog;}
    private SearchFilterSheet(Activity a,int s,int m,boolean f,boolean c,Changed listener){
        owner=a;scope=s;mode=s==0?3:m;fuzzy=f;ignoreCase=c;changed=listener;
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);
        content=new LinearLayout(a);content.setOrientation(1);content.setPadding(dp(20),dp(16),dp(20),dp(24));content.setBackground(background(PAPER,24));scroll.addView(content);dialog.setContentView(scroll);render();
    }
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    private String text(String value){return UiText.get(owner,value);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void open(){dialog.show();Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.BOTTOM;p.width=WindowManager.LayoutParams.MATCH_PARENT;p.height=WindowManager.LayoutParams.WRAP_CONTENT;p.dimAmount=.28f;w.setAttributes(p);w.getDecorView().setPadding(0,0,0,0);}
    private void notifyChanged(){if(scope==0)mode=3;changed.accept(scope,mode,fuzzy,ignoreCase);}
    private Button button(String label,boolean selected){Button b=new Button(owner);b.setText((selected?"✓  ":"")+text(label));b.setAllCaps(false);b.setTextSize(14);b.setTextColor(selected?PAPER:INK);b.setBackground(background(selected?INK:SOFT,14));b.setMinHeight(dp(48));b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setSelected(selected);b.setElevation(0);return b;}
    private void heading(String value){TextView t=new TextView(owner);t.setText(text(value));t.setTextSize(14);t.setTextColor(INK);t.setPadding(0,dp(20),0,dp(10));content.addView(t);}
    private void choices(String[] labels,int[] values,boolean fields){
        for(int start=0;start<values.length;start+=2){LinearLayout row=new LinearLayout(owner);content.addView(row);for(int j=start;j<Math.min(start+2,values.length);j++){int value=values[j];Button b=button(labels[j],value==(fields?scope:mode));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(j==start?0:dp(8),0,0,dp(8));row.addView(b,lp);b.setOnClickListener(v->{if(fields)scope=value;else mode=value;notifyChanged();render();});}}
    }
    private void render(){
        content.removeAllViews();LinearLayout header=new LinearLayout(owner);header.setGravity(Gravity.CENTER_VERTICAL);content.addView(header);
        TextView title=new TextView(owner);title.setText(text("搜索筛选"));title.setTextSize(20);title.setTextColor(INK);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button close=button("×",false);close.setContentDescription(text("关闭筛选"));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dialog.dismiss());
        heading("搜索范围");choices(new String[]{"全部字段","转写字母","传统蒙古文","西里尔蒙古文","中文释义"},new int[]{0,2,1,3,4},true);
        if(scope!=0){heading("匹配方式");choices(new String[]{"包含","开头","精确","结尾"},new int[]{3,0,1,2},false);}
        if(scope==0||scope==2){Switch toggle=new Switch(owner);toggle.setText(text("转写模糊查询"));toggle.setTextColor(INK);toggle.setMinHeight(dp(52));toggle.setChecked(fuzzy);content.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->{fuzzy=on;notifyChanged();});}
        Button extra=button("更多选项",false);extra.setText(text("更多选项")+(more?"  −":"  +"));content.addView(extra);extra.setOnClickListener(v->{more=!more;render();});
        if(more){Switch toggle=new Switch(owner);toggle.setText(text("不分大小写"));toggle.setTextColor(INK);toggle.setMinHeight(dp(52));toggle.setChecked(ignoreCase);content.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->{ignoreCase=on;notifyChanged();});}
        Button reset=button("恢复默认",false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(16);content.addView(reset,lp);reset.setOnClickListener(v->{scope=0;mode=3;fuzzy=true;ignoreCase=false;more=false;notifyChanged();render();});
    }
}
