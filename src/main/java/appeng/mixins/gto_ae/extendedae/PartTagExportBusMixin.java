package appeng.mixins.gto_ae.extendedae;

import com.glodblock.github.extendedae.common.parts.PartTagExportBus;
import com.glodblock.github.extendedae.common.parts.base.PartSpecialExportBus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gto_ae.compat.extendedae.EAEStackTransferContext;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.parts.IPartItem;

@Mixin(PartTagExportBus.class)
public abstract class PartTagExportBusMixin extends PartSpecialExportBus {

    public PartTagExportBusMixin(IPartItem<?> partItem) {
        super(partItem);
    }

    @Inject(method = "createTransferContext", at = @At("RETURN"), remap = false, cancellable = true)
    private void createTransferContext(IStorageService storageService, IEnergyService energyService,
            CallbackInfoReturnable<StackTransferContext> cir) {
        var context = cir.getReturnValue();
        if (context instanceof EAEStackTransferContext eae) {
            eae.gto$ae$setStats(throughputCounter);
        }
        cir.setReturnValue(context);
    }
}
