package io.github.lichen.locationbridge;

import java.util.*;

/** Transactional test-provider lifecycle. Backend enables real-device and failure-path tests. */
final class ManualLocationController {
    interface Backend {
        void add(String provider) throws Exception;
        void publish(String provider,ManualPosition position) throws Exception;
        void remove(String provider) throws Exception;
    }
    private final Backend backend;
    private final Set<String> owned=new LinkedHashSet<>();
    private ManualPosition position;
    ManualLocationController(Backend backend){this.backend=backend;}
    void recoverOwned(){owned.add("gps");owned.add("network");}
    void start(ManualPosition next) throws Exception {
        if(next==null)throw new IllegalArgumentException("Missing position");
        try {
            for(String provider:new String[]{"gps","network"}) {
                if(!owned.contains(provider)){owned.add(provider);backend.add(provider);}
            }
            // Both providers must publish successfully before reporting the mode as active.
            for(String provider:owned)backend.publish(provider,next);
            position=next;
        } catch(Exception e){position=null;try{stop();}catch(Exception cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    void tick() throws Exception {
        if(position==null)return;
        try {for(String provider:owned)backend.publish(provider,position);}
        catch(Exception e){position=null;try{stop();}catch(Exception cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    void stop() throws Exception {
        position=null;Exception failure=null;
        for(String provider:new ArrayList<>(owned)) {
            try{backend.remove(provider);owned.remove(provider);}catch(Exception e){failure=e;}
        }
        if(failure!=null)throw failure;
    }
    boolean active(){return position!=null;}
    boolean cleanupRequired(){return !owned.isEmpty();}
}
