package offcraft.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.gl.Framebuffer;
import offcraft.client.RenderPolicy;
import offcraft.client.OffcraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Shadow private int attackCooldown;
    @Shadow public Screen currentScreen;

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void offcraft$clearClosedScreenAttackSuppression(Screen screen, CallbackInfo ci) {
        // GUI ticks install 10000; ordinary attack cooldowns are at most 10.
        // Hidden mode has no cursor reacquisition or guaranteed neutral input tick.
        if (currentScreen == null && attackCooldown > 10) attackCooldown = 0;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;onWindowFocusChanged(Z)V"), index = 0)
    private boolean offcraft$initiallyUnfocused(boolean focused) {
        return false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void offcraft$commands(boolean tick, CallbackInfo ci) {
        OffcraftClient.beforeFrame((MinecraftClient) (Object) this);
        RenderPolicy.begin((MinecraftClient) (Object) this, OffcraftClient.forceDraw());
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/Window;swapBuffers()V"))
    private void offcraft$capture(boolean tick, CallbackInfo ci) {
        OffcraftClient.afterFrame((MinecraftClient) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void offcraft$beginActionTick(CallbackInfo ci) {
        OffcraftClient.beforeTick((MinecraftClient) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void offcraft$endActionTick(CallbackInfo ci) {
        OffcraftClient.afterTick((MinecraftClient) (Object) this);
    }

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Mouse;isCursorLocked()Z"))
    private boolean offcraft$allowControlledBreaking(Mouse mouse) {
        return OffcraftClient.attacking() || mouse.isCursorLocked();
    }

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;hasControlDown()Z"))
    private boolean offcraft$controlForStackDrop() {
        return OffcraftClient.droppingStack() || Screen.hasControlDown();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"))
    private void offcraft$drawWhenNeeded(GameRenderer renderer, RenderTickCounter counter, boolean tick) {
        if (RenderPolicy.draw() || OffcraftClient.forceDraw()) {
            renderer.render(OffcraftClient.captureTickCounter(counter), tick);
            RenderPolicy.markRendered();
        } else {
            RenderPolicy.skipped();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"))
    private void offcraft$skipUnchangedBlit(Framebuffer framebuffer, int width, int height) {
        if (RenderPolicy.rendered()) framebuffer.draw(width, height);
    }

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void offcraft$keepTickLoopLive(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RenderPolicy.loopFps((MinecraftClient) (Object) this));
    }

    @Inject(method = "scheduleStop", at = @At("HEAD"))
    private void offcraft$releaseOnStop(CallbackInfo ci) {
        OffcraftClient.stopRequested((MinecraftClient) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void offcraft$releaseBeforeClose(CallbackInfo ci) {
        OffcraftClient.stopRequested((MinecraftClient) (Object) this);
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void offcraft$completeShutdown(CallbackInfo ci) {
        OffcraftClient.closed((MinecraftClient) (Object) this);
    }
}
