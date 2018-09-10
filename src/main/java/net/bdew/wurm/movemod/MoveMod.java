package net.bdew.wurm.movemod;

import com.wurmonline.server.behaviours.BdewVehicleOverride;
import javassist.ClassPool;
import javassist.CtClass;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.vehicles.ModVehicleBehaviours;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MoveMod implements WurmServerMod, Configurable, Initable, PreInitable, ServerStartedListener {
    private static final Logger logger = Logger.getLogger("MoveMod");

    public static float weightLimit = 7000;
    public static float slowWeight = 2000;
    public static float encumberedWeight = 3500;
    public static float cantMoveWeight = 7000;
    public static float playerSpeedMultiplier = 1;
    public static boolean enableBoatHitching = false;
    public static float creatureSpeedMultiplier = 1;
    public static float boatGlobalMultiplier, boatSeaGodBonus;

    private HashMap<Integer, BdewVehicleOverride> vehicleOverrides = new HashMap<>();
    private HashMap<Integer, BdewVehicleOverride> creatureOverrides = new HashMap<>();

    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static void logWarning(String msg) {
        if (logger != null)
            logger.log(Level.WARNING, msg);
    }

    public static void logInfo(String msg) {
        if (logger != null)
            logger.log(Level.INFO, msg);
    }

    private BdewVehicleOverride prepareVehicleOverride(String paramStr, boolean forCreature, int id) {
        String[] params = paramStr.trim().split(",");
        if (forCreature && params.length != 5)
            throw new RuntimeException("Invalid number of parameters in creature config: " + paramStr);
        if (!forCreature && params.length != 7)
            throw new RuntimeException("Invalid number of parameters in vehicle config: " + paramStr);
        float speed = Float.parseFloat(params[0]);
        float maxDepth = Float.parseFloat(params[1]);
        float maxHeight = Float.parseFloat(params[2]);
        float maxSlope = Float.parseFloat(params[3]);
        if (!forCreature) {
            byte windImpact = Byte.parseByte(params[4]);
            int loadDistance = Integer.parseInt(params[5]);
            float skill = Float.parseFloat(params[6]);
            return new BdewVehicleOverride(speed, maxDepth, maxHeight, maxSlope, loadDistance, windImpact, skill);
        } else {
            float skill = Float.parseFloat(params[4]);
            return new BdewVehicleOverride(speed, maxDepth, maxHeight, maxSlope, skill);
        }
    }

    private void setHitchParams(BdewVehicleOverride vo, String paramStr) {
        String[] params = paramStr.trim().split(",");
        if (vo == null)
            throw new RuntimeException("No override found");
        if (params.length != 4)
            throw new RuntimeException("Invalid number of parameters in hitch config: " + paramStr);
        vo.hitchPairs = Integer.parseInt(params[0]);
        vo.hitchXOffset = Float.parseFloat(params[1]);
        vo.hitchXDelta = Float.parseFloat(params[2]);
        vo.hitchYOffset = Float.parseFloat(params[3]);
    }

    private String getOverrideDescription(Integer id, BdewVehicleOverride vo) {
        return null;
    }

    @Override
    public void configure(Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            try {
                String value = properties.getProperty(name);
                switch (name) {
                    case "weightLimit":
                        weightLimit = Float.parseFloat(value);
                        break;
                    case "slowWeight":
                        slowWeight = Float.parseFloat(value);
                        break;
                    case "encumberedWeight":
                        encumberedWeight = Float.parseFloat(value);
                        break;
                    case "cantMoveWeight":
                        cantMoveWeight = Float.parseFloat(value);
                        break;
                    case "playerSpeedMultiplier":
                        playerSpeedMultiplier = Float.parseFloat(value);
                        break;
                    case "enableBoatHitching":
                        enableBoatHitching = Boolean.parseBoolean(value);
                        break;
                    case "creatureSpeedMultiplier":
                        creatureSpeedMultiplier = Float.parseFloat(value);
                        break;
                    case "boatGlobalMultiplier":
                        boatGlobalMultiplier = Float.parseFloat(value);
                        break;
                    case "boatSeaGodBonus":
                        boatSeaGodBonus = Float.parseFloat(value);
                        break;
                    case "classname":
                    case "classpath":
                    case "sharedClassLoader":
                        break; //ignore
                    default:
                        if (name.startsWith("creature@")) {
                            String[] split = name.split("@");
                            int id = Integer.parseInt(split[1]);
                            creatureOverrides.put(id, prepareVehicleOverride(value, true, id));
                        } else if (name.startsWith("vehicle@")) {
                            String[] split = name.split("@");
                            int id = Integer.parseInt(split[1]);
                            vehicleOverrides.put(id, prepareVehicleOverride(value, false, id));
                        } else if (name.startsWith("hitch@")) {
                            String[] split = name.split("@");
                            int id = Integer.parseInt(split[1]);
                            setHitchParams(vehicleOverrides.get(id), value);
                        } else {
                            logWarning("Unknown config property: " + name);
                        }
                }
            } catch (Exception e) {
                logException("Error processing property " + name, e);
            }
        }
        logInfo("weightLimit = " + weightLimit);
        logInfo("slowWeight = " + slowWeight);
        logInfo("encumberedWeight = " + encumberedWeight);
        logInfo("cantMoveWeight = " + cantMoveWeight);
        logInfo("playerSpeedMultiplier = " + playerSpeedMultiplier);
        logInfo("enableBoatHitching = " + enableBoatHitching);
        logInfo("creatureSpeedMultiplier = " + creatureSpeedMultiplier);
        logInfo("boatGlobalMultiplier = " + boatGlobalMultiplier);
        logInfo("boatSeaGodBonus = " + boatSeaGodBonus);
        logInfo(vehicleOverrides.size() + " Vehicle Overrides:");
        for (Map.Entry<Integer, BdewVehicleOverride> vo : vehicleOverrides.entrySet())
            logInfo(String.format("%d -> %s", vo.getKey(), vo.getValue().infoString()));
        logInfo(creatureOverrides.size() + " Creature Overrides:");
        for (Map.Entry<Integer, BdewVehicleOverride> vo : creatureOverrides.entrySet())
            logInfo(String.format("%d -> %s", vo.getKey(), vo.getValue().infoString()));
    }

    @Override
    public void init() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            if (playerSpeedMultiplier != 1) {
                // Tweak base player speed
                CtClass ctMovementScheme = classPool.getCtClass("com.wurmonline.server.creatures.MovementScheme");
                ctMovementScheme.getMethod("getSpeedModifier", "()F").insertAfter("if ($_>0 && this.creature.isPlayer()) {" +
                        "   $_ = $_ * " + playerSpeedMultiplier + ";" +
                        "};");
            }

            // Buff carry weight limits
            CtClass ctCreature = classPool.getCtClass("com.wurmonline.server.creatures.Creature");
            ctCreature.getMethod("setMoveLimits", "()V").insertAfter(
                    "       com.wurmonline.server.skills.Skill strength = this.skills.getSkill(102);" +
                            "this.moveslow = (int)strength.getKnowledge(0.0) * " + slowWeight + ";" +
                            "this.encumbered = (int)strength.getKnowledge(0.0) * " + encumberedWeight + ";" +
                            "this.cantmove = (int)strength.getKnowledge(0.0) * " + cantMoveWeight + ";"
            );

            // Use correct carry capacity
            ctCreature.getMethod("canCarry", "(I)Z").setBody("{ return getCarryingCapacityLeft() > $1; }");
            ctCreature.getMethod("getCarryCapacityFor", "(I)I").setBody("{ return (int) getCarryingCapacityLeft() / $1; }");
            ctCreature.getMethod("getCarryingCapacityLeft", "()I").setBody("{ " +
                    "   try {" +
                    "       return (int)this.skills.getSkill(102).getKnowledge(0.0) * " + (int) weightLimit + " - this.carriedWeight;" +
                    "   } catch (com.wurmonline.server.skills.NoSuchSkillException nss) {" +
                    "       logger.log(java.util.logging.Level.WARNING, \"No strength skill for \" + this, (java.lang.Throwable)nss);" +
                    "       return 0;" +
                    "   }" +
                    "}");
            ctCreature.getMethod("getMoveModifier", "(I)F").insertAfter("if (!isPlayer()) $_ = $_ * " + creatureSpeedMultiplier + ";");

            if (enableBoatHitching) {
                // Fix boat when hitching animals
                classPool.getCtClass("com.wurmonline.server.behaviours.Vehicle").getMethod("calculateNewVehicleSpeed", "(Z)B").insertBefore(
                        "if (this.windImpact != 0) return this.calculateNewBoatSpeed(false);"
                );

                // No need for strength check to hitch to a boat
                classPool.getCtClass("com.wurmonline.server.behaviours.VehicleBehaviour").getMethod("isStrongEnoughToDrag", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z").insertBefore(
                        "if ($2.isBoat()) return true;"
                );

                // Fix z teleport spam for creatures dragging boats
                ctCreature.getMethod("calculateFloorLevel", "(Lcom/wurmonline/server/zones/VolaTile;ZZ)V").insertBefore(
                        "if (this.getHitched()!=null && this.getHitched().getWindImpact()!=0) return;"
                );
            }

            CtClass ctVehicle = classPool.getCtClass("com.wurmonline.server.behaviours.Vehicle");
            ctVehicle.getMethod("calculateNewVehicleSpeed", "(Z)B").insertAfter("if ($_<0) $_=java.lang.Byte.MAX_VALUE;");
            ctVehicle.getMethod("calculateNewBoatSpeed", "(Z)B").insertAfter("if ($_<0) $_=java.lang.Byte.MAX_VALUE;");
            ctVehicle.getMethod("calculateNewMountSpeed", "(Lcom/wurmonline/server/creatures/Creature;Z)B").insertAfter("if ($_<0) $_=java.lang.Byte.MAX_VALUE;");

            for (Map.Entry<Integer, BdewVehicleOverride> ent : vehicleOverrides.entrySet()) {
                ModVehicleBehaviours.addItemVehicle(ent.getKey(), ent.getValue());
            }

            for (Map.Entry<Integer, BdewVehicleOverride> ent : creatureOverrides.entrySet()) {
                ModVehicleBehaviours.addCreatureVehicle(ent.getKey(), ent.getValue());
            }

            classPool.getCtClass("com.wurmonline.server.creatures.MovementScheme")
                    .getMethod("addMountSpeed", "(S)Z")
                    .insertBefore("$1 = net.bdew.wurm.movemod.Hooks.modMountSpeed(creature, $1);");

        } catch (Throwable e) {
            logException("Error loading mod", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onServerStarted() {
//        try {
//            try (PrintStream ps = new PrintStream("movemod.template")) {
//                Field fSpeed = Vehicle.class.getDeclaredField("maxSpeed");
//                fSpeed.setAccessible(true);
//
//                ps.println("# ============ Creatures ============");
//
//                for (CreatureTemplate t : CreatureTemplateFactory.getInstance().getTemplates()) {
//                    Creature c = Creature.doNew(t.getTemplateId(), 0f, 0f, 0f, 0, "", (byte) 0);
//
//                    Vehicle v = Vehicles.getVehicle(c);
//                    if (v != null) {
//                        ps.println("# ===================================");
//                        ps.println("# " + t.getName());
//                        ps.println(String.format("creature@%d = %.0f,%.1f,%.1f,%.2f,%.0f",
//                                t.getTemplateId(), (Float) fSpeed.get(v), v.maxDepth, v.maxHeight, v.maxHeightDiff, v.skillNeeded)
//                        );
//                    }
//                }
//
//                ps.println("# ============ Vehicles =============");
//
//
//                for (ItemTemplate t : ItemTemplateFactory.getInstance().getTemplates()) {
//                    if (t.isVehicle()) {
//                        Item i = new TempItem("", t, 99, "");
//                        Vehicle v = Vehicles.getVehicle(i);
//                        if (v != null) {
//                            ps.println("# ===================================");
//                            ps.println("# " + t.getName());
//                            ps.println(String.format("vehicle@%d = %.0f,%.1f,%.1f,%.2f,%d,%d,%.0f",
//                                    t.getTemplateId(), (Float) fSpeed.get(v), v.maxDepth, v.maxHeight, v.maxHeightDiff, v.getWindImpact(), v.getMaxAllowedLoadDistance(), v.skillNeeded)
//                            );
//                        }
//                    }
//                }
//
//
//                ps.flush();
//            }
//        } catch (Exception e) {
//            logException("error dumping", e);
//        }
    }

    @Override
    public void preInit() {
        ModVehicleBehaviours.init();
    }
}
