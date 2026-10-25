package io.github.lichen.locationbridge;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private EditText keyInput;
    private CheckBox consent;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            try {
                JSONObject s=new JSONObject(new String(Files.readAllBytes(new File(getFilesDir(),"status.json").toPath()),StandardCharsets.UTF_8));
                String state=s.optString("status");
                String label=switch(state) {
                    case "fresh_network_fix" -> "已取得网络位置";
                    case "waiting_for_fresh_network_fix" -> "等待新的位置观测";
                    case "requesting" -> "正在请求位置";
                    case "idle" -> "等待系统定位请求";
                    case "configuration_required" -> "请先填写并保存百度定位 AK";
                    case "permission_or_location_disabled" -> "请检查定位开关、权限和使用授权";
                    default -> state;
                };
                long age=s.optLong("last_fix_age_ms",-1);
                status.setText("状态："+label+"\n新定位次数："+s.optInt("accepted")
                        +(age<0?"":"\n上次观测："+age/1000+" 秒前")
                        +"\n精度："+s.optDouble("accuracy_m")+" 米\n定位来源："+("wf".equals(s.optString("source"))?"Wi-Fi":s.optString("source"))
                        +"\n已送入系统："+s.optInt("delivered")+"\n系统已绑定："+s.optBoolean("manager_bound"));
            } catch(Exception e){status.setText("尚未开始测试。请先保存 AK，再允许定位权限。");}
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(20*getResources().getDisplayMetrics().density);
        content.setPadding(padding,padding,padding,padding);
        TextView title=new TextView(this);title.setTextSize(23);title.setText("百度网络定位桥接 · 公开预览版");content.addView(title);
        TextView intro=new TextView(this);intro.setTextSize(16);
        intro.setText("使用百度网络定位，为系统和 Google 服务提供真实位置。"
                +"本版本不内置 AK，请填写你在百度地图开放平台申请的 Android 定位 AK。"
                +"AK 仅保存在本应用的私有设置中，不显示在诊断记录中。\n\n");content.addView(intro);
        keyInput=new EditText(this);keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint("百度 Android 定位 AK（32 位）");keyInput.setText(KeySettings.get(this));content.addView(keyInput);
        consent=new CheckBox(this);consent.setText("同意将 Wi-Fi、基站及位置信息发送给百度以获取定位，并授权后台按需定位。");
        consent.setChecked(getSharedPreferences("bridge",MODE_PRIVATE).getBoolean("consent",false));content.addView(consent);
        Button save=new Button(this);save.setText("保存 AK 和使用授权");
        save.setOnClickListener(v->{
            if(!consent.isChecked()){message("请先阅读并勾选使用授权。");return;}
            if(!KeySettings.valid(KeySettings.normalize(keyInput.getText().toString()))){message("AK 应为 32 位英文字母或数字。");return;}
            if(!KeySettings.save(this,keyInput.getText().toString())){message("保存失败，请检查存储空间。");return;}
            startService(new Intent(this,NetworkLocationService.class).setAction("KEY_CHANGED"));
            message("已保存。请核对定位权限，再开始室内测试。");
        });content.addView(save);
        Button start=new Button(this);start.setText("开始两分钟室内测试");start.setOnClickListener(v->startTest());content.addView(start);
        Button stop=new Button(this);stop.setText("结束手动测试");
        stop.setOnClickListener(v->startService(new Intent(this,NetworkLocationService.class).setAction("TEST_STOP")));content.addView(stop);
        Button revoke=new Button(this);revoke.setText("清除 AK 并停止百度定位");
        revoke.setOnClickListener(v->{
            if(!KeySettings.clear(this)){message("清除失败。");return;}
            keyInput.setText("");consent.setChecked(false);
            startService(new Intent(this,NetworkLocationService.class).setAction("KEY_CHANGED"));
            message("已清除 AK 并停止百度定位。完整停用桥接请按文档操作。");
        });content.addView(revoke);
        status=new TextView(this);status.setTextSize(16);status.setTextIsSelectable(true);content.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
    }
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void startTest() {
        if(!KeySettings.valid(KeySettings.get(this))||!getSharedPreferences("bridge",MODE_PRIVATE).getBoolean("consent",false)){
            message("请先保存 AK 和使用授权。");return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},1);return;
        }
        startForegroundService(new Intent(this,NetworkLocationService.class).setAction("TEST_START"));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==1&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startTest();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
