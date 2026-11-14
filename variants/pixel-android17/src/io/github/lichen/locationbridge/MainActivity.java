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
    private EditText latitudeInput,longitudeInput,accuracyInput;
    private Spinner coordinateSystem;
    private TextView manualStatus;
    private boolean pendingManualPermission;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(manualStatus!=null) {
                android.content.SharedPreferences prefs=getSharedPreferences(ManualLocationService.PREFS,MODE_PRIVATE);
                String text=prefs.getString("status","未启用手动位置");
                if(ManualLocationService.pending(MainActivity.this)&&!ManualLocationService.running)text="上次手动定位中断，请点停止清理后恢复自动定位";
                manualStatus.setText(text+"\n已发布："+prefs.getInt("deliveries",0)+" 条（模拟）");
            }
            try {
                JSONObject s=new JSONObject(new String(Files.readAllBytes(new File(getFilesDir(),"status.json").toPath()),StandardCharsets.UTF_8));
                String state=s.optString("status");
                String label=switch(state) {
                    case "fresh_network_fix" -> "已取得网络位置";
                    case "waiting_for_fresh_network_fix" -> "等待新的位置观测";
                    case "requesting" -> "正在请求位置";
                    case "manual_position_active" -> "手动位置模式，已暂停百度自动定位";
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
        TextView title=new TextView(this);title.setTextSize(23);title.setText("定位桥接 · 自动 / 手动位置");content.addView(title);
        addManualControls(content);
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
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });setContentView(scroll);
    }
    private void addManualControls(LinearLayout content) {
        TextView intro=new TextView(this);intro.setText("手动位置用于系统 GPS / 网络定位，保留模拟位置标记。无需百度 AK；首次需在开发者选项的“选择模拟位置信息应用”中选择“定位桥接”。停止后恢复原定位提供者。不会开机自动启用。");content.addView(intro);
        android.content.SharedPreferences prefs=getSharedPreferences(ManualLocationService.PREFS,MODE_PRIVATE);
        latitudeInput=coordinateInput(content,"纬度（-90 至 90）",prefs.getString("latitude",""));
        longitudeInput=coordinateInput(content,"经度（-180 至 180）",prefs.getString("longitude",""));
        accuracyInput=coordinateInput(content,"位置精度（米，1 至 10000）",prefs.getString("accuracy","100"));
        coordinateSystem=new Spinner(this);coordinateSystem.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"WGS84（GPS / Google 坐标）","GCJ-02（高德 / 腾讯坐标）"}));
        coordinateSystem.setSelection(prefs.getInt("crs",0));content.addView(coordinateSystem);
        Button settings=new Button(this);settings.setText("打开开发者选项，选择模拟位置应用");
        settings.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(RuntimeException e){message("请在系统设置中打开开发者选项，选择模拟位置应用。");}});content.addView(settings);
        Button start=new Button(this);start.setText("使用这个位置（模拟定位）");start.setOnClickListener(v->startManual());content.addView(start);
        Button stop=new Button(this);stop.setText("停止手动位置，恢复自动定位");stop.setOnClickListener(v->startService(new Intent(this,ManualLocationService.class).setAction(ManualLocationService.STOP)));content.addView(stop);
        manualStatus=new TextView(this);manualStatus.setTextSize(16);content.addView(manualStatus);
        TextView automatic=new TextView(this);automatic.setTextSize(20);automatic.setText("\n百度自动定位");content.addView(automatic);
    }
    private EditText coordinateInput(LinearLayout content,String hint,String value) {
        TextView label=new TextView(this);label.setText(hint);content.addView(label);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(hint);input.setContentDescription(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(value);content.addView(input);return input;
    }
    private void startManual() {
        String lat=latitudeInput.getText().toString(),lon=longitudeInput.getText().toString(),accuracy=accuracyInput.getText().toString();
        String crs=coordinateSystem.getSelectedItemPosition()==0?"wgs84":"gcj02";
        try{ManualPosition.parse(lat,lon,accuracy,crs);}catch(IllegalArgumentException e){message("请填写有效的经纬度和 1 至 10000 米精度。");return;}
        if(!getSystemService(android.location.LocationManager.class).isLocationEnabled()){message("请先打开系统定位开关。");return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            pendingManualPermission=true;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},2);return;
        }
        if(!getSharedPreferences(ManualLocationService.PREFS,MODE_PRIVATE).edit().putString("latitude",lat).putString("longitude",lon).putString("accuracy",accuracy).putInt("crs",coordinateSystem.getSelectedItemPosition()).commit()){message("保存失败。");return;}
        startForegroundService(new Intent(this,ManualLocationService.class).setAction(ManualLocationService.APPLY)
                .putExtra("latitude",lat).putExtra("longitude",lon).putExtra("accuracy",accuracy).putExtra("crs",crs));
    }
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void startTest() {
        if(ManualLocationService.blocksAutomatic(this)){message("请先停止手动位置，再测试自动定位。");return;}
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
        if(request==2&&pendingManualPermission){pendingManualPermission=false;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startManual();return;}
        if(request==1&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startTest();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
