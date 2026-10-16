package camelchests.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camel.class)
public abstract class CamelMixin extends AbstractHorse {

    // @Unique ensures no symbol collision with vanilla fields
    @Unique
    private boolean camelchest$hasChest = false;

    // 27 slots = standard 3-row chest (ChestMenu.threeRows)
    @Unique
    private final SimpleContainer camelchest$inventory = new SimpleContainer(27);

    // Mandatory dummy constructor for extending AbstractHorse in a Mixin; ignored
    // at runtime
    protected CamelMixin(EntityType<? extends AbstractHorse> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void camelchest$onInteract(Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!this.isTamed() || this.isBaby()) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);

        // 1. Equip chest when right-clicking with a Chest item
        if (!this.camelchest$hasChest && stack.is(Items.CHEST)) {
            if (!this.level().isClientSide()) {
                this.camelchest$hasChest = true;
                this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // 2. Open the chest GUI when sneaking (Shift + Right-Click)
        // if (this.camelchest$hasChest && player.isSecondaryUseActive()) {
        // if (!this.level().isClientSide()) {
        // player.openMenu(new SimpleMenuProvider(
        // (containerId, playerInventory, p) -> ChestMenu.threeRows(containerId,
        // playerInventory,
        // this.camelchest$inventory),
        // Component.translatable("container.chest")));
        // }
        // cir.setReturnValue(InteractionResult.SUCCESS);
        // }
    }

    @Inject(method = "openCustomInventoryScreen", at = @At("HEAD"), cancellable = true)
    private void camelchest$onOpenInventory(Player player, CallbackInfo ci) {
        if (!this.level().isClientSide() && this.isTamed() && this.camelchest$hasChest && this.hasPassenger(player)) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> ChestMenu.threeRows(
                            containerId, playerInventory, this.camelchest$inventory),
                    Component.translatable("container.chest")));
            ci.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void camelchest$saveData(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("CamelHasChest", this.camelchest$hasChest);
        if (this.camelchest$hasChest) {
            ContainerHelper.saveAllItems(output.child("CamelChestItems"), this.camelchest$inventory.getItems());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void camelchest$loadData(ValueInput input, CallbackInfo ci) {
        this.camelchest$hasChest = input.getBooleanOr("CamelHasChest", false);
        this.camelchest$inventory.clearContent();
        if (this.camelchest$hasChest) {
            input.child("CamelChestItems").ifPresent(
                    childInput -> ContainerHelper.loadAllItems(childInput, this.camelchest$inventory.getItems()));
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (this.camelchest$hasChest) {
            this.spawnAtLocation(level, Items.CHEST);
            Containers.dropContents(level, this, this.camelchest$inventory);
            this.camelchest$hasChest = false;
        }
    }
}