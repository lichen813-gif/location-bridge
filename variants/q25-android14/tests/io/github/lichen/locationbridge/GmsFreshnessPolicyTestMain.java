package io.github.lichen.locationbridge;

import android.location.Location;
import android.os.SystemClock;
import android.os.Bundle;

/** In-memory input validation only. Never registers or publishes a location. */
public final class GmsFreshnessPolicyTestMain {
    static Location fresh() {
        Location l=new Location("network");l.setLatitude(30);l.setLongitude(110);l.setAccuracy(45);
        l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return l;
    }
    static void check(boolean expected,Location l,String name) {
        if(GmsNlpBridge.valid(l)!=expected)throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(true,fresh(),"fresh real observation");
        check(false,null,"missing observation");
        Location l=fresh();l.setTime(l.getTime()-31_000);check(false,l,"stale wall clock");
        l=fresh();l.setElapsedRealtimeNanos(l.getElapsedRealtimeNanos()-31_000_000_000L);check(false,l,"stale monotonic clock");
        l=fresh();l.setElapsedRealtimeNanos(l.getElapsedRealtimeNanos()+1_000_000_000L);check(false,l,"future monotonic clock");
        l=fresh();l.removeAccuracy();check(false,l,"missing accuracy");
        l=fresh();l.setAccuracy(Float.NaN);check(false,l,"invalid accuracy");
        l=fresh();l.setLatitude(91);check(false,l,"latitude out of bounds");
        l=fresh();l.setLongitude(Double.NaN);check(false,l,"invalid longitude");
        l=fresh();Bundle extras=new Bundle();extras.putString("networkLocationType","wf");l.setExtras(extras);
        Location copy=GmsNlpBridge.copyForGms(l);
        if(!"wifi".equals(copy.getExtras().getString("networkLocationType"))
                ||!"wf".equals(l.getExtras().getString("networkLocationType"))
                ||copy.getTime()!=l.getTime()||copy.getElapsedRealtimeNanos()!=l.getElapsedRealtimeNanos()
                ||copy.getLatitude()!=l.getLatitude()||copy.getLongitude()!=l.getLongitude()
                ||copy.getAccuracy()!=l.getAccuracy()||!copy.getProvider().equals(l.getProvider()))
            throw new AssertionError("Wi-Fi translation changed a measured field");
        l=fresh();extras=new Bundle();extras.putString("networkLocationType","unknown");l.setExtras(extras);
        if(GmsNlpBridge.copyForGms(l)!=null)throw new AssertionError("Unknown source accepted");
        System.out.println("PASS: 11 freshness/format cases; clocks and coordinates preserved; no location published");
    }
}
