package tektonikal.customblockhighlight.mixin;

//? if <= 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.LiquidBlock;
//? if = 1.8.9
//import tektonikal.customblockhighlight.legacy.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tektonikal.customblockhighlight.legacy.LegacyWorld;

@Mixin(LiquidBlock.class)
public class LiquidBlockMixin {
	@ModifyReturnValue(method = "canRayTrace", at = @At("RETURN"))
	//? if = 1.7.10 {
	private boolean cbh$pickFlowingLiquids(boolean original, int metadata, boolean allowLiquids) {
	//? } else {
	/*private boolean cbh$pickFlowingLiquids(boolean original, BlockState state, boolean allowLiquids) {
	*///? }
		return original || (allowLiquids && LegacyWorld.pickAnyFluid);
	}
}
//?}
