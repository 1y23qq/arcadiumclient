package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.features.PasswordMask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PasswordMaskTest {
    private val commands = "login, l, register, reg, changepassword"

    @Test fun masksArgumentsAndPreservesCommandAndCursorIndices() {
        for ((input, masked) in listOf(
            "/login secret" to "/login ******",
            "/l a b" to "/l * *",
            "  /LOGIN test" to "  /LOGIN ****",
            "/authme:login a§c123" to "/authme:login ******",
            "/register secret secret" to "/register ****** ******",
            "/changepassword old new" to "/changepassword *** ***",
            "/login パスワード" to "/login *****",
        )) {
            assertEquals(masked, PasswordMask.display(input, commands))
            assertEquals(input.length, masked.length)
        }
    }

    @Test fun leavesOtherInputAndCommandsWithoutArgumentsUnchanged() {
        for (text in listOf("hello secret", "/login", "/login ", "/loginhelp secret", "/play bedwars_eight_one", ""))
            assertEquals(text, PasswordMask.display(text, commands))
        assertEquals("/custom ****", PasswordMask.display("/custom test", "/custom"))
        assertEquals("/login test", PasswordMask.display("/login test", "custom"))
    }
}
