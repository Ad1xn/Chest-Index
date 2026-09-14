package dev.adrian.chesttracker.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The one door into building a render type of our own.
 *
 * <p>Everything a custom render type needs is already public -
 * {@code RenderSetup.builder(pipeline)} and {@code createRenderSetup()} both
 * are - except the last step, which is package-private. So this is an invoker
 * rather than a reimplementation: the type is built by vanilla's own factory,
 * and nothing about how a render type works is copied here to go stale.
 *
 * <p>Identical on both targets, unusually. {@code RenderType.create} kept its
 * name, its signature and its package through the deferred-renderer rewrite,
 * so this needs no version branch at all.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {

    @Invoker("create")
    static RenderType chesttracker$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
