package net.bdew.wurm.movemod;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.WurmId;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.shared.constants.CounterTypes;

public class Hooks {
    public static short modMountSpeed(Creature creature, short base) {
        Vehicle vehicle = Vehicles.getVehicleForId(creature.getVehicle());
        if (vehicle != null) {
            try {
                if (WurmId.getType(vehicle.wurmid) == CounterTypes.COUNTER_TYPE_ITEMS && Items.getItem(vehicle.wurmid).isBoat() && vehicle.pilotId == creature.getWurmId()) {
                    base = (short) (base * MoveMod.boatGlobalMultiplier);
                    Deity deity = creature.getDeity();
                    if (deity != null && deity.isWaterGod()) {
                        base = (short) (base * MoveMod.boatSeaGodBonus);
                    }
                }
            } catch (NoSuchItemException e) {
                MoveMod.logException(String.format("Failed to get vehicle for %s (%d)", creature.getName(), vehicle.wurmid), e);
            }
        }
        return base;
    }
}
