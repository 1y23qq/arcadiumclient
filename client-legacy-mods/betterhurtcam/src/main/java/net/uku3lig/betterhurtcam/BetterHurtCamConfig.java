package net.uku3lig.betterhurtcam;

import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;

/**
 * OneConfig presentation and persistence for the unchanged BetterHurtCam TOML schema.
 *
 * <p>The file ID intentionally includes {@code .toml}; OneConfig then reads and writes
 * the same {@code config/betterhurtcam.toml} file used by upstream BetterHurtCam.</p>
 */
public final class BetterHurtCamConfig extends Config {
	public static final BetterHurtCamConfig INSTANCE = new BetterHurtCamConfig();

	@Switch(title = "Enable HurtCam", category = "BetterHurtCam")
	public boolean enabled = true;

	@Number(
		title = "HurtCam multiplier",
		description = "Multiplies the damage camera rotation. Set to 0 to remove it.",
		category = "BetterHurtCam",
		min = -3.4028235E38f,
		max = 3.4028235E38f
	)
	public double multiplier = 0.3D;

	@Switch(
		title = "Health bar blinking",
		description = "Has no effect while OverflowAnimations' Disable Heart Flash is on, as that option takes over.",
		category = "BetterHurtCam"
	)
	public boolean heartBlink = true;

	@Dropdown(
		title = "HurtCam type",
		description = "OLD removes horizontal damage-direction rotation.",
		category = "BetterHurtCam",
		options = {"OLD", "YAW_BASED"}
	)
	public String type = "YAW_BASED";

	@org.polyfrost.oneconfig.api.config.v1.annotations.Include
	public boolean oldTypeIn18 = false;

	private static final boolean OVERFLOW_ANIMATIONS = FabricLoader.getInstance().isModLoaded("overflowanimations");
	private boolean heartBlinkOverridden;

	private BetterHurtCamConfig() {
		super("betterhurtcam.toml", "assets/betterhurtcam/icon.png", "BetterHurtCam", Category.VISUALS);
		addDependency("heartBlink", "OverflowAnimations' Disable Heart Flash being off",
			() -> heartBlinkOverridden ? Property.Display.DISABLED : Property.Display.SHOWN);
	}

	boolean isHeartBlinkOverridden() {
		return OVERFLOW_ANIMATIONS && OverflowAnimationsConfig.instance().screen.disableHeartFlash;
	}

	void refreshHeartBlinkOverride() {
		boolean overridden = isHeartBlinkOverridden();
		if (overridden != heartBlinkOverridden) {
			heartBlinkOverridden = overridden;
			getProperty("heartBlink").revaluateDisplay();
		}
	}

	public HurtCamType getType() {
		return HurtCamType.parse(type);
	}

	public void setType(HurtCamType type) {
		this.type = (type == null ? HurtCamType.YAW_BASED : type).name();
		save();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		save();
	}

	public void setMultiplier(double multiplier) {
		if (Double.isFinite(multiplier)) {
			this.multiplier = multiplier;
			save();
		}
	}

	public void setHeartBlink(boolean heartBlink) {
		this.heartBlink = heartBlink;
		save();
	}
}
