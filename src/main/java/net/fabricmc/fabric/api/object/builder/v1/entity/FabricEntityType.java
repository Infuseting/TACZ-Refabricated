package net.fabricmc.fabric.api.object.builder.v1.entity;

import net.minecraft.world.entity.Entity;

/**
 * Compatibility interface for mods compiled with older Fabric API versions
 * that injected FabricEntityType$Builder into EntityType$Builder.
 */
public interface FabricEntityType {
    interface Builder<T extends Entity> {
    }
}
