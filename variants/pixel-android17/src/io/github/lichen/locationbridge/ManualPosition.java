package io.github.lichen.locationbridge;

/** Explicit user-entered position, never represented as a measured fix. */
final class ManualPosition {
    final double latitude,longitude;
    final float accuracy;
    ManualPosition(double latitude,double longitude,float accuracy) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180
                ||!Float.isFinite(accuracy)||accuracy<1||accuracy>10000)throw new IllegalArgumentException("Invalid manual position");
        this.latitude=latitude;this.longitude=longitude;this.accuracy=accuracy;
    }
    static ManualPosition parse(String latitude,String longitude,String accuracy,String crs) {
        try {
            double[] wgs=Coordinates.toWgs84(Double.parseDouble(latitude.trim()),Double.parseDouble(longitude.trim()),crs);
            return new ManualPosition(wgs[0],wgs[1],Float.parseFloat(accuracy.trim()));
        } catch(RuntimeException e){throw new IllegalArgumentException("Invalid manual position");}
    }
}
