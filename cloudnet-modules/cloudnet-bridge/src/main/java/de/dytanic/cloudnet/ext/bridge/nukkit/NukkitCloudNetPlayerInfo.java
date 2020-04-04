package de.dytanic.cloudnet.ext.bridge.nukkit;

import de.dytanic.cloudnet.driver.network.HostAndPort;
import de.dytanic.cloudnet.ext.bridge.WorldPosition;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

@ToString
@EqualsAndHashCode
public final class NukkitCloudNetPlayerInfo {

    protected double health, maxHealth, saturation;
    protected int level, ping;
    protected WorldPosition location;
    protected HostAndPort address;
    private UUID uniqueId;
    private String name;

    public NukkitCloudNetPlayerInfo(double health, double maxHealth, double saturation, int level, int ping, WorldPosition location, HostAndPort address, UUID uniqueId, String name) {
        this.health = health;
        this.maxHealth = maxHealth;
        this.saturation = saturation;
        this.level = level;
        this.ping = ping;
        this.location = location;
        this.address = address;
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public double getHealth() {
        return this.health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getMaxHealth() {
        return this.maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public double getSaturation() {
        return this.saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = saturation;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPing() {
        return this.ping;
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    public WorldPosition getLocation() {
        return this.location;
    }

    public void setLocation(WorldPosition location) {
        this.location = location;
    }

    public HostAndPort getAddress() {
        return this.address;
    }

    public void setAddress(HostAndPort address) {
        this.address = address;
    }

    public UUID getUniqueId() {
        return this.uniqueId;
    }

    public void setUniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

}