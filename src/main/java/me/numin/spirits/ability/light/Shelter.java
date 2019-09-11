package me.numin.spirits.ability.light;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.airbending.AirSwipe;
import com.projectkorra.projectkorra.earthbending.EarthBlast;
import com.projectkorra.projectkorra.firebending.FireBlast;
import com.projectkorra.projectkorra.firebending.FireBlastCharged;
import com.projectkorra.projectkorra.waterbending.WaterManipulation;
import me.numin.spirits.ability.api.removal.Removal;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.util.ParticleEffect;

import me.numin.spirits.Spirits;
import me.numin.spirits.Methods;
import me.numin.spirits.Methods.SpiritType;
import me.numin.spirits.ability.api.LightAbility;

public class Shelter extends LightAbility implements AddonAbility {

    public enum ShelterType {
        CLICK, SHIFT
    }
    private Location location, origin, shieldLocation;
    private Removal removal;
    private ShelterType shelterType;
    private Vector direction;

    private boolean isDamaged, progress, registerY, removeIfFar, removeOnDamage;
    private double entityY, othersRadius, selfRadius, startHealth;
    private int currPoint, range;
    private long clickDelay, duration, othersCooldown, selfCooldown, time;

    public Shelter(Player player, ShelterType shelterType) {
        super(player);

        if (!bPlayer.canBend(this)) {
            return;
        }

        setFields();

        time = System.currentTimeMillis();
        this.shelterType = shelterType;
        startHealth = player.getHealth();
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.1F, 2);

        start();
    }

    private void setFields() {
        this.othersCooldown = Spirits.plugin.getConfig().getLong("Abilities.Spirits.LightSpirit.Shelter.Others.Cooldown");
        this.selfCooldown = Spirits.plugin.getConfig().getLong("Abilities.Spirits.LightSpirit.Shelter.Self.Cooldown");
        this.duration = Spirits.plugin.getConfig().getLong("Abilities.Spirits.LightSpirit.Shelter.Duration");
        this.clickDelay = Spirits.plugin.getConfig().getLong("Abilities.Spirits.LightSpirit.Shelter.Others.ClickDelay");
        this.removeOnDamage = Spirits.plugin.getConfig().getBoolean("Abilities.Spirits.LightSpirits.Shelter.RemoveOnDamage");
        this.removeIfFar = Spirits.plugin.getConfig().getBoolean("Abilities.Spirits.LightSpirit.Shelter.RemoveIfFarAway.Enabled");
        this.range = Spirits.plugin.getConfig().getInt("Abilities.Spirits.LightSpirit.Shelter.RemoveIfFarAway.Range");
        this.othersRadius = Spirits.plugin.getConfig().getDouble("Abilities.Spirits.LightSpirit.Shelter.Others.Radius");
        this.selfRadius = Spirits.plugin.getConfig().getDouble("Abilities.Spirits.LightSpirit.Shelter.Self.Radius");

        this.origin = player.getLocation().clone().add(0, 1, 0);
        this.location = origin.clone();
        this.direction = player.getLocation().getDirection();
        this.progress = true;
        this.isDamaged = false;
        this.registerY = true;
        this.removal = new Removal(player);
    }

    @Override
    public void progress() {
        if (removal.stop()) {
            remove();
            return;
        }
        if (origin.distanceSquared(location) > range * range) {
            remove();
            return;
        }
        if (removeOnDamage) {
            if (player.getHealth() < startHealth) {
                isDamaged = true;
            }
        }
        if (shelterType == ShelterType.CLICK) {
            shieldOther();
        } else if (shelterType == ShelterType.SHIFT) {
            if (player.isSneaking()) {
                shieldSelf();
            } else {
                bPlayer.addCooldown(this, selfCooldown);
                remove();
            }
        }
    }

    private void shieldSelf() {
        if (System.currentTimeMillis() > time + duration) {
            bPlayer.addCooldown(this, selfCooldown);
            remove();
        } else {
            rotateShield(player.getLocation(), 96, selfRadius);
            blockMove();
            for (Entity target : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), selfRadius)) {
                if (target instanceof LivingEntity && !target.getUniqueId().equals(player.getUniqueId())) {
                    this.blockEntity((LivingEntity)target, target.getLocation().getY());
                } else if (target instanceof Projectile) {
                    target.remove();
                }
            }
        }
    }

    private void shieldOther() {
        if (progress) {
            location.add(direction.multiply(1));
            progressBlast(location);
        }

        for (Entity target : GeneralMethods.getEntitiesAroundPoint(location, 2)) {
            if (target instanceof LivingEntity && !target.getUniqueId().equals(player.getUniqueId())) {
                bPlayer.addCooldown(this, othersCooldown);
                if (System.currentTimeMillis() > time + duration) {
                    remove();
                    return;
                } else {
                    this.progress = false;
                    location = target.getLocation();

                    if (isDamaged) {
                        remove();
                        return;
                    }
                    for (Entity target2 : GeneralMethods.getEntitiesAroundPoint(location, othersRadius)) {
                        if (target2 instanceof LivingEntity && !target2.getUniqueId().equals(target.getUniqueId()) && !target2.getUniqueId().equals(player.getUniqueId())) {
                            if (registerY) {
                                entityY = target.getLocation().getY();
                                registerY = false;
                            }
                            blockEntity((LivingEntity) target2, this.entityY);
                        } else if (target2 instanceof Projectile) {
                            target2.remove();
                        }
                    }
                    blockMove();
                    rotateShield(location, 100, othersRadius);
                    shieldLocation = location;

                    if (removeIfFar) {
                        if (player.getLocation().distanceSquared(target.getLocation()) > range * range) {
                            remove();
                            return;
                        }
                    }
                }
            } else {
                bPlayer.addCooldown(this, clickDelay);
            }
        }
    }
    private void rotateShield(Location location, int points, double size) {
        for (int t = 0; t < 6; t++) {
            currPoint += 360 / points;
            if (currPoint > 360) {
                currPoint = 0;
            }
            double angle = currPoint * Math.PI / 180 * Math.cos(Math.PI);
            double x2 = size * Math.cos(angle);
            double y = 0.9 * (Math.PI * 5 - t) - 10;
            double z2 = size * Math.sin(angle);
            location.add(x2, y, z2);
            ParticleEffect.SPELL_INSTANT.display(location, 0.5F, 0.5F, 0.5F, 0, 1);
            location.subtract(x2, y, z2);
        }
    }
    private void progressBlast(Location location) {
        for (int i = 0; i < 6; i++) {
            currPoint += 360 / 100;
            if (currPoint > 360) {
                currPoint = 0;
            }
            double angle = currPoint * Math.PI / 180 * Math.cos(Math.PI);
            double x = 0.04 * (Math.PI * 4 - angle) * Math.cos(angle + i);
            double z = 0.04 * (Math.PI * 4 - angle) * Math.sin(angle + i);
            location.add(x, 0.1F, z);
            ParticleEffect.SPELL_INSTANT.display(location, 0, 0, 0, 0, 1);
            location.subtract(x, 0.1F, z);
        }
    }

    private void blockEntity(LivingEntity entity, double Y) {
        Vector velocity = entity.getLocation().toVector().subtract(player.getLocation().toVector()).multiply(0.1);
        velocity.setY(-0.5);
        entity.setVelocity(velocity);
    }

    private static void blockMove() {
        CoreAbility fireBlast = CoreAbility.getAbility(FireBlast.class);
        CoreAbility earthBlast = CoreAbility.getAbility(EarthBlast.class);
        CoreAbility waterManip = CoreAbility.getAbility(WaterManipulation.class);
        CoreAbility airSwipe = CoreAbility.getAbility(AirSwipe.class);
        CoreAbility fireBlastCharged = CoreAbility.getAbility(FireBlastCharged.class);
        CoreAbility shelter = CoreAbility.getAbility(Shelter.class);

        CoreAbility[] smallAbilities = {airSwipe, earthBlast, waterManip, fireBlast, fireBlastCharged};

        for (CoreAbility smallAbility : smallAbilities) {
            ProjectKorra.getCollisionManager().addCollision(new Collision(shelter, smallAbility, false, true));
        }
    }

    @Override
    public long getCooldown() {
        return shelterType == ShelterType.CLICK ? othersCooldown : selfCooldown;
    }

    @Override
    public Location getLocation() {
        return shelterType == ShelterType.CLICK ?  shieldLocation : player.getLocation();
    }

    @Override
    public double getCollisionRadius() {
        return this.shelterType == ShelterType.CLICK ? othersRadius : selfRadius;
    }

    @Override
    public String getName() {
        return "Shelter";
    }

    @Override
    public String getDescription() {
        return Methods.setSpiritDescription(SpiritType.LIGHT, "Defense") +
                Spirits.plugin.getConfig().getString("Language.Abilities.LightSpirit.Shelter.Description");
    }

    @Override
    public String getInstructions() {
        return Methods.getSpiritColor(SpiritType.LIGHT) + Spirits.plugin.getConfig().getString("Language.Abilities.LightSpirit.Shelter.Instructions");
    }

    @Override
    public String getAuthor() {
        return Methods.getSpiritColor(SpiritType.LIGHT) + "" + Methods.getAuthor();
    }

    @Override
    public String getVersion() {
        return Methods.getSpiritColor(SpiritType.LIGHT) + Methods.getVersion();
    }

    @Override
    public boolean isEnabled() {
        return Spirits.plugin.getConfig().getBoolean("Abilities.Spirits.LightSpirit.Shelter.Enabled");
    }

    @Override
    public boolean isExplosiveAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isIgniteAbility() {
        return false;
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public void load() {}
    @Override
    public void stop() {}
}