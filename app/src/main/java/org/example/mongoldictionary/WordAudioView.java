package org.example.mongoldictionary;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import java.io.File;

/** A word and its recording control share one horizontal reading area. */
final class WordAudioView extends HorizontalScrollView {
 private MediaPlayer player;
 private final Speaker button;
 private final File audio;
 private final Runnable onListen;
 private boolean active;
 private VerticalTextView wordView;
 private Runnable undoSwipe;
 private float downX,downY;
 private boolean tracking,dragging,copied;
 void setUndoSwipe(Runnable action){undoSwipe=action;}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){return undoSwipe==null&&super.onInterceptTouchEvent(event);}
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  if(undoSwipe==null)return super.dispatchTouchEvent(e);
  int action=e.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){
   downX=e.getX();downY=e.getY();dragging=false;copied=false;
   int[] pos=new int[2];wordView.getLocationOnScreen(pos);
   tracking=e.getRawX()>=pos[0]&&e.getRawX()<pos[0]+wordView.getWidth()&&e.getRawY()>=pos[1]&&e.getRawY()<pos[1]+wordView.getHeight()
     &&e.getRawX()>dp(24)&&e.getRawX()<getResources().getDisplayMetrics().widthPixels-dp(24);
  }
  float dx=e.getX()-downX,dy=e.getY()-downY;
  if(action==MotionEvent.ACTION_POINTER_DOWN||copied)tracking=false;
  if(action==MotionEvent.ACTION_MOVE&&tracking){
   int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
   if(!dragging&&Math.abs(dy)>slop&&Math.abs(dy)>=Math.abs(dx)){tracking=false;}
   else if(!dragging&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.5f){
    dragging=true;getParent().requestDisallowInterceptTouchEvent(true);
    MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
   }
  }
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
   boolean consumed=dragging;
   boolean trigger=action==MotionEvent.ACTION_UP&&tracking&&!copied&&dragging&&dx>=dp(64)&&dx>Math.abs(dy)*1.5f;
   tracking=false;dragging=false;getParent().requestDisallowInterceptTouchEvent(false);
   if(trigger){undoSwipe.run();return true;}
   if(consumed)return true;
  }
  return dragging||super.dispatchTouchEvent(e);
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 WordAudioView(Context c,String word,int size,int height,File file,Runnable listen){
  super(c);audio=file;onListen=listen;setHorizontalScrollBarEnabled(false);
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);addView(row);
  wordView=new VerticalTextView(c,word,size,height);row.addView(wordView);
  wordView.setOnLongClickListener(v->{copied=true;tracking=false;TextCopy.copy(c,word);return true;});
  button=new Speaker(c);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.setMargins(dp(12),dp(8),dp(8),0);row.addView(button,lp);
  button.setOnClickListener(v->{if(audio==null||!audio.isFile()){Toast.makeText(c,en()?"No recording":"暂无录音",Toast.LENGTH_SHORT).show();return;}if(active){stop();return;}if(onListen!=null)onListen.run();start();});
  update();
 }
 private boolean en(){return UiText.language(getContext()).equals("en");}
 private void update(){button.setContentDescription(en()?(active?"Stop audio":audio==null?"No recording":"Play audio"):(active?"停止播放":audio==null?"暂无录音":"播放语音"));button.invalidate();}
 private void start(){
  stop();active=true;update();MediaPlayer p=new MediaPlayer();player=p;
  p.setOnPreparedListener(m->{if(player==m)m.start();});p.setOnCompletionListener(m->stop());
  p.setOnErrorListener((m,w,e)->{stop();Toast.makeText(getContext(),en()?"Unable to play audio":"语音无法播放",Toast.LENGTH_SHORT).show();return true;});
  try{p.setDataSource(audio.getAbsolutePath());p.prepareAsync();}catch(Exception e){stop();Toast.makeText(getContext(),en()?"Unable to read audio":"无法读取语音",Toast.LENGTH_SHORT).show();}
 }
 void stop(){if(player!=null){player.release();player=null;}active=false;update();}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
 private final class Speaker extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Speaker(Context c){super(c);setClickable(true);setFocusable(true);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(231,239,233));bg.setCornerRadius(dp(24));setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x301b4942),bg,null));}
  @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/48f,getHeight()/48f);paint.setColor(audio!=null&&audio.isFile()?Color.rgb(25,65,59):Color.rgb(140,150,146));paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
   Path p=new Path();p.moveTo(13,21);p.lineTo(18,21);p.lineTo(24,16);p.lineTo(24,32);p.lineTo(18,27);p.lineTo(13,27);p.close();canvas.drawPath(p,paint);
   if(audio==null||!audio.isFile()){canvas.drawLine(29,21,35,27,paint);canvas.drawLine(35,21,29,27,paint);}else if(active){canvas.drawLine(29,19,29,29,paint);canvas.drawLine(34,19,34,29,paint);}else{canvas.drawArc(23,18,33,30,-55,110,false,paint);canvas.drawArc(21,13,39,35,-50,100,false,paint);}canvas.restore();
  }
 }
}
