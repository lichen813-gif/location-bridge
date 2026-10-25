package io.github.lichen.locationbridge;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.*;
import android.util.Log;
import com.baidu.location.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Real network positioning only. No mock providers, fake timestamps or GNSS injection. */
public final class NetworkLocationService extends Service {
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationClient client;
    private IBinder manager;
    private long requestedInterval=Long.MAX_VALUE,manualUntil,sessionStart,lastTimestamp,lastFixElapsed;
    private int accepted,rejected,lastCode,delivered;
    private String status="idle",source="",coordinateType="";
    private float accuracy;
    private boolean foreground,sessionAccepted;
    private long lastRequestElapsed;
    private int sdkGeneration;
    private final Runnable tick=new Runnable() {
        @Override public void run() { updateDemand();writeStatus();main.postDelayed(this,5000); }
    };

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                "location","网络定位桥接",NotificationManager.IMPORTANCE_LOW));
        main.post(tick);event("SERVICE_CREATED","sdk="+Build.VERSION.SDK_INT);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"TEST_START".equals(intent.getAction())) {
            manualUntil=SystemClock.elapsedRealtime()+120_000;
            getSharedPreferences("bridge",MODE_PRIVATE).edit().putBoolean("consent",true).apply();
        } else if(intent!=null&&"TEST_STOP".equals(intent.getAction())) manualUntil=0;
        else if(intent!=null&&"KEY_CHANGED".equals(intent.getAction())) {
            stopClient();lastRequestElapsed=0;manualUntil=0;
            if(!getSharedPreferences("bridge",MODE_PRIVATE).getBoolean("consent",false))
                LocationClient.setAgreePrivacy(false);
        }
        updateDemand();if(manager!=null)callManager(2,null);return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { event("PROVIDER_BOUND","");return provider; }
    @Override public boolean onUnbind(Intent intent) {
        manager=null;requestedInterval=Long.MAX_VALUE;updateDemand();return false;
    }

    private final Binder provider=new Binder() {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
            if(code==INTERFACE_TRANSACTION) { if(reply!=null)reply.writeString(ProviderWire.PROVIDER);return true; }
            if(Binder.getCallingUid()!=1000||Build.VERSION.SDK_INT!=37)
                throw new SecurityException("Only Android 17 system_server may control provider");
            data.enforceInterface(ProviderWire.PROVIDER);
            if(code==FIRST_CALL_TRANSACTION) {
                IBinder m=data.readStrongBinder();data.enforceNoDataAvail();
                main.post(()->{manager=m;callManager(1,null);event("PROVIDER_INITIALIZED","");updateDemand();});
            } else if(code==FIRST_CALL_TRANSACTION+1) {
                long interval=ProviderWire.readRequestInterval(data);
                main.post(()->{requestedInterval=interval;event("PROVIDER_REQUEST",interval==Long.MAX_VALUE?"off":"on");updateDemand();});
            } else if(code==FIRST_CALL_TRANSACTION+2) {
                data.enforceNoDataAvail();main.post(()->callManager(6,null));
            } else if(code==FIRST_CALL_TRANSACTION+3) {
                data.readString();data.readTypedObject(Bundle.CREATOR);data.enforceNoDataAvail();
            } else return super.onTransact(code,data,reply,flags);
            return true;
        }
    };

    private boolean allowed() {
        return Build.VERSION.SDK_INT==37&&getSharedPreferences("bridge",MODE_PRIVATE).getBoolean("consent",false)
                &&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                &&getSystemService(LocationManager.class).isLocationEnabled();
    }
    private long requestPeriod() {
        if(manualUntil>SystemClock.elapsedRealtime())return 10_000;
        return Math.max(10_000,requestedInterval);
    }
    private void updateDemand() {
        boolean demand=manualUntil>SystemClock.elapsedRealtime()||(manager!=null&&requestedInterval!=Long.MAX_VALUE);
        if(!demand||!allowed()) { stopClient();lastRequestElapsed=0;status=demand?"permission_or_location_disabled":"idle";return; }
        if(client!=null&&sessionAccepted&&requestPeriod()>=60_000){stopClient();return;}
        if(lastRequestElapsed>0&&SystemClock.elapsedRealtime()-lastRequestElapsed<requestPeriod())return;
        if(client!=null&&!sessionAccepted&&SystemClock.elapsedRealtime()-lastRequestElapsed<25_000)return;
        if(client!=null) {
            // The SDK's manual request path can repeat the previous observation.
            // A new SDK session obtains a new observation without changing its clocks.
            stopSdk();event("SDK_SESSION_REFRESH","framework_demand");
        }
        try {
            String key=KeySettings.get(this);
            if(!KeySettings.valid(key)){status="configuration_required";stopClient();return;}
            ensureForeground();LocationClient.setAgreePrivacy(true);LocationClient.setKey(key);
            LocationClientOption o=new LocationClientOption();
            o.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);o.setOpenGnss(false);
            // SDK 9.7 clamps zero to one second. The bridge schedules fresh sessions.
            o.setCoorType("gcj02");o.setScanSpan(Integer.MAX_VALUE);o.setWifiCacheTimeOut(0);
            o.disableCache(true);o.setEnableCache(false);
            o.setEnableSystemNetworkFallback(false); // a provider must never fall back into itself
            o.setIgnoreKillProcess(true);o.setIsNeedAddress(false);o.setTimeOut(20_000);
            client=new LocationClient(getApplicationContext(),o);
            final int generation=++sdkGeneration;
            client.registerLocationListener(new BDAbstractLocationListener() {
                @Override public void onReceiveLocation(BDLocation fix) {
                    main.post(()->{if(generation==sdkGeneration)receive(fix);});
                }
                @Override public void onLocDiagnosticMessage(int type,int diagnostic,String message) {
                    event("SDK_DIAGNOSTIC","code="+type+";diagnostic="+diagnostic);
                }
            });
            sessionAccepted=false;sessionStart=System.currentTimeMillis();client.start();lastRequestElapsed=SystemClock.elapsedRealtime();status="requesting";
            event("SDK_STARTED","network_only;cache_disabled");
        } catch(Exception|LinkageError e) {
            status="sdk_start_failed_"+e.getClass().getSimpleName();event("SDK_START_FAILED",e.getClass().getSimpleName());stopClient();
        }
        writeStatus();
    }
    private void ensureForeground() {
        if(foreground)return;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"location").setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("百度网络定位桥接").setContentText("正在使用 Wi-Fi 和基站获取真实位置")
                .setContentIntent(open).setOngoing(true).build();
        startForeground(7301,n);foreground=true;
    }
    private void stopClient() {
        stopSdk();
        if(foreground) { stopForeground(STOP_FOREGROUND_REMOVE);foreground=false; }
    }
    private void stopSdk() {
        if(client!=null) {
            sdkGeneration++;try{client.stop();}catch(RuntimeException ignored){}
            client=null;event("SDK_STOPPED","");
        }
    }
    private long timestamp(BDLocation fix) throws Exception {
        long t=fix.getTimeStamp();
        if(t>1_000_000_000L&&t<10_000_000_000L)t*=1000;
        if(t>=1_000_000_000_000L)return t;
        SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT);
        parser.setLenient(false);parser.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        Date value=parser.parse(fix.getTime());if(value==null)throw new IllegalArgumentException("Missing time");
        return value.getTime();
    }
    private void receive(BDLocation fix) {
        if(client==null||!allowed()||fix==null)return;
        lastCode=fix.getLocType();coordinateType=fix.getCoorType();source=fix.getNetworkLocationType();
        event("SDK_CALLBACK","code="+lastCode+";crs="+coordinateType+";source="+source);
        try {
            if(lastCode!=BDLocation.TypeNetWorkLocation&&lastCode!=BDLocation.TypeCoarseLocation)
                throw new IllegalArgumentException("Not a fresh network result");
            long observation=timestamp(fix),age=System.currentTimeMillis()-observation;
            event("OBSERVATION_METADATA","timestamp="+observation+";previous="+lastTimestamp
                    +";age_ms="+age+";text_time="+fix.getTime());
            if(observation<sessionStart-5000||age< -5000||age>30_000||observation<=lastTimestamp)
                throw new IllegalArgumentException("Stale observation");
            float r=fix.getRadius();if(!Float.isFinite(r)||r<=0||r>20_000)throw new IllegalArgumentException("Invalid accuracy");
            double[] wgs=Coordinates.toWgs84(fix.getLatitude(),fix.getLongitude(),coordinateType);
            Location location=new Location("network");location.setLatitude(wgs[0]);location.setLongitude(wgs[1]);
            location.setAccuracy(Math.max(10,r+5));location.setTime(observation);
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-Math.max(0,age)*1_000_000L);
            Bundle extras=new Bundle();extras.putString("networkLocationType",source);
            extras.putString("locationBridgeSource","baidu_network_9.7.0");location.setExtras(extras);
            lastTimestamp=observation;lastFixElapsed=location.getElapsedRealtimeNanos()/1_000_000;
            accuracy=location.getAccuracy();accepted++;sessionAccepted=true;status="fresh_network_fix";
            JSONObject stored=new JSONObject();stored.put("latitude",wgs[0]);stored.put("longitude",wgs[1]);
            stored.put("accuracy",accuracy);stored.put("time",observation);stored.put("elapsed_ms",lastFixElapsed);
            stored.put("source",source);writeFile("latest-fix.json",stored.toString());
            if(manager!=null&&requestedInterval!=Long.MAX_VALUE&&callManager(4,location))delivered++;
            event("FRESH_FIX","accuracy_m="+accuracy+";age_ms="+Math.max(0,age)+";delivered="+delivered);
            if(requestPeriod()>=60_000)stopClient();
        } catch(Exception e) {
            rejected++;status=accepted>0&&lastCode==161?"waiting_for_fresh_network_fix":"rejected_code_"+lastCode;
            event("RESULT_REJECTED","code="+lastCode+";reason="+e.getClass().getSimpleName());
        }
        writeStatus();
    }
    private boolean callManager(int transaction,Location fix) {
        IBinder current=manager;if(current==null)return false;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(ProviderWire.MANAGER);
            if(transaction==1) {
                data.writeBoolean(allowed());
                ProviderProperties props=new ProviderProperties.Builder().setHasNetworkRequirement(true)
                        .setHasSatelliteRequirement(false).setHasCellRequirement(false).setHasMonetaryCost(false)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW).setAccuracy(ProviderProperties.ACCURACY_COARSE).build();
                data.writeTypedObject(props,0);data.writeString(null);
            } else if(transaction==2)data.writeBoolean(allowed());
            else if(transaction==4)data.writeTypedObject(fix,0);
            if(!current.transact(transaction,data,reply,0))return false;
            reply.readException();return true;
        } catch(RemoteException|RuntimeException e) { event("MANAGER_CALL_FAILED",e.getClass().getSimpleName());return false; }
        finally { data.recycle();reply.recycle(); }
    }
    private JSONObject statusJson() throws Exception {
        JSONObject s=new JSONObject();s.put("status",status);s.put("sdk_version","9.7.0");
        s.put("accepted",accepted);s.put("rejected",rejected);s.put("last_code",lastCode);s.put("source",source);
        s.put("source_crs",coordinateType);s.put("accuracy_m",accuracy);s.put("last_fix_elapsed_ms",lastFixElapsed);
        s.put("delivered",delivered);s.put("manager_bound",manager!=null);s.put("request_active",requestedInterval!=Long.MAX_VALUE);
        s.put("provider_interval_ms",requestedInterval);s.put("manual_test_active",manualUntil>SystemClock.elapsedRealtime());
        s.put("effective_request_interval_ms",requestPeriod());
        s.put("last_fix_age_ms",lastFixElapsed>0?Math.max(0,SystemClock.elapsedRealtime()-lastFixElapsed):-1);
        s.put("elapsed_ms",SystemClock.elapsedRealtime());return s;
    }
    private void writeStatus() { try{writeFile("status.json",statusJson().toString());}catch(Exception ignored){} }
    private void writeFile(String name,String text) throws Exception {
        File tmp=new File(getFilesDir(),name+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));}
        if(!tmp.renameTo(new File(getFilesDir(),name)))throw new IOException("Rename failed");
    }
    private void event(String name,String detail) {
        Log.i("LocationBridge",name+(detail.isEmpty()?"":" "+detail)); // no coordinates, AP IDs or AK
        try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"events.jsonl"),true)) {
            JSONObject row=new JSONObject();row.put("elapsed_ms",SystemClock.elapsedRealtime());
            row.put("event",name);row.put("detail",detail);
            out.write((row.toString()+"\n").getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored){}
    }
    @Override protected void dump(FileDescriptor fd,PrintWriter writer,String[] args) {
        try{writer.println(statusJson());}catch(Exception e){writer.println("status_unavailable");}
    }
    @Override public void onDestroy() { main.removeCallbacks(tick);stopClient();super.onDestroy(); }
}
