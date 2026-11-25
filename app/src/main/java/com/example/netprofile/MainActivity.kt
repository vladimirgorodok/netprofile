package com.example.netprofile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.netprofile.device.ActionRunner
import com.example.netprofile.device.DeviceReader
import com.example.netprofile.device.Step
import com.example.netprofile.domain.Check
import com.example.netprofile.domain.Role
import com.example.netprofile.domain.Rules
import com.example.netprofile.domain.Status

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }

    @Composable
    private fun App() {
        var role by remember { mutableStateOf<Role?>(null) }
        val current = role
        if (current == null) {
            RolePicker(onPick = { role = it })
        } else {
            ChecksScreen(role = current, onBack = { role = null })
        }
    }
}

@Composable
private fun RolePicker(onPick: (Role) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Как будет использоваться устройство?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onPick(Role.SHARE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Раздаю интернет через точку доступа")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onPick(Role.RECEIVE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Принимаю интернет через точку доступа")
        }
    }
}

@Composable
private fun ChecksScreen(role: Role, onBack: () -> Unit) {
    val context = LocalContext.current
    val checks = remember(role) {
        Rules.evaluate(role, DeviceReader.read(context))
    }
    val selected = remember(role) {
        mutableStateListOf<String>().apply {
            addAll(checks.filter { it.preselected }.map { it.id })
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (role == Role.SHARE) "Раздача интернета" else "Приём интернета",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(checks, key = { it.id }) { check ->
                CheckCard(
                    check = check,
                    checked = check.id in selected,
                    onToggle = { on ->
                        if (on) selected.add(check.id) else selected.remove(check.id)
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val queue = remember { mutableStateListOf<Step>() }
        val first = queue.firstOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Назад") }
            if (first == null) {
                Button(
                    onClick = {
                        val chosen = checks.filter { it.id in selected && it.canApply }
                        queue.addAll(ActionRunner.plan(context, chosen).steps)
                        queue.firstOrNull()?.let { context.startActivity(it.intent) }
                    },
                    enabled = selected.any { id -> checks.any { it.id == id && it.canApply } },
                ) { Text("Выполнить") }
            } else {
                Button(
                    onClick = {
                        queue.removeAt(0)
                        queue.firstOrNull()?.let { context.startActivity(it.intent) }
                    },
                ) {
                    Text(if (queue.size > 1) "Далее (${queue.size - 1} осталось)" else "Готово")
                }
            }
        }
    }
}

@Composable
private fun CheckCard(check: Check, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val (bg, label) = when (check.status) {
        Status.OK -> Color(0xFFE8F5E9) to "OK"
        Status.WARN -> Color(0xFFFFF3E0) to "Не оптимально"
        Status.BAD -> Color(0xFFFFEBEE) to "Неправильно"
        Status.INFO -> Color(0xFFE3F2FD) to "Проверьте вручную"
    }
    Card(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (check.canApply) {
                Checkbox(checked = checked, onCheckedChange = onToggle)
            } else {
                Spacer(Modifier.padding(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleMedium)
                Text("Сейчас: ${check.current}", style = MaterialTheme.typography.bodyMedium)
                Text("Рекомендовано: ${check.recommended}", style = MaterialTheme.typography.bodyMedium)
                if (check.reason.isNotBlank()) {
                    Text(check.reason, style = MaterialTheme.typography.bodySmall)
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
