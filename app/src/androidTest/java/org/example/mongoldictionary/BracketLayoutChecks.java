package org.example.mongoldictionary;
import android.content.Context;
import java.lang.reflect.Field;
import java.util.List;
final class BracketLayoutChecks {
 static void run(Context context)throws Exception{
  for(int height:new int[]{80,160,220})for(String suffix:new String[]{"(᠊ᠨ)","（᠊ᠨ）"}){
   String value="ᠭᠣᠷᠢᠶ᠎ᠠ"+suffix;
   VerticalTextView view=new VerticalTextView(context,value,38,height);
   Field field=VerticalTextView.class.getDeclaredField("pieces");field.setAccessible(true);
   List<VerticalTextView.Piece> pieces=(List<VerticalTextView.Piece>)field.get(view);
   float center=pieces.get(0).x+pieces.get(0).breadth/2,previous=-1;
   StringBuilder original=new StringBuilder();
   for(VerticalTextView.Piece p:pieces){if(Math.abs(p.x+p.breadth/2-center)>0.1f||p.y<=previous)throw new AssertionError("Optional ending split at height "+height);previous=p.y;original.append(p.text);}
   if(!value.contentEquals(original)||!value.contentEquals(view.getContentDescription()))throw new AssertionError("Original text changed");
  }
 }
}
