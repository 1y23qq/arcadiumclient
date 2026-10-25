// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface AccountSessionAccess {
    @Mutable @Accessor("user") void arcadium$setUser(User value);
    @Mutable @Accessor("profileFuture") void arcadium$setProfile(CompletableFuture<ProfileResult> value);
    @Mutable @Accessor("userApiService") void arcadium$setApi(UserApiService value);
    @Mutable @Accessor("userPropertiesFuture") void arcadium$setProperties(CompletableFuture<UserApiService.UserProperties> value);
    @Mutable @Accessor("profileKeyPairManager") void arcadium$setKeys(ProfileKeyPairManager value);
    @Mutable @Accessor("playerSocialManager") void arcadium$setSocial(PlayerSocialManager value);
    @Accessor("telemetryManager") ClientTelemetryManager arcadium$getTelemetry();
    @Mutable @Accessor("telemetryManager") void arcadium$setTelemetry(ClientTelemetryManager value);
    @Accessor("reportingContext") void arcadium$setReporting(ReportingContext value);
}
