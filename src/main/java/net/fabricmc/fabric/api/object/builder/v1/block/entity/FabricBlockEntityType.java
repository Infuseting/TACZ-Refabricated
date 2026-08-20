package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Compatibility interface for mods compiled with older Fabric API versions
 * that injected FabricBlockEntityType$Builder into BlockEntityType$Builder.
 */
public interface FabricBlockEntityType {
    interface Builder<T extends BlockEntity> {
    }
}
