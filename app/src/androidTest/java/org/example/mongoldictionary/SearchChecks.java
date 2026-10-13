package org.example.mongoldictionary;
import org.json.JSONObject;
public final class SearchChecks {
 static void check(boolean value){if(!value)throw new AssertionError("search rule failed");}
 public static void run()throws Exception{
  JSONObject r=new JSONObject().put("mongolian","ᠬᠡᠷᠡᠭ\u202fᠢᠶᠡᠨ").put("transliteration","kereg bolqu").put("pronunciation_extra","хэрэг\nөөр").put("definition","发生事情");
  String[][] pairs={{"morin","murin"},{"kOl","gUl"},{"kereg","gereg"},{"tala","dala"}};
  for(String[] pair:pairs)for(int direction=0;direction<2;direction++){
   JSONObject item=new JSONObject().put("transliteration",pair[direction]);
   for(int mode=0;mode<4;mode++)check(SearchRules.matches(item,pair[1-direction],2,mode,false,true));
   check(!SearchRules.matches(item,pair[1-direction],2,1,false,false));
  }
  JSONObject head=new JSONObject().put("transliteration","aba eba-a");
  check(!SearchRules.matches(head,"eba eba-a",2,1,false,true));
  check(!SearchRules.matches(head,"aba aba-a",2,1,false,true));
  check(!SearchRules.matches(head,"aba eba-e",2,1,false,true));
  check(!SearchRules.matches(head,"be",2,3,false,true));
  for(String[] pair:new String[][]{{"na","ne"},{"mal","mel"},{"kereg","kerag"},{"aba","abe"}})
   for(int direction=0;direction<2;direction++)for(int mode=0;mode<4;mode++)
    check(!SearchRules.matches(new JSONObject().put("transliteration",pair[direction]),pair[1-direction],2,mode,false,true));
  check(!SearchRules.matches(new JSONObject().put("definition","morin"),"murin",4,1,false,true));
  check(!SearchRules.matches(new JSONObject().put("transliteration","O"),"u",2,1,false,true));
  check(SearchRules.matches(new JSONObject().put("transliteration","O"),"u",2,1,true,true));
  check(!SearchRules.matches(r,"",0,3,false));
  check(SearchRules.matches(r,"kereg",2,0,false));check(!SearchRules.matches(r,"kereg",2,1,false));
  check(SearchRules.matches(r,"bolqu",2,2,false));check(SearchRules.matches(r,"reg bol",2,3,false));
  check(!SearchRules.matches(r,"KEREG",2,0,false));check(SearchRules.matches(r,"KEREG",2,0,true));
  check(!SearchRules.matches(r,"kereg",1,3,true));check(SearchRules.matches(r,"өөр",3,1,false));
  check(!SearchRules.matches(r,"ᠬᠡᠷᠡᠭ ᠢᠶᠡᠨ",1,1,false));
 }
}
