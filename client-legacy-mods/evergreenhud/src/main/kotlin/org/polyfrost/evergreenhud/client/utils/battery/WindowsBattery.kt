package org.polyfrost.evergreenhud.client.utils.battery

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure

@Suppress("ClassName", "FunctionName", "PropertyName")
object WindowsBattery {
    fun obtain(): Battery {
        val status = SYSTEM_POWER_STATUS()
        if (!Kernel32.INSTANCE.GetSystemPowerStatus(status)) {
            return Battery.UnknownBattery
        }

        if (!status.hasBattery) {
            return Battery.UnknownBattery
        }

        return status
    }

    private interface Kernel32 : Library {
        companion object {
            val INSTANCE: Kernel32 = Native.loadLibrary("Kernel32", Kernel32::class.java) as Kernel32
        }

        fun GetSystemPowerStatus(result: SYSTEM_POWER_STATUS): Boolean
    }

    @Suppress("unused")
    class SYSTEM_POWER_STATUS : Battery, Structure() {
        @JvmField var ACLineStatus: Byte = 0
        @JvmField var BatteryFlag: Byte = 0
        @JvmField var BatteryLifePercent: Byte = 0
        @JvmField var SystemStatusFlag: Byte = 0
        @JvmField var BatteryLifeTime: Int = 0
        @JvmField var BatteryFullLifeTime: Int = 0

        val hasBattery
            get() = BatteryFlag.toInt() and 0xFF != 0xFF &&
                BatteryFlag.toInt() and 0x80 == 0 &&
                BatteryLifePercent.toInt() and 0xFF != 0xFF

        override val percentage get() = (BatteryLifePercent.toInt() and 0xFF).coerceIn(0, 100)

        override val isCharging get() = ACLineStatus == 1.toByte() || BatteryFlag.toInt() and 0x08 != 0
        override val lifetimeRemaining get() = BatteryLifeTime
        override val isBatterySaverEnabled get() = SystemStatusFlag.toInt() and 0x01 != 0

        override fun getFieldOrder(): List<String> {
            return listOf(
                "ACLineStatus",
                "BatteryFlag",
                "BatteryLifePercent",
                "SystemStatusFlag",
                "BatteryLifeTime",
                "BatteryFullLifeTime"
            )
        }
    }
}
