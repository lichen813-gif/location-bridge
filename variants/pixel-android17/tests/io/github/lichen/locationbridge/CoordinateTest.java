package io.github.lichen.locationbridge;
public final class CoordinateTest {
    public static void main(String[] args) {
        double[] w=Coordinates.toWgs84(39.91022649807321,116.4037135824225,"gcj02");
        if(Math.abs(w[0]-39.908823)>1e-6||Math.abs(w[1]-116.397470)>1e-6)throw new AssertionError("Reference point");
        for(double[] p:new double[][]{{22.5431,114.0579},{30.5728,104.0668},{31.2304,121.4737},{45.75,126.63}}) {
            double[] g=Coordinates.toGcj02(p[0],p[1]);double[] v=Coordinates.toWgs84(g[0],g[1],"gcj02");
            if(Math.abs(p[0]-v[0])>1e-7||Math.abs(p[1]-v[1])>1e-7)throw new AssertionError("Round trip");
        }
        double[] outside=Coordinates.toWgs84(51.5074,-.1278,"gcj02");
        if(outside[0]!=51.5074||outside[1]!=-.1278)throw new AssertionError("Outside mainland");
        try{Coordinates.toWgs84(30,120,"bd09ll");throw new AssertionError("Unknown CRS accepted");}catch(IllegalArgumentException expected){}
        try{Coordinates.toWgs84(Double.NaN,120,"wgs84");throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: coordinate reference, inversion, bounds and CRS rejection");
    }
}
