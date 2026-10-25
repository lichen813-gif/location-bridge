package io.github.lichen.locationbridge;
import android.content.Context;
/** Runtime configuration only. Release APKs contain no developer API key. */
public final class KeySettings {
    private KeySettings() {}
    public static String get(Context c) {return c.getSharedPreferences("bridge",Context.MODE_PRIVATE).getString("baidu_api_key","");}
    public static String normalize(String value) {return value==null?"":value.trim();}
    public static boolean valid(String value) {return value!=null&&value.matches("[A-Za-z0-9]{32}");}
    public static boolean clear(Context c) {return c.getSharedPreferences("bridge",Context.MODE_PRIVATE).edit().remove("baidu_api_key").putBoolean("consent",false).commit();}
    public static boolean save(Context c,String value) {
        String key=normalize(value);
        if(!valid(key))throw new IllegalArgumentException("Invalid API key format");
        return c.getSharedPreferences("bridge",Context.MODE_PRIVATE).edit().putString("baidu_api_key",key).putBoolean("consent",true).commit();
    }
}
