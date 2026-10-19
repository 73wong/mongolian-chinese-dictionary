package org.example.mongoldictionary;
import android.content.Context;
import android.view.*;
import android.widget.*;
final class SwipeUndoChecks {
 static void run(Context c){
  float d=c.getResources().getDisplayMetrics().density;int[] count={0};
  FrameLayout host=new FrameLayout(c);WordAudioView v=new WordAudioView(c,"ᠬᠡᠷᠡᠭ",38,150,null,null);host.addView(v);v.setUndoSwipe(()->count[0]++);
  host.measure(View.MeasureSpec.makeMeasureSpec((int)(360*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((int)(200*d),View.MeasureSpec.EXACTLY));host.layout(0,0,(int)(360*d),(int)(200*d));
  swipe(v,d,30,30,110,32,MotionEvent.ACTION_UP);if(count[0]!=1)throw new AssertionError("Right swipe");
  swipe(v,d,30,30,50,32,MotionEvent.ACTION_UP);
  swipe(v,d,30,30,110,140,MotionEvent.ACTION_UP);
  swipe(v,d,30,30,110,32,MotionEvent.ACTION_CANCEL);
  swipe(v,d,2,30,110,32,MotionEvent.ACTION_UP);
  LinearLayout row=(LinearLayout)v.getChildAt(0);View speaker=row.getChildAt(1);float x=(speaker.getLeft()+speaker.getWidth()/2f)/d;
  swipe(v,d,x,30,x+90,32,MotionEvent.ACTION_UP);
  if(count[0]!=1)throw new AssertionError("Accidental undo");
 }
 static void swipe(View v,float d,float x,float y,float endX,float endY,int finish){
  long t=android.os.SystemClock.uptimeMillis();int[] actions={0,2,finish};float[] xs={x,endX,endX},ys={y,endY,endY};
  for(int i=0;i<3;i++){MotionEvent e=MotionEvent.obtain(t,t+i*100,actions[i],xs[i]*d,ys[i]*d,0);v.dispatchTouchEvent(e);e.recycle();}
 }
}
