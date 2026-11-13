package org.example.mongoldictionary;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;

/** Two peer destinations; keep their existing activity and view trees alive. */
final class AppNavigation {
    static void open(Activity owner,Class<? extends Activity> target,String destination){
        Intent intent=new Intent(owner,target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if(destination!=null)intent.putExtra("destination",destination);
        owner.startActivity(intent);owner.overridePendingTransition(0,0);
    }
    static void bar(Activity owner,LinearLayout parent,int selected){
        LinearLayout bar=new LinearLayout(owner);parent.addView(bar);
        String[] labels={"词典","学习"};
        for(int i=0;i<labels.length;i++){
            final int tab=i;
            android.widget.FrameLayout slot=new android.widget.FrameLayout(owner);
            bar.addView(slot,new LinearLayout.LayoutParams(0,dp(owner,60),1));
            Button button=new Button(owner);button.setText(UiText.get(owner,labels[i]));button.setAllCaps(false);
            button.setElevation(0);button.setStateListAnimator(null);button.setTextSize(15);
            button.setTextColor(0xff193d37);button.setSelected(i==selected);
            button.setTypeface(null,i==selected?Typeface.BOLD:Typeface.NORMAL);
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(i==selected?0xffe6ede5:Color.TRANSPARENT);bg.setCornerRadius(dp(owner,16));button.setBackground(bg);
            slot.addView(button,new android.widget.FrameLayout.LayoutParams(dp(owner,96),dp(owner,44),android.view.Gravity.CENTER));
            button.setOnClickListener(v->{if(tab!=selected)open(owner,tab==0?MainActivity.class:StudyActivity.class,"dictionary");});
        }
    }
    static ImageButton settings(Activity owner,LinearLayout parent,Runnable action){
        ImageButton button=new ImageButton(owner);button.setImageResource(org.example.mongoldictionary.R.drawable.ic_menu);
        button.setScaleType(ImageView.ScaleType.CENTER);button.setContentDescription(UiText.get(owner,"设置"));
        android.util.TypedValue background=new android.util.TypedValue();owner.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,background,true);button.setBackgroundResource(background.resourceId);
        button.setElevation(0);parent.addView(button,new LinearLayout.LayoutParams(dp(owner,48),dp(owner,48)));button.setOnClickListener(v->action.run());return button;
    }
    static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
}
