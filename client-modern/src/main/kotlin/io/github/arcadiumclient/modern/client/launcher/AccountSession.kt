// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.launcher

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.yggdrasil.ProfileResult
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import io.github.arcadiumclient.modern.mixin.AccountSessionAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.gui.screens.social.PlayerSocialManager
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment
import net.minecraft.client.multiplayer.chat.report.ReportingContext
import net.minecraft.client.telemetry.ClientTelemetryManager
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

object AccountSession {
    fun activate(account: AccountRepository.StoredAccount) {
        val mc = Minecraft.getInstance()
        check(mc.isSameThread) { "Account activation must run on the game thread." }
        check(mc.level == null && mc.connection == null) { "Leave your current world before switching accounts." }
        val uuid = AccountRepository.parseUuid(account.id)
            ?: UUID.nameUUIDFromBytes("OfflinePlayer:${account.username}".toByteArray(Charsets.UTF_8))
        val online = AccountRepository.isOnline(account)
        require(!online || account.accessToken.isNotBlank()) { "Sign in again before switching accounts." }
        val user = User(account.username, uuid, account.accessToken.ifBlank { "0" }, Optional.empty(), Optional.empty())
        val api = if (online) YggdrasilAuthenticationService(mc.proxy).createUserApiService(account.accessToken)
            else UserApiService.OFFLINE
        val profile = ProfileResult(GameProfile(uuid, account.username))
        val access = mc as AccountSessionAccess
        mc.playerSocialManager.stopOnlineMode()
        access.`arcadium$getTelemetry`().close()
        access.`arcadium$setUser`(user)
        access.`arcadium$setProfile`(CompletableFuture.completedFuture(profile))
        access.`arcadium$setApi`(api)
        access.`arcadium$setProperties`(CompletableFuture.supplyAsync {
            runCatching { api.fetchProperties() }.getOrDefault(UserApiService.OFFLINE_PROPERTIES)
        })
        access.`arcadium$setKeys`(if (online) ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()) else ProfileKeyPairManager.EMPTY_KEY_MANAGER)
        access.`arcadium$setSocial`(PlayerSocialManager(mc, api))
        access.`arcadium$setTelemetry`(ClientTelemetryManager(mc, api, user))
        access.`arcadium$setReporting`(ReportingContext.create(ReportEnvironment.local(), api))
    }
}
