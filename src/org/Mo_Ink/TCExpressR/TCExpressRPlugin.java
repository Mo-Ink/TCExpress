package org.Mo_Ink.TCExpressR;

import com.google.common.collect.Iterables;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class TCExpressRPlugin extends JavaPlugin implements Listener {
    private final static double NORMAL_SPEED = 0.4;
    private double MAX_SPEED = 2;//Max speed per tick
    private int BUFFER_LENGTH = 5;//The final distance on which cart keep 8m/s
    private int ADJUST_LENGTH = 10;//The adjust distance on which cart decelerate from max speed to 8m/s
    private boolean isExtraBoostEnabled = false;//If an extra boost is enabled, to accelerate all carts to max speed

    private static Block nextRail(BlockFace direction, Block block) {
        Block b = block.getRelative(direction);
        return isRail(b) ? b : null;
    }

    private static boolean isRail(Block block) {
        Material mat = block.getType();
        return mat == Material.RAIL || mat == Material.ACTIVATOR_RAIL || mat == Material.DETECTOR_RAIL || mat == Material.POWERED_RAIL;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfig() {
        MAX_SPEED = getConfig().getDouble("max_speed");
        MAX_SPEED /= 20;
        BUFFER_LENGTH = getConfig().getInt("buffer_length");
        ADJUST_LENGTH = getConfig().getInt("adjust_length");
        isExtraBoostEnabled = getConfig().getBoolean("is_extra_boost_enabled");
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    void onMove(VehicleMoveEvent e) {
        if (!(e.getVehicle() instanceof Minecart minecart)) return;

        Entity passenger = Iterables.getFirst(minecart.getPassengers(), null);
        if (passenger == null || passenger.getType() != EntityType.PLAYER) return;

        Block curBlock = minecart.getLocation().getBlock();
        if (!isRail(curBlock)) {
            minecart.setMaxSpeed(NORMAL_SPEED);
            return;
        }

        Rail.Shape curRailType = ((Rail) (curBlock.getBlockData())).getShape();
        if (curRailType != Rail.Shape.EAST_WEST && curRailType != Rail.Shape.NORTH_SOUTH) {
            minecart.setMaxSpeed(NORMAL_SPEED);
            return;
        }

        Vector vector = e.getVehicle().getVelocity();
        if (vector.getY() != 0) {
            minecart.setMaxSpeed(NORMAL_SPEED);
            return;
        }
        double x = vector.getX();
        double z = vector.getZ();

        if (x == 0 && z == 0) {
            minecart.setMaxSpeed(NORMAL_SPEED);
            return;
        }

        boolean isX = x != 0 && z == 0;
        boolean n = isX ? x < 0 : z < 0;
        double curSpeed = isX ? (n ? (-x) : x) : (n ? (-z) : z);
        BlockFace direction = isX ? (n ? BlockFace.WEST : BlockFace.EAST) : (n ? BlockFace.NORTH : BlockFace.SOUTH);

        int flatLength = 0;
        while ((curBlock = nextRail(direction, curBlock)) != null && flatLength < BUFFER_LENGTH + ADJUST_LENGTH) {
            curRailType = ((Rail) (curBlock.getBlockData())).getShape();
            if (isX) {
                if (curRailType != Rail.Shape.EAST_WEST) break;
            } else {
                if (curRailType != Rail.Shape.NORTH_SOUTH) break;
            }

            flatLength++;
        }

        if (flatLength < BUFFER_LENGTH) {
            minecart.setMaxSpeed(NORMAL_SPEED);
            return;
        }

        int freeLength = flatLength - BUFFER_LENGTH;

        double s = (double) freeLength / ADJUST_LENGTH;
        if (s > 1) s = 1;
        double speed = NORMAL_SPEED + (MAX_SPEED - NORMAL_SPEED) * s;
        minecart.setMaxSpeed(speed);

        if (!isExtraBoostEnabled)
            return;

        if ((curSpeed <= 8) || (curSpeed >= MAX_SPEED))
            return;

        double extra_boost = curSpeed * (MAX_SPEED - curSpeed + 10) / MAX_SPEED / 20;//Add extra acceleration
        if (extra_boost > 1)
            extra_boost = 1;

        curSpeed += extra_boost;
        if (curSpeed > MAX_SPEED)
            curSpeed = MAX_SPEED;
        if (isX) {
            vector.setX(n ? (-curSpeed) : curSpeed);
        } else {
            vector.setZ(n ? (-curSpeed) : curSpeed);
        }
        minecart.setVelocity(vector);
    }
}