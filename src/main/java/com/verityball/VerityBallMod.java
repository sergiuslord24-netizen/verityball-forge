package com.verityball;

import com.verityball.entity.VerityEntity;
import com.verityball.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(VerityBallMod.MODID)
public class VerityBallMod {
    public static final String MODID = "verityball";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<VerityEntity>> VERITY = ENTITIES.register("verity",
            () -> EntityType.Builder.<VerityEntity>of(VerityEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)
                    .build(MODID + ":verity"));

    public static final RegistryObject<Item> VERITY_SPAWN_EGG = ITEMS.register("verity_spawn_egg",
            () -> new ForgeSpawnEggItem(VERITY, 0xFED905, 0x000000, new Item.Properties()));

    public VerityBallMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        ModSounds.SOUNDS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCreativeTab);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VERITY.get(), VerityEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            // Un huevo por variante: el huevo lleva EntityTag con la variante elegida
            for (VerityVariant variant : VerityVariant.values()) {
                ItemStack egg = new ItemStack(VERITY_SPAWN_EGG.get());
                CompoundTag entityTag = new CompoundTag();
                entityTag.putInt("VerityVariant", variant.id);
                egg.getOrCreateTag().put("EntityTag", entityTag);
                egg.setHoverName(Component.translatable("item.verityball.verity_spawn_egg." + variant.key)
                        .withStyle(style -> style.withItalic(false)));
                event.accept(egg);
            }
        }
    }
}
