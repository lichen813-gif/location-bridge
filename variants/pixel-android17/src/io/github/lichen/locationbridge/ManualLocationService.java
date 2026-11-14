package io.github.lichen.locationbridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.location.provider.ProviderProperties;
import android.os.*;
import java.util.*;

/** Uses Android's public mock-location API; mock flags are never hidden or stripped. */
public final class ManualLocationService extends Service {
    static final String APPLY="MANUAL_APPLY",STOP="MANUAL_STOP",PREFS="manual_position";
    static volatile boolean running;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ManualLocationController controller;
    private boolean finishing,restoreOnExit;
    private int deliveries;
    private final Runnable tick=new Runnable(){public void run(){
        if(finishing)return;
        if(!controller.active()){finishManual("手动位置已停止");return;}
        try {
            LocationManager lm=getSystemService(LocationManager.class);
            if(!lm.isLocationEnabled())throw new IllegalStateException("location disabled");
            controller.tick();deliveries+=2;state("正在提供手动位置（模拟）");
        } catch(Exception e){finishManual("定位权限、模拟位置授权或系统定位开关已变化，手动定位已停止");}
        if(!finishing)handler.postDelayed(this,2000);
    }};
    static boolean pending(Context c){return c.getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("owned",false);}
    static boolean blocksAutomatic(Context c){return running||pending(c);}
    @Override public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("manual_location","手动位置",NotificationManager.IMPORTANCE_LOW));
        LocationManager lm=getSystemService(LocationManager.class);
        controller=new ManualLocationController(new ManualLocationController.Backend(){
            public void add(String provider){
                lm.addTestProvider(provider,new ProviderProperties.Builder().setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW).build());
                lm.setTestProviderEnabled(provider,true);
            }
            public void publish(String provider,ManualPosition p){
                Location l=new Location(provider);l.setLatitude(p.latitude);l.setLongitude(p.longitude);l.setAccuracy(p.accuracy);
                l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());l.setMock(true);
                Bundle b=new Bundle();b.putString("locationBridgeSource","manual_user_position");l.setExtras(b);
                lm.setTestProviderLocation(provider,l);
            }
            public void remove(String provider){lm.removeTestProvider(provider);}
        });
        if(pending(this))controller.recoverOwned();
    }
    private void foreground(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ManualLocationService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(7302,new Notification.Builder(this,"manual_location").setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("手动位置 · 模拟定位").setContentText("正在使用指定坐标；点停止恢复自动定位")
                .setContentIntent(open).addAction(new Notification.Action.Builder(null,"停止",stop).build()).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        restoreOnExit=intent!=null&&STOP.equals(intent.getAction());
        try{foreground();}catch(RuntimeException e){finishManual("无法启动位置服务，请检查定位权限");return START_NOT_STICKY;}
        if(intent==null||!APPLY.equals(intent.getAction())){finishManual("手动位置已停止");return START_NOT_STICKY;}
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                    ||!getSystemService(LocationManager.class).isLocationEnabled())throw new SecurityException();
            AppOpsManager ops=getSystemService(AppOpsManager.class);
            if(ops.unsafeCheckOpNoThrow("android:mock_location",android.os.Process.myUid(),getPackageName())!=AppOpsManager.MODE_ALLOWED){
                finishManual("请在开发者选项中将“选择模拟位置信息应用”设为“定位桥接”");return START_NOT_STICKY;
            }
            ManualPosition point=ManualPosition.parse(intent.getStringExtra("latitude"),intent.getStringExtra("longitude"),intent.getStringExtra("accuracy"),intent.getStringExtra("crs"));
            if(!getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("owned",true).commit())throw new IllegalStateException();
            if(!controller.active()&&controller.cleanupRequired())controller.stop();
            running=true;notifyAutomatic();
            controller.start(point);deliveries+=2;finishing=false;
            state("正在提供手动位置（模拟）");handler.removeCallbacks(tick);handler.postDelayed(tick,2000);
        } catch(Exception e){finishManual("启动失败，请检查定位权限和模拟位置授权");}
        return START_NOT_STICKY; // Never resume a fixed location silently after process/device restart.
    }
    private void state(String status){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("status",status).putInt("deliveries",deliveries).apply();}
    private void notifyAutomatic(){notifyAutomatic(false);}
    private void notifyAutomatic(boolean restore){
        // Both services run on the same main thread. Stop the SDK before posting mock fixes.
        NetworkLocationService.pauseForManual();
        try{startService(new Intent(this,NetworkLocationService.class).setAction(restore?"RESTORE_AUTOMATIC":"MANUAL_CHANGED"));}catch(RuntimeException ignored){}
    }
    private void finishManual(String message){
        handler.removeCallbacks(tick);
        boolean hadOverride=restoreOnExit||pending(this)||running||controller.cleanupRequired();
        try{controller.stop();}catch(Exception ignored){}
        if(controller.cleanupRequired()){
            running=true;state("恢复未完成：请重新选择本应用为模拟位置应用，再点停止");
            handler.postDelayed(tick,5000);return;
        }
        finishing=true;running=false;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("owned",false).commit();
        state(hadOverride?"手动位置已停止，正在等待真实融合定位":message);notifyAutomatic(hadOverride);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(tick);
        try{controller.stop();}catch(Exception ignored){}
        running=false;
        if(!controller.cleanupRequired())getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("owned",false).commit();
        notifyAutomatic();super.onDestroy();
    }
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        out.println("manual_active="+controller.active()+";providers_owned="+controller.cleanupRequired()+";deliveries="+deliveries+";mock=true");
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
