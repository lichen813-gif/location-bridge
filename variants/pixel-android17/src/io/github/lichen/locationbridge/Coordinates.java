package io.github.lichen.locationbridge;

/** Latitude, longitude. GCJ-02 inverse is iterative rather than a fixed offset. */
public final class Coordinates {
    private static final double A = 6378245.0, EE = .00669342162296594323;
    private Coordinates() {}
    public static double[] toWgs84(double lat, double lon, String type) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180)
            throw new IllegalArgumentException("Invalid coordinates");
        if ("wgs84".equalsIgnoreCase(type)) return new double[]{lat,lon};
        if (!"gcj02".equalsIgnoreCase(type)) throw new IllegalArgumentException("Unknown CRS");
        if (outsideChina(lat,lon)) return new double[]{lat,lon};
        double a=lat,b=lon;
        for (int i=0;i<10;i++) {
            double[] forward=toGcj02(a,b);
            double da=forward[0]-lat,db=forward[1]-lon;
            a-=da;b-=db;
            if (Math.max(Math.abs(da),Math.abs(db))<1e-8) break;
        }
        return new double[]{a,b};
    }
    static double[] toGcj02(double lat,double lon) {
        if (outsideChina(lat,lon)) return new double[]{lat,lon};
        double x=lon-105,y=lat-35;
        double da=-100+2*x+3*y+.2*y*y+.1*x*y+.2*Math.sqrt(Math.abs(x));
        da+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        da+=(20*Math.sin(y*Math.PI)+40*Math.sin(y*Math.PI/3))*2/3;
        da+=(160*Math.sin(y*Math.PI/12)+320*Math.sin(y*Math.PI/30))*2/3;
        double db=300+x+2*y+.1*x*x+.1*x*y+.1*Math.sqrt(Math.abs(x));
        db+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        db+=(20*Math.sin(x*Math.PI)+40*Math.sin(x*Math.PI/3))*2/3;
        db+=(150*Math.sin(x*Math.PI/12)+300*Math.sin(x*Math.PI/30))*2/3;
        double rad=Math.toRadians(lat),magic=1-EE*Math.sin(rad)*Math.sin(rad),sqrt=Math.sqrt(magic);
        da=da*180/((A*(1-EE))/(magic*sqrt)*Math.PI);
        db=db*180/(A/sqrt*Math.cos(rad)*Math.PI);
        return new double[]{lat+da,lon+db};
    }
    private static boolean outsideChina(double a,double b) {
        return b<72.004||b>137.8347||a<.8293||a>55.8271;
    }
}
