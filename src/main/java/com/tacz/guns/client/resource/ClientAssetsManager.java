package com.tacz.guns.client.resource;

import cn.sh1rocu.tacz.TaCZFabric;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.gltf.AnimationStructure;
import com.tacz.guns.api.vmlib.LuaAnimationConstant;
import com.tacz.guns.api.vmlib.LuaGunAnimationConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.client.resource.manager.DisplayManager;
import com.tacz.guns.client.resource.manager.GltfManager;
import com.tacz.guns.client.resource.manager.PackInfoManager;
import com.tacz.guns.client.resource.pojo.CommonTransformObject;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.animation.bedrock.SoundEffectKeyframes;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import com.tacz.guns.client.resource.serialize.AnimationKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.ItemStackSerializer;
import com.tacz.guns.client.resource.serialize.SoundEffectKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.Vector3fSerializer;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.LazyJsonDataManager;
import com.tacz.guns.resource.manager.ScriptManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 客户端资源管理器<br/>
 * 所有枪包资源缓存在此
 */
@Environment(EnvType.CLIENT)
public enum ClientAssetsManager {
    INSTANCE;
    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer())
            .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
            .registerTypeAdapter(CommonTransformObject.class, new CommonTransformObject.Serializer())
            .registerTypeAdapter(ItemStack.class, new ItemStackSerializer())
            .registerTypeAdapter(AnimationKeyframes.class, new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
            .registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())
            .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
            .create();

    // 枪械展示数据
    private DisplayManager<GunDisplay> gunDisplay;
    // 弹药展示数据
    private DisplayManager<AmmoDisplay> ammoDisplay;
    // 配件展示数据
    private DisplayManager<AttachmentDisplay> attachmentDisplay;
    // 方块展示数据
    private DisplayManager<BlockDisplay> blockDisplay;
    // 原始基岩版模型
    private LazyJsonDataManager<BedrockModelPOJO> bedrockModel;
    // 基岩版模型动画
    private LazyJsonDataManager<BedrockAnimationFile> bedrockAnimation;
    // gltf 动画
    private GltfManager gltfAnimation;
    // 客户端脚本
    private final List<LuaLibrary> libList = List.of(new LuaAnimationConstant(), new LuaGunAnimationConstant());
    private ScriptManager scriptManager;
    // 音效
    // 枪包元数据
    private PackInfoManager packInfo;

    private List<IdentifiableResourceReloadListener> listeners;

    public void reloadAndRegister(Consumer<IdentifiableResourceReloadListener> register) {
        if (listeners == null) {
            listeners = new ArrayList<>();
            gunDisplay = register(new DisplayManager<>(GunDisplay.class, GSON, "display/guns", "GunDisplayLoader"));
            ammoDisplay = register(new DisplayManager<>(AmmoDisplay.class, GSON, "display/ammo", "AmmoDisplayLoader"));
            attachmentDisplay = register(new DisplayManager<>(AttachmentDisplay.class, GSON, "display/attachments", "AttachmentDisplayLoader"));
            blockDisplay = register(new DisplayManager<>(BlockDisplay.class, GSON, "display/blocks", "BlockDisplayLoader"));

            bedrockModel = register(new LazyJsonDataManager<>(BedrockModelPOJO.class, GSON, "geo_models", "BedrockModelLoader",
                    id -> GunMod.MOD_ID.equals(id.getNamespace())));
            bedrockAnimation = register(new LazyJsonDataManager<>(BedrockAnimationFile.class, GSON, new FileToIdConverter("animations", ".animation.json"),
                    "BedrockAnimationLoader", id -> GunMod.MOD_ID.equals(id.getNamespace())));
            gltfAnimation = register(new GltfManager());
            scriptManager = register(new ScriptManager(new FileToIdConverter("scripts", ".lua"), libList));
            packInfo = register(new PackInfoManager());
            register(new IdentifiableResourceReloadListener() {
                static final ResourceLocation ID = new ResourceLocation(GunMod.MOD_ID, "client_index_manager_reload");

                @Override
                public ResourceLocation getFabricId() {
                    return ID;
                }

                @Override
                public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
                    return barrier.wait(Void.TYPE).thenRunAsync(ClientIndexManager::reload, gameExecutor);
                }
            });
        }
        listeners.forEach(register);
    }

    private <T extends IdentifiableResourceReloadListener> T register(T listener) {
        listeners.add(listener);
        return listener;
    }

    @Nullable
    public GunDisplay getGunDisplay(ResourceLocation id) {
        return gunDisplay.getData(id);
    }

    public Set<Map.Entry<ResourceLocation, GunDisplay>> getGunDisplays() {
        return gunDisplay.getAllData().entrySet();
    }

    public Set<ResourceLocation> getGunDisplayIds() {
        return gunDisplay.getAllData().keySet();
    }

    @Nullable
    public AttachmentDisplay getAttachmentDisplay(ResourceLocation id) {
        return attachmentDisplay.getData(id);
    }

    @Nullable
    public AmmoDisplay getAmmoDisplay(ResourceLocation id) {
        return ammoDisplay.getData(id);
    }

    @Nullable
    public BlockDisplay getBlockDisplay(ResourceLocation id) {
        return blockDisplay.getData(id);
    }

    @Nullable
    public BedrockModelPOJO getBedrockModelPOJO(ResourceLocation id) {
        return bedrockModel.getData(id);
    }

    @Nullable
    public BedrockAnimationFile getBedrockAnimations(ResourceLocation id) {
        return bedrockAnimation.getData(id);
    }

    @Nullable
    public LuaTable getScript(ResourceLocation id) {
        return scriptManager.getScript(id);
    }

    @Nullable
    public AnimationStructure getGltfAnimation(ResourceLocation id) {
        return gltfAnimation.getGltfAnimation(id);
    }

    @Nullable
    public PackInfo getPackInfo(String namespace) {
        return packInfo.getData(namespace);
    }

    @Nullable
    public PackInfo getPackInfo(@Nullable ResourceLocation namespace) {
        if (namespace == null) {
            return null;
        }
        return packInfo.getData(namespace.getNamespace());
    }

    public void loadSecurePack(EncryptedPackResources pack) {
        if (pack == null) return;
        for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
            // Gun displays
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "display/guns", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("display/guns/")) {
                        String sub = path.substring("display/guns/".length());
                        if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        GunDisplay display = GSON.fromJson(reader, GunDisplay.class);
                        if (display != null && gunDisplay != null) {
                            display.init();
                            gunDisplay.putCustomData(id, display);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Ammo displays
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "display/ammo", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("display/ammo/")) {
                        String sub = path.substring("display/ammo/".length());
                        if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        AmmoDisplay display = GSON.fromJson(reader, AmmoDisplay.class);
                        if (display != null && ammoDisplay != null) {
                            display.init();
                            ammoDisplay.putCustomData(id, display);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Attachment displays
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "display/attachments", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("display/attachments/")) {
                        String sub = path.substring("display/attachments/".length());
                        if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        AttachmentDisplay display = GSON.fromJson(reader, AttachmentDisplay.class);
                        if (display != null && attachmentDisplay != null) {
                            display.init();
                            attachmentDisplay.putCustomData(id, display);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Block displays
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "display/blocks", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("display/blocks/")) {
                        String sub = path.substring("display/blocks/".length());
                        if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        BlockDisplay display = GSON.fromJson(reader, BlockDisplay.class);
                        if (display != null && blockDisplay != null) {
                            display.init();
                            blockDisplay.putCustomData(id, display);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Bedrock models
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "geo_models", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("geo_models/")) {
                        String sub = path.substring("geo_models/".length());
                        if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        BedrockModelPOJO model = GSON.fromJson(reader, BedrockModelPOJO.class);
                        if (model != null && bedrockModel != null) {
                            bedrockModel.putCustomData(id, model);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Bedrock animations
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "animations", (loc, io) -> {
                try (var reader = new InputStreamReader(io.get(), StandardCharsets.UTF_8)) {
                    String path = loc.getPath();
                    if (path.startsWith("animations/")) {
                        String sub = path.substring("animations/".length());
                        if (sub.endsWith(".animation.json")) {
                            sub = sub.substring(0, sub.length() - ".animation.json".length());
                        } else if (sub.endsWith(".json")) {
                            sub = sub.substring(0, sub.length() - 5);
                        }
                        ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                        BedrockAnimationFile anim = GSON.fromJson(reader, BedrockAnimationFile.class);
                        if (anim != null && bedrockAnimation != null) {
                            bedrockAnimation.putCustomData(id, anim);
                        }
                    }
                } catch (Exception ignored) {}
            });
            // Lua scripts — two-pass: register all in preload first, then evaluate
            // Pass 1: read raw bytes and register preload entries
            Map<ResourceLocation, byte[]> scriptBytes = new java.util.LinkedHashMap<>();
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "scripts", (loc, io) -> {
                if (loc.getPath().endsWith(".lua") && loc.getPath().startsWith("scripts/")) {
                    try {
                        scriptBytes.put(loc, io.get().readAllBytes());
                    } catch (Exception ignored) {}
                }
            });
            if (scriptManager != null && !scriptBytes.isEmpty()) {
                // Register all scripts in package.preload so require() can find them
                for (Map.Entry<ResourceLocation, byte[]> e : scriptBytes.entrySet()) {
                    ResourceLocation loc = e.getKey();
                    String sub = loc.getPath().substring("scripts/".length());
                    if (sub.endsWith(".lua")) sub = sub.substring(0, sub.length() - 4);
                    ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                    final byte[] data = e.getValue();
                    scriptManager.preloadScript(id, data);
                }
                // Pass 2: evaluate all scripts (require() can now resolve dependencies)
                for (Map.Entry<ResourceLocation, byte[]> e : scriptBytes.entrySet()) {
                    ResourceLocation loc = e.getKey();
                    String sub = loc.getPath().substring("scripts/".length());
                    if (sub.endsWith(".lua")) sub = sub.substring(0, sub.length() - 4);
                    ResourceLocation id = new ResourceLocation(loc.getNamespace(), sub);
                    try (var reader = new InputStreamReader(new java.io.ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)) {
                        scriptManager.loadFromReader(id, reader);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void reloadAllPack() {
        try {
            Minecraft.getInstance().reloadResourcePacks().get();
            if (TaCZFabric.getServer() != null) {
                // 直接刷新data
                CommonAssetsManager.reloadAllPack();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
