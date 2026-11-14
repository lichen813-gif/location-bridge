package io.github.lichen.locationbridge;
import android.location.Location;
public final class RestorationPolicyTestMain {
    public static void main(String[] args) {
        long now=100_000_000_000L,started=now-1_000_000_000L;int count=0;
        Location fix=new Location("fused");fix.setAccuracy(45);fix.setElapsedRealtimeNanos(now);
        if(!NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("new real fix");count++;
        fix.setMock(true);if(NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("mock accepted");count++;fix.setMock(false);
        fix.setElapsedRealtimeNanos(started-1);if(NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("pre-stop cache accepted");count++;
        fix.setElapsedRealtimeNanos(now+1);if(NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("future accepted");count++;
        fix.setElapsedRealtimeNanos(now-31_000_000_000L);if(NetworkLocationService.restoredFix(fix,1,now))throw new AssertionError("stale accepted");count++;
        fix.setElapsedRealtimeNanos(now);fix.setAccuracy(Float.NaN);if(NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("invalid accuracy");count++;
        fix.removeAccuracy();if(NetworkLocationService.restoredFix(fix,started,now))throw new AssertionError("missing accuracy");count++;
        if(NetworkLocationService.restoredFix(null,started,now))throw new AssertionError("null accepted");count++;
        fix.setAccuracy(45);if(NetworkLocationService.restoredFix(fix,0,now))throw new AssertionError("inactive recovery");count++;
        System.out.println("PASS: "+count+" restoration policy checks; mock/stale/pre-stop caches rejected; no positions published");
    }
}
