package org.example.mongoldictionary;

import android.content.*;
import android.widget.Toast;

public final class TextCopy {
    public static void copy(Context context,String text) {
        ((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("词典文本",text));
        Toast.makeText(context,"已复制原文",Toast.LENGTH_SHORT).show();
    }
}
