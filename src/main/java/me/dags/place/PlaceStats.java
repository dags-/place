package me.dags.place;

import ninja.leaping.configurate.ConfigurationNode;

/**
 * @author dags <dags@dags.me>
 */
public class PlaceStats {

    private final ConfigurationNode node;

    PlaceStats(ConfigurationNode node) {
        this.node = node;
    }

    public ConfigurationNode getParent() {
        return node.getParent();
    }

    public long getTimestamp(String world) {
        return node.getNode(world, "timestamp").getLong(0);
    }

    public int getPlacements(String world) {
        return node.getNode(world, "placements").getInt(0);
    }

    public int getReplacements(String world) {
        return node.getNode(world, "replacements").getInt(0);
    }

    public void setTimestamp(String world) {
        long time = System.currentTimeMillis();
        node.getNode(world, "timestamp").setValue(time);
    }

    public void incPlacements(String world) {
        ConfigurationNode placements = node.getNode(world, "placements");
        int count = placements.getInt(0) + 1;
        placements.setValue(count);
    }

    public void incReplacements(String world) {
        ConfigurationNode replacements = node.getNode(world, "replacements");
        int count = replacements.getInt(0) + 1;
        replacements.setValue(count);
    }
}
