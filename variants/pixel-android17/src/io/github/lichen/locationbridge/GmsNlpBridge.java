package io.github.lichen.locationbridge;

import android.content.Context;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

/** Reviewed-core supplemental real network observations for the stock GMS NLP callback. */
final class GmsNlpBridge {
    static final String SWITCH="lichen_location_bridge_gms_enabled";
    private static final String TAG="GmsLocationBridge";
    private final XposedInterface api;
    private final Map<Object,Session> sessions=new IdentityHashMap<>();
    private Field contextField,dispatcherField,callbackField,handlerField,receiverOwnerField,engineRunningField;
    private Method enqueue,reportLocation,modernReports;
    private Constructor<?> reportConstructor;
    private Class<?> reportClass,engineClass;

    GmsNlpBridge(XposedInterface api) { this.api=api; }

    void install(ClassLoader loader,String apk) {
        try {
            GmsCompatibility.Profile profile=GmsCompatibility.verify(apk,loader);
            Class<?> helper=Class.forName(profile.helper(),false,loader);
            Class<?> dispatcher=Class.forName(profile.dispatcher(),false,loader);
            Class<?> receiver=Class.forName(profile.receiver(),false,loader);
            engineClass=Class.forName(profile.engine(),false,loader);
            reportClass=Class.forName("com.google.android.location.internal.server.NlpReport",false,loader);
            Class<?> wifiScan=Class.forName("com.google.android.gms.location.WifiScan",false,loader);
            contextField=field(helper,"h",Context.class);
            dispatcherField=field(helper,"f",dispatcher);
            callbackField=helper.getDeclaredField("l");callbackField.setAccessible(true);
            handlerField=field(dispatcher,"a",Handler.class);
            receiverOwnerField=field(receiver,"a",helper);
            engineRunningField=field(engineClass,"j",boolean.class);
            enqueue=dispatcher.getDeclaredMethod("b",int.class,Object.class);enqueue.setAccessible(true);
            reportLocation=reportClass.getDeclaredMethod("a");
            if(reportLocation.getReturnType()!=Location.class)throw new NoSuchMethodException("report result");
            reportConstructor=reportClass.getDeclaredConstructor(Location.class,Location.class,int.class,wifiScan,byte[].class);
            modernReports=Class.forName(profile.selector(),false,loader).getDeclaredMethod(profile.flag());
            if(modernReports.getReturnType()!=boolean.class||!Modifier.isStatic(modernReports.getModifiers()))
                throw new NoSuchMethodException("report selector");
            Method request=helper.getDeclaredMethod("a",long.class,long.class);
            Method stop=helper.getDeclaredMethod("b");
            Method delivery=receiver.getDeclaredMethod("handleMessage",Message.class);
            if(request.getReturnType()!=void.class||stop.getReturnType()!=void.class
                    ||delivery.getReturnType()!=void.class||enqueue.getReturnType()!=void.class
                    ||!Handler.class.isAssignableFrom(receiver)
                    ||!callbackField.getType().isAssignableFrom(engineClass))
                throw new NoSuchMethodException("NLP layout");
            // Verify the exact report constructor/accessor used below before installing any hooks.
            Location check=new Location("network");
            Object report=reportConstructor.newInstance(check,null,1,null,null);
            if(reportLocation.invoke(report)!=check)throw new IllegalStateException("NLP report contract");
            api.hook(request).setId("baidu-real-network-gms-demand").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain->{
                Object result=chain.proceed();
                try { session(chain.getThisObject()).request((Long)chain.getArg(0)); }
                catch(Exception|LinkageError e){failure("REQUEST",e);}
                return result;
            });
            api.hook(stop).setId("baidu-real-network-gms-stop").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain->{
                Object result=chain.proceed();
                synchronized(sessions) {
                    Session s=sessions.get(chain.getThisObject());if(s!=null)s.cancel();
                }
                return result;
            });
            api.hook(delivery).setId("baidu-real-network-gms-callback-observer").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain->{
                Object result=chain.proceed(); // original callback always runs unchanged
                try {
                    Session s;
                    synchronized(sessions){s=sessions.get(receiverOwnerField.get(chain.getThisObject()));}
                    if(s!=null)s.observed((Message)chain.getArg(0));
                } catch(Exception|LinkageError e){failure("CALLBACK_OBSERVATION",e);}
                return result;
            });
            log("HOOKS_READY; reviewed_core="+profile.version()+"; original requests and GPS retained");
        } catch(Exception|LinkageError e) { failure("INSTALL_UNAVAILABLE",e); }
    }

    private static Field field(Class<?> owner,String name,Class<?> type) throws Exception {
        Field f=owner.getDeclaredField(name);if(f.getType()!=type)throw new NoSuchFieldException(name);
        f.setAccessible(true);return f;
    }
    private Session session(Object owner) throws Exception {
        synchronized(sessions) {
            Session s=sessions.get(owner);
            if(s==null){s=new Session(owner);sessions.put(owner,s);}return s;
        }
    }
    private final class Session implements LocationListener {
        final Object owner,dispatcher;
        final Context context;
        final Handler handler;
        final LocationManager locations;
        long requested=Long.MAX_VALUE,registeredInterval=-1,lastForwarded,googleElapsed;
        long probeStarted;
        CancellationSignal probe;
        float googleAccuracy=Float.MAX_VALUE;
        String registeredProvider="";
        boolean active;
        int forwarded;
        Session(Object owner) throws Exception {
            this.owner=owner;context=(Context)contextField.get(owner);
            dispatcher=dispatcherField.get(owner);
            handler=(Handler)handlerField.get(dispatcher);
            locations=context.getSystemService(LocationManager.class);
            ContentObserver observer=new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange){configure();}
            };
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(SWITCH),false,observer);
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(BridgeModule.ENABLE_SETTING),false,observer);
        }
        void request(long interval) {
            handler.post(()->{active=true;requested=interval;configure();requestFresh();});
        }
        void cancel() { handler.post(()->{active=false;detach();log("REQUEST_STOPPED");}); }
        boolean enabled() {
            return Settings.Global.getInt(context.getContentResolver(),SWITCH,0)==1
                    &&Settings.Global.getInt(context.getContentResolver(),BridgeModule.ENABLE_SETTING,0)==1;
        }
        void configure() {
            try {
                if(!active||!enabled()){detach();return;}
                boolean passive=requested==Long.MAX_VALUE;
                String provider=passive?LocationManager.PASSIVE_PROVIDER:LocationManager.NETWORK_PROVIDER;
                long period=passive?0:Math.max(10_000,requested);
                if(provider.equals(registeredProvider)&&registeredInterval==period)return;
                detach();
                LocationRequest request=new LocationRequest.Builder(period)
                        .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                        .setMinUpdateIntervalMillis(0).setMinUpdateDistanceMeters(0).build();
                locations.requestLocationUpdates(provider,request,handler::post,this);
                registeredProvider=provider;registeredInterval=period;
                log("REGISTERED provider="+provider+";interval_ms="+period);
                requestFresh();
            } catch(RuntimeException e){failure("REGISTER_FAILED",e);detach();}
        }
        void requestFresh() {
            long now=SystemClock.elapsedRealtimeNanos();
            if(!active||!enabled()||requested==Long.MAX_VALUE||probe!=null
                    ||(lastForwarded>0&&now-lastForwarded<30_000_000_000L)
                    ||SystemClock.elapsedRealtime()-probeStarted<10_000)return;
            try {
                CancellationSignal signal=new CancellationSignal();probe=signal;
                probeStarted=SystemClock.elapsedRealtime();
                locations.getCurrentLocation(LocationManager.NETWORK_PROVIDER,signal,handler::post,l->{
                    if(probe==signal)probe=null;
                    onLocationChanged(l);
                });
                log("FRESH_REQUEST; interval_ms="+requested);
            } catch(RuntimeException e){probe=null;failure("FRESH_REQUEST_FAILED",e);}
        }
        void detach() {
            if(probe!=null){probe.cancel();probe=null;}
            if(registeredProvider.isEmpty())return;
            try{locations.removeUpdates(this);}catch(RuntimeException ignored){}
            registeredProvider="";registeredInterval=-1;log("NETWORK_LISTENER_REMOVED");
        }
        @Override public void onLocationChanged(Location original) {
            if(!active||!enabled()||!valid(original)||!ours(original))return;
            try {
                long now=SystemClock.elapsedRealtimeNanos(),elapsed=original.getElapsedRealtimeNanos();
                if(elapsed<=lastForwarded)return;
                if(googleElapsed>0&&now-googleElapsed<=30_000_000_000L&&googleAccuracy<=original.getAccuracy())return;
                Object engine=callbackField.get(owner);
                if(!engineClass.isInstance(engine)||!engineRunningField.getBoolean(engine)) {
                    log("ENGINE_NOT_READY; real observation withheld");return;
                }
                Location copy=copyForGms(original);
                if(copy==null){log("UNSUPPORTED_NETWORK_SOURCE");return;}
                boolean reports=(Boolean)modernReports.invoke(null);
                ArrayList<Object> payload=new ArrayList<>();
                if(reports) {
                    String type=copy.getExtras().getString("networkLocationType","");
                    if("wifi".equals(type))payload.add(reportConstructor.newInstance(copy,null,1,null,null));
                    else if("cell".equals(type))payload.add(reportConstructor.newInstance(null,copy,2,null,null));
                    else {log("UNSUPPORTED_NETWORK_SOURCE");return;}
                } else payload.add(copy);
                enqueue.invoke(dispatcher,reports?3:1,payload);
                lastForwarded=elapsed;forwarded++;
                log("REAL_FIX_QUEUED format="+(reports?"NlpReport":"Location")+";fix_elapsed_ms="+elapsed/1_000_000
                        +";age_ms="+(now-elapsed)/1_000_000+";accuracy_m="+copy.getAccuracy()+";count="+forwarded);
            } catch(Exception|LinkageError e){failure("FORWARD_FAILED",e);}
        }
        void observed(Message message) throws Exception {
            if((message.what!=1&&message.what!=3)||!(message.obj instanceof List<?>))return;
            for(Object item:(List<?>)message.obj) {
                Location l=item instanceof Location?(Location)item:
                        reportClass.isInstance(item)?(Location)reportLocation.invoke(item):null;
                if(!valid(l))continue;
                if(ours(l)) {
                    log("ORIGINAL_CALLBACK_RETURNED fix_elapsed_ms="+l.getElapsedRealtimeNanos()/1_000_000);
                } else {googleElapsed=l.getElapsedRealtimeNanos();googleAccuracy=l.getAccuracy();}
            }
        }
    }
    private static boolean ours(Location l) {
        Bundle extras=l.getExtras();
        return "network".equals(l.getProvider())&&extras!=null
                &&"baidu_network_9.7.0".equals(extras.getString("locationBridgeSource"));
    }
    static Location copyForGms(Location original) {
        Bundle source=original.getExtras();if(source==null)return null;
        String type=source.getString("networkLocationType","");
        String canonical;
        if("wf".equals(type)||"wifi".equals(type))canonical="wifi";
        else if("cl".equals(type)||"cell".equals(type))canonical="cell";
        else return null;
        Location copy=new Location(original);Bundle extras=new Bundle(source);
        extras.putString("networkLocationType",canonical);copy.setExtras(extras);
        return copy; // all measured fields, including both clocks, remain unchanged
    }
    static boolean valid(Location l) {
        if(l==null||l.isMock()||!l.hasAccuracy()||!Float.isFinite(l.getAccuracy())||l.getAccuracy()<=0||l.getAccuracy()>20_000)return false;
        if(!Double.isFinite(l.getLatitude())||Math.abs(l.getLatitude())>90
                ||!Double.isFinite(l.getLongitude())||Math.abs(l.getLongitude())>180)return false;
        long age=SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos();
        long wall=System.currentTimeMillis()-l.getTime();
        return l.getElapsedRealtimeNanos()>0&&age>=0&&age<=30_000_000_000L&&wall>=-5000&&wall<=30_000;
    }
    private void log(String s){api.log(Log.INFO,TAG,s);}
    private void failure(String stage,Throwable e) {
        Throwable cause=e instanceof InvocationTargetException&&e.getCause()!=null?e.getCause():e;
        log(stage+" "+cause.getClass().getSimpleName()); // no coordinates, account data or SDK key
    }
}
