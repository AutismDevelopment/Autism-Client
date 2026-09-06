package autismclient.util;

final class AutismPathGeometry {
    private AutismPathGeometry() { }

    static int lowerBound(int x, int z, int goalX, int goalZ) {
        return 9 * (Math.abs(x - goalX) + Math.abs(z - goalZ));
    }

    static boolean safeRise(double fromFloor, double toFloor, double stepHeight) {
        return toFloor - fromFloor <= stepHeight + 1.0E-5
            && fromFloor - toFloor < 0.5;
    }

    static boolean crossesBox(double ax, double az, double bx, double bz,
                              double minX, double minZ, double maxX, double maxZ) {
        double dx = bx - ax;
        double dz = bz - az;
        double enter = 0.0;
        double leave = 1.0;
        if (Math.abs(dx) < 1.0E-10) {
            if (ax <= minX || ax >= maxX) return false;
        } else {
            double p = (minX - ax) / dx;
            double q = (maxX - ax) / dx;
            enter = Math.max(enter, Math.min(p, q));
            leave = Math.min(leave, Math.max(p, q));
        }
        if (Math.abs(dz) < 1.0E-10) {
            if (az <= minZ || az >= maxZ) return false;
        } else {
            double p = (minZ - az) / dz;
            double q = (maxZ - az) / dz;
            enter = Math.max(enter, Math.min(p, q));
            leave = Math.min(leave, Math.max(p, q));
        }
        return enter < leave - 1.0E-9;
    }
}
