package io.github.lichen.locationbridge;
import java.util.*;

public final class ManualPositionTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("check "+checks);checks++;}
    private static final class Fake implements ManualLocationController.Backend {
        final Set<String> providers=new HashSet<>();int writes,adds;String failAdd,failWrite,failRemove;
        public void add(String p)throws Exception{providers.add(p);adds++;if(p.equals(failAdd))throw new Exception("enable failed after registration");}
        public void publish(String p,ManualPosition v)throws Exception{if(p.equals(failWrite))throw new Exception("publish failed");if(!providers.contains(p))throw new AssertionError();writes++;}
        public void remove(String p)throws Exception{if(p.equals(failRemove))throw new Exception("permission revoked");providers.remove(p);}
    }
    public static void main(String[] args)throws Exception {
        for(String[] input:new String[][]{{"NaN","0","100"},{"0","Infinity","100"},{"91","0","100"},{"0","181","100"},{"0","0","0"},{"0","0","10001"},{"","0","100"}}){
            try{ManualPosition.parse(input[0],input[1],input[2],"wgs84");throw new AssertionError("accepted invalid position");}catch(IllegalArgumentException expected){checks++;}
        }
        ManualPosition p=ManualPosition.parse(" -90 ","180","1","wgs84");check(p.latitude==-90&&p.longitude==180);
        double[] gcj=Coordinates.toGcj02(30.1,110.1);ManualPosition converted=ManualPosition.parse(""+gcj[0],""+gcj[1],"100","gcj02");check(Math.abs(converted.latitude-30.1)<1e-7&&Math.abs(converted.longitude-110.1)<1e-7);
        Fake f=new Fake();ManualLocationController c=new ManualLocationController(f);c.start(p);check(c.active()&&f.providers.size()==2&&f.writes==2);
        c.tick();check(f.writes==4);c.start(converted);check(f.adds==2&&f.writes==6);c.stop();check(!c.active()&&!c.cleanupRequired()&&f.providers.isEmpty());
        f=new Fake();c=new ManualLocationController(f);f.failAdd="network";try{c.start(p);throw new AssertionError();}catch(Exception expected){}check(f.providers.isEmpty()&&!c.active()&&!c.cleanupRequired());
        f=new Fake();c=new ManualLocationController(f);f.failWrite="network";try{c.start(p);throw new AssertionError();}catch(Exception expected){}check(f.providers.isEmpty()&&!c.active());
        f=new Fake();c=new ManualLocationController(f);c.start(p);f.failWrite="gps";f.failRemove="network";try{c.tick();throw new AssertionError();}catch(Exception expected){}check(!c.active()&&c.cleanupRequired()&&f.providers.contains("network"));
        f.failRemove=null;c.stop();check(!c.cleanupRequired()&&f.providers.isEmpty());
        f=new Fake();f.providers.addAll(List.of("gps","network"));c=new ManualLocationController(f);c.recoverOwned();c.stop();check(f.providers.isEmpty()&&!c.cleanupRequired());
        System.out.println("PASS: "+checks+" manual-position validation and provider lifecycle checks; no device positions published");
    }
}
