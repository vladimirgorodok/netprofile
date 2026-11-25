package com.example.netprofile.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.netprofile.domain.Apply
import com.example.netprofile.domain.Check

/** Шаг выполнения: системный экран, который открывается по одному. */
data class Step(val check: Check, val intent: Intent)

data class Plan(val steps: List<Step>, val manual: List<Check>)

object ActionRunner {

    /**
     * Android не даёт сторонним приложениям менять эти настройки напрямую,
     * поэтому каждый выбранный пункт превращается в системный экран.
     * Экраны открываются по очереди, так как одновременно открыть несколько нельзя.
     */
    fun plan(context: Context, checks: List<Check>): Plan {
        val steps = mutableListOf<Step>()
        val manual = mutableListOf<Check>()
        val seen = mutableSetOf<String>()

        checks.forEach { check ->
            when (val apply = check.apply) {
                is Apply.Open -> if (seen.add(apply.intentAction)) {
                    steps += Step(check, Intent(apply.intentAction))
                }
                Apply.BatteryExemption -> steps += Step(
                    check,
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}")),
                )
                Apply.Manual -> manual += check
            }
        }
        return Plan(steps, manual)
    }
}
