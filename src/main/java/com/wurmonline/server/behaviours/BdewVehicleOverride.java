package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import net.bdew.wurm.movemod.MoveMod;
import org.gotti.wurmunlimited.modsupport.vehicles.ModVehicleBehaviour;

public class BdewVehicleOverride extends ModVehicleBehaviour {
    public boolean forCreature;
    public float maxSpeed;
    public float maxDepth;
    public float maxHeight;
    public float maxHeightDiff;
    public float skillNeeded;
    public byte windImpact;
    public int loadDistance;
    public int hitchPairs;
    public float hitchXOffset;
    public float hitchXDelta;
    public float hitchYOffset;

    public BdewVehicleOverride(float maxSpeed, float maxDepth, float maxHeight, float maxHeightDiff, int loadDistance, byte windImpact, float skillNeeded) {
        this.forCreature = windImpact < 0;
        this.maxSpeed = maxSpeed;
        this.maxDepth = maxDepth;
        this.maxHeight = maxHeight;
        this.maxHeightDiff = maxHeightDiff;
        this.windImpact = windImpact;
        this.loadDistance = loadDistance;
        this.skillNeeded = skillNeeded;
    }

    public BdewVehicleOverride(float maxSpeed, float maxDepth, float maxHeight, float maxHeightDiff, float skillNeeded) {
        this(maxSpeed, maxDepth, maxHeight, maxHeightDiff, 0, (byte) -1, skillNeeded);
    }

    @Override
    public void setSettingsForVehicle(Creature creature, Vehicle vehicle) {
        if (!forCreature) {
            MoveMod.logWarning("VehicleOverride called for wrong type! Expected item, got creature #" + creature.getTemplate().getTemplateId());
            return;
        }
        vehicle.setMaxSpeed(maxSpeed);
        vehicle.setMaxDepth(maxDepth);
        vehicle.setMaxHeight(maxHeight);
        vehicle.setMaxHeightDiff(maxHeightDiff);
        vehicle.setSkillNeeded(skillNeeded);
    }

    @Override
    public void setSettingsForVehicle(Item item, Vehicle vehicle) {
        if (forCreature) {
            MoveMod.logWarning("VehicleOverride called for wrong type! Expected creature, got item #" + item.getTemplate().getTemplateId());
            return;
        }
        vehicle.setMaxSpeed(maxSpeed);
        vehicle.setMaxDepth(maxDepth);
        vehicle.setMaxHeight(maxHeight);
        vehicle.setMaxHeightDiff(maxHeightDiff);
        vehicle.setWindImpact(windImpact);
        vehicle.setMaxAllowedLoadDistance(loadDistance);
        vehicle.setSkillNeeded(skillNeeded);
        if (MoveMod.enableBoatHitching && hitchPairs > 0) {
            Seat[] hitches = new Seat[hitchPairs * 2];
            for (int i = 0; i < hitchPairs; i++) {
                Seat h1 = hitches[i * 2] = new Seat((byte) 2);
                Seat h2 = hitches[i * 2 + 1] = new Seat((byte) 2);
                h1.offy = hitchYOffset;
                h2.offy = -hitchYOffset;
                h1.offx = h2.offx = hitchXOffset + hitchXDelta * i;
            }
            vehicle.addHitchSeats(hitches);
        }
    }

    public String infoString() {
        if (forCreature)
            return String.format("maxSpeed=%.2f maxDepth=%.2f maxHeight=%.2f maxSlope=%.2f skillNeeded=%.2f", maxSpeed, maxDepth, maxHeight, maxHeightDiff, skillNeeded);
        else
            return String.format("maxSpeed=%.2f maxDepth=%.2f maxHeight=%.2f maxSlope=%.2f windImpact=%d loadDistance=%d skillNeeded=%.2f",
                    maxSpeed, maxDepth, maxHeight, maxHeightDiff, windImpact, loadDistance, skillNeeded) +
                    ((hitchPairs > 0 && MoveMod.enableBoatHitching) ? String.format(" BoatHitch: pairs=%d xOffset=%.2f xDelta=%.2f yOffset=%.2f", hitchPairs, hitchXOffset, hitchXDelta, hitchYOffset) : "");
    }
}
