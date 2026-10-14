package com.oneshop.shop;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Decoupled visual icon representation for a shop entry or category.
 * Can represent either a vanilla/modded {@link ItemStack} or an arbitrary {@link ResourceLocation} texture/sprite.
 */
public record LotIcon(
        Type type,
        ItemStack item,
        ResourceLocation texture
) {
    public enum Type implements StringRepresentable {
        ITEM("item"),
        TEXTURE("texture");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static final Codec<Type> CODEC = Codec.STRING.xmap(
                s -> {
                    try {
                        return Type.valueOf(s.toUpperCase(Locale.ROOT));
                    } catch (Exception e) {
                        return ITEM;
                    }
                },
                Type::name
        );
    }

    public static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/barrier.png");

    public static final Codec<LotIcon> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Type.CODEC.optionalFieldOf("type", Type.ITEM).forGetter(LotIcon::type),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(LotIcon::item),
            ResourceLocation.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(LotIcon::texture)
    ).apply(instance, LotIcon::new));

    public static final Codec<LotIcon> CODEC = Codec.either(DIRECT_CODEC, ItemStack.CODEC).xmap(
            either -> either.map(icon -> icon, LotIcon::of),
            Either::left
    );

    public LotIcon {
        type = type != null ? type : Type.ITEM;
        item = item != null ? item : ItemStack.EMPTY;
        texture = texture != null ? texture : DEFAULT_TEXTURE;
    }

    public static LotIcon of(ItemStack item) {
        return new LotIcon(Type.ITEM, item != null ? item.copy() : ItemStack.EMPTY, DEFAULT_TEXTURE);
    }

    public static LotIcon of(ResourceLocation texture) {
        return new LotIcon(Type.TEXTURE, ItemStack.EMPTY, texture != null ? texture : DEFAULT_TEXTURE);
    }

    public static LotIcon ofItem(ItemStack item) {
        return of(item);
    }

    public static LotIcon ofTexture(ResourceLocation texture) {
        return of(texture);
    }

    public boolean isItem() {
        return type == Type.ITEM;
    }

    public boolean isTexture() {
        return type == Type.TEXTURE;
    }

    public boolean isEmpty() {
        if (isItem()) {
            return item == null || item.isEmpty();
        }
        return texture == null;
    }

    public LotIcon copy() {
        return new LotIcon(type, item.copy(), texture);
    }
}
