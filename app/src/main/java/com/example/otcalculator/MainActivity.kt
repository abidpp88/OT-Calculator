package com.example.otcalculator

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val Navy = Color(0xFF08265F)
private val RoyalBlue = Color(0xFF123FAF)
private val Green = Color(0xFF0A8F63)
private val PaleBlue = Color(0xFFF3F7FF)
private val SoftGreen = Color(0xFFF0FAF5)
private val Orange = Color(0xFFF47C20)

data class WorkEntry(
    val id: Long,
    val date: String, // yyyy-MM-dd
    val type: String, // OT / Stayback
    val hours: Double,
    val rate: Double,
    val allowance: Double,
    val notes: String = ""
) {
    val workAmount: Double get() = hours * rate
    val dayTotal: Double get() = workAmount + allowance
}

data class AppSettings(
    val otRate: Double = 17.64,
    val staybackRate: Double = 14.70,
    val dailyAllowance: Double = 50.0,
    val defaultSalary: Double = 2446.0
)

data class MonthlyExtras(
    val monthKey: String,
    val salary: Double = 2446.0,
    val ramadan: Double = 0.0,
    val ph: Double = 0.0,
    val split: Double = 0.0,
    val wpc: Double = 0.0
)

private enum class Page { HOME, ADD, SUMMARY, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OTCalculatorApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTCalculatorApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ot_calculator", 0) }

    fun loadSettings(): AppSettings {
        return AppSettings(
            otRate = prefs.getString("otRate", "17.64")?.toDoubleOrNull() ?: 17.64,
            staybackRate = prefs.getString("staybackRate", "14.70")?.toDoubleOrNull() ?: 14.70,
            dailyAllowance = prefs.getString("dailyAllowance", "50.00")?.toDoubleOrNull() ?: 50.0,
            defaultSalary = prefs.getString("defaultSalary", "2446.00")?.toDoubleOrNull() ?: 2446.0
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("otRate", s.otRate.toString())
            .putString("staybackRate", s.staybackRate.toString())
            .putString("dailyAllowance", s.dailyAllowance.toString())
            .putString("defaultSalary", s.defaultSalary.toString())
            .apply()
    }

    fun loadEntries(): List<WorkEntry> {
        val array = JSONArray(prefs.getString("entries", "[]") ?: "[]")
        val list = mutableListOf<WorkEntry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list += WorkEntry(
                id = o.optLong("id", i.toLong()),
                date = o.optString("date"),
                type = o.optString("type", "OT"),
                hours = o.optDouble("hours", 0.0),
                rate = o.optDouble("rate", 0.0),
                allowance = o.optDouble("allowance", 50.0),
                notes = o.optString("notes", "")
            )
        }
        return list.sortedWith(compareByDescending<WorkEntry> { it.date }.thenByDescending { it.id })
    }

    fun saveEntries(list: List<WorkEntry>) {
        val array = JSONArray()
        list.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("date", e.date)
                put("type", e.type)
                put("hours", e.hours)
                put("rate", e.rate)
                put("allowance", e.allowance)
                put("notes", e.notes)
            })
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    fun loadExtras(monthKey: String, defaultSalary: Double): MonthlyExtras {
        val root = JSONObject(prefs.getString("monthlyExtras", "{}") ?: "{}")
        val o = root.optJSONObject(monthKey)
        return MonthlyExtras(
            monthKey = monthKey,
            salary = o?.optDouble("salary", defaultSalary) ?: defaultSalary,
            ramadan = o?.optDouble("ramadan", 0.0) ?: 0.0,
            ph = o?.optDouble("ph", 0.0) ?: 0.0,
            split = o?.optDouble("split", 0.0) ?: 0.0,
            wpc = o?.optDouble("wpc", 0.0) ?: 0.0
        )
    }

    fun saveExtras(extras: MonthlyExtras) {
        val root = JSONObject(prefs.getString("monthlyExtras", "{}") ?: "{}")
        root.put(extras.monthKey, JSONObject().apply {
            put("salary", extras.salary)
            put("ramadan", extras.ramadan)
            put("ph", extras.ph)
            put("split", extras.split)
            put("wpc", extras.wpc)
        })
        prefs.edit().putString("monthlyExtras", root.toString()).apply()
    }

    val monthFormatter = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val monthTitleFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    var settings by remember { mutableStateOf(loadSettings()) }
    var entries by remember { mutableStateOf(loadEntries()) }
    var page by remember { mutableStateOf(Page.HOME) }
    var selectedMonth by remember { mutableStateOf(monthFormatter.format(Date())) }
    var addType by remember { mutableStateOf("OT") }
    var editEntry by remember { mutableStateOf<WorkEntry?>(null) }

    fun monthTitle(key: String): String = try {
        val d = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)
        if (d != null) monthTitleFormatter.format(d) else key
    } catch (_: Exception) { key }

    fun filteredMonthEntries(key: String) = entries.filter { it.date.startsWith(key) }

    fun openAdd(type: String) {
        addType = type
        editEntry = null
        page = Page.ADD
    }

    BackHandler(enabled = page != Page.HOME) {
        page = when (page) {
            Page.ADD -> Page.HOME
            Page.SUMMARY -> Page.HOME
            Page.HISTORY -> Page.HOME
            Page.SETTINGS -> Page.HOME
            Page.HOME -> Page.HOME
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, secondary = Green, background = PaleBlue)
    ) {
        Scaffold(
            containerColor = PaleBlue,
            bottomBar = {
                if (page != Page.ADD) {
                    NavigationBar(containerColor = Navy) {
                        BottomItem(page == Page.HOME, "Home", Icons.Default.Home) { page = Page.HOME }
                        BottomItem(page == Page.SUMMARY, "Summary", Icons.Default.BarChart) { page = Page.SUMMARY }
                        BottomItem(page == Page.HISTORY, "History", Icons.Default.History) { page = Page.HISTORY }
                        BottomItem(page == Page.SETTINGS, "Settings", Icons.Default.Settings) { page = Page.SETTINGS }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.HOME -> HomeScreen(
                        monthTitle = monthTitle(selectedMonth),
                        entries = filteredMonthEntries(selectedMonth),
                        extras = loadExtras(selectedMonth, settings.defaultSalary),
                        onAddOT = { openAdd("OT") },
                        onAddStayback = { openAdd("Stayback") },
                        onSummary = { page = Page.SUMMARY },
                        onHistory = { page = Page.HISTORY },
                        onSettings = { page = Page.SETTINGS }
                    )
                    Page.ADD -> AddEntryScreen(
                        initialType = addType,
                        settings = settings,
                        editing = editEntry,
                        onBack = { page = Page.HOME },
                        onSave = { newEntry ->
                            entries = if (editEntry == null) {
                                (entries + newEntry).sortedWith(compareByDescending<WorkEntry> { it.date }.thenByDescending { it.id })
                            } else {
                                entries.map { if (it.id == newEntry.id) newEntry else it }
                            }
                            saveEntries(entries)
                            selectedMonth = newEntry.date.take(7)
                            page = Page.SUMMARY
                        }
                    )
                    Page.SUMMARY -> SummaryScreen(
                        monthKey = selectedMonth,
                        monthTitle = monthTitle(selectedMonth),
                        entries = filteredMonthEntries(selectedMonth),
                        extras = loadExtras(selectedMonth, settings.defaultSalary),
                        onExtrasSave = { saveExtras(it) },
                        onMonthChange = { selectedMonth = it },
                        onEditEntry = {
                            editEntry = it
                            addType = it.type
                            page = Page.ADD
                        },
                        onDeleteEntry = { target ->
                            entries = entries.filterNot { it.id == target.id }
                            saveEntries(entries)
                        }
                    )
                    Page.HISTORY -> HistoryScreen(
                        entries = entries,
                        defaultSalary = settings.defaultSalary,
                        loadExtras = { loadExtras(it, settings.defaultSalary) },
                        monthTitle = ::monthTitle,
                        onOpenMonth = {
                            selectedMonth = it
                            page = Page.SUMMARY
                        }
                    )
                    Page.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onSave = {
                            settings = it
                            saveSettings(it)
                            page = Page.HOME
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomItem(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF90EE90),
            selectedTextColor = Color.White,
            unselectedIconColor = Color.White,
            unselectedTextColor = Color.White,
            indicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun HomeScreen(
    monthTitle: String,
    entries: List<WorkEntry>,
    extras: MonthlyExtras,
    onAddOT: () -> Unit,
    onAddStayback: () -> Unit,
    onSummary: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    val totalHours = entries.sumOf { it.hours }
    val workAmount = entries.sumOf { it.workAmount }
    val allowances = entries.sumOf { it.allowance }
    val grand = extras.salary + extras.ramadan + extras.ph + extras.split + extras.wpc + workAmount + allowances

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("OT Calculator", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Track OT & Stayback easily", color = Color(0xFFDDE7FF))
                    }
                    Icon(Icons.Default.Calculate, null, tint = Color.White, modifier = Modifier.size(58.dp))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(monthTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Total Hours", "${money(totalHours)} h", Green)
                        Metric("Work + Allow.", "AED ${money(workAmount + allowances)}", RoyalBlue)
                        Metric("Grand Total", "AED ${money(grand)}", Orange)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddOT, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) {
                    Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Add OT")
                }
                Button(onClick = onAddStayback, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                    Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Add Stayback")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuCard("Monthly Summary", Icons.Default.Assessment, onSummary, Modifier.weight(1f))
                MenuCard("History", Icons.Default.History, onHistory, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuCard("Calendar View", Icons.Default.CalendarMonth, onSummary, Modifier.weight(1f))
                MenuCard("Settings", Icons.Default.Settings, onSettings, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CardGiftcard, null, tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Daily Allowance", fontWeight = FontWeight.Bold, color = Green)
                        Text("AED 50 is added automatically for every OT or Stayback day.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 115.dp)) {
        Text(label, fontSize = 12.sp, color = Color.DarkGray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun MenuCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RoyalBlue)
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreen(
    initialType: String,
    settings: AppSettings,
    editing: WorkEntry?,
    onBack: () -> Unit,
    onSave: (WorkEntry) -> Unit
) {
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var type by remember { mutableStateOf(editing?.type ?: initialType) }
    var date by remember { mutableStateOf(editing?.date ?: today) }
    var hoursText by remember { mutableStateOf(editing?.hours?.toString() ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    val hours = hoursText.toDoubleOrNull() ?: 0.0
    val rate = if (type == "OT") settings.otRate else settings.staybackRate
    val workAmount = hours * rate
    val total = workAmount + settings.dailyAllowance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "Add OT / Stayback" else "Edit Entry", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("OT", type == "OT", Modifier.weight(1f)) { type = "OT" }
                    ChoiceButton("Stayback", type == "Stayback", Modifier.weight(1f)) { type = "Stayback" }
                }
            }
            item {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val cal = Calendar.getInstance()
                        try {
                            val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                            if (d != null) cal.time = d
                        } catch (_: Exception) {}
                        DatePickerDialog(context, { _, y, m, day ->
                            date = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, day)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    label = { Text("Date") },
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, null) }
                )
            }
            item {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { if (it.matches(Regex("\\d*\\.?\\d*"))) hoursText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hours") },
                    suffix = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
            item { AmountInfo("${type} Rate (per hour)", "AED ${money(rate)}", PaleBlue) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AmountLine("$type Amount", "AED ${money(workAmount)}")
                        AmountLine("Daily Allowance", "AED ${money(settings.dailyAllowance)}")
                        HorizontalDivider()
                        AmountLine("Total for Day", "AED ${money(total)}", true, Green)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    label = { Text("Notes (Optional)") }
                )
            }
            item {
                Button(
                    onClick = {
                        if (hours > 0) {
                            onSave(WorkEntry(
                                id = editing?.id ?: System.currentTimeMillis(),
                                date = date,
                                type = type,
                                hours = hours,
                                rate = rate,
                                allowance = settings.dailyAllowance,
                                notes = notes.trim()
                            ))
                        }
                    },
                    enabled = hours > 0,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Save Entry") }
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun AmountInfo(label: String, value: String, bg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label); Text(value, fontWeight = FontWeight.Bold, color = RoyalBlue)
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, bold: Boolean = false, color: Color = Color.Black) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryScreen(
    monthKey: String,
    monthTitle: String,
    entries: List<WorkEntry>,
    extras: MonthlyExtras,
    onExtrasSave: (MonthlyExtras) -> Unit,
    onMonthChange: (String) -> Unit,
    onEditEntry: (WorkEntry) -> Unit,
    onDeleteEntry: (WorkEntry) -> Unit
) {
    var showPayments by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val ot = entries.filter { it.type == "OT" }
    val stay = entries.filter { it.type == "Stayback" }
    val otAmount = ot.sumOf { it.workAmount }
    val stayAmount = stay.sumOf { it.workAmount }
    val allowance = entries.sumOf { it.allowance }
    val grand = extras.salary + extras.ramadan + extras.ph + extras.split + extras.wpc + otAmount + stayAmount + allowance

    if (showPayments) {
        ExtrasDialog(extras, onDismiss = { showPayments = false }) {
            onExtrasSave(it); showPayments = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(monthTitle, color = Color.White) },
            actions = {
                IconButton(onClick = { onMonthChange(previousMonth(monthKey)) }) { Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White) }
                IconButton(onClick = { onMonthChange(nextMonth(monthKey)) }) { Icon(Icons.Default.ChevronRight, "Next", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Summary") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Entries") })
            }
            if (tab == 0) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Monthly Summary Overview", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
                                SummaryMoneyRow("Salary", extras.salary, RoyalBlue)
                                SummaryMoneyRow("Ramadan", extras.ramadan, Green)
                                SummaryMoneyRow("PH", extras.ph, Color(0xFF7B4FCB))
                                SummaryMoneyRow("Split", extras.split, Orange)
                                SummaryMoneyRow("WPC", extras.wpc, Color(0xFFC2185B))
                                HorizontalDivider()
                                SummaryMoneyRow("Total OT Amount", otAmount, Green)
                                SummaryMoneyRow("Total Stayback Amount", stayAmount, RoyalBlue)
                                SummaryMoneyRow("Total Allowance", allowance, Orange)
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Green)
                                Text("AED ${money(grand)}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Green)
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Work Summary", fontWeight = FontWeight.Bold, color = Navy)
                                AmountLine("Total OT Hours", "${money(ot.sumOf { it.hours })} h")
                                AmountLine("Total Stayback Hours", "${money(stay.sumOf { it.hours })} h")
                                AmountLine("Total Working Days", entries.size.toString())
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { showPayments = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit Salary / Ramadan / PH / Split / WPC")
                        }
                    }
                }
            } else {
                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No entries for this month") }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(entries.sortedByDescending { it.date }, key = { it.id }) { e ->
                            EntryCard(e, onEdit = { onEditEntry(e) }, onDelete = { onDeleteEntry(e) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMoneyRow(label: String, amount: Double, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Text("AED ${money(amount)}", fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun EntryCard(e: WorkEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(prettyDate(e.date), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${e.type} • ${money(e.hours)} hrs • AED ${money(e.dayTotal)}", color = if (e.type == "OT") Green else RoyalBlue)
                if (e.notes.isNotBlank()) Text(e.notes, fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFB00020)) }
        }
    }
}

@Composable
private fun ExtrasDialog(extras: MonthlyExtras, onDismiss: () -> Unit, onSave: (MonthlyExtras) -> Unit) {
    var salary by remember { mutableStateOf(extras.salary.toString()) }
    var ramadan by remember { mutableStateOf(extras.ramadan.toString()) }
    var ph by remember { mutableStateOf(extras.ph.toString()) }
    var split by remember { mutableStateOf(extras.split.toString()) }
    var wpc by remember { mutableStateOf(extras.wpc.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Payments") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Salary", salary) { salary = it }
                MoneyField("Ramadan", ramadan) { ramadan = it }
                MoneyField("PH", ph) { ph = it }
                MoneyField("Split", split) { split = it }
                MoneyField("WPC", wpc) { wpc = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(MonthlyExtras(
                    monthKey = extras.monthKey,
                    salary = salary.toDoubleOrNull() ?: 0.0,
                    ramadan = ramadan.toDoubleOrNull() ?: 0.0,
                    ph = ph.toDoubleOrNull() ?: 0.0,
                    split = split.toDoubleOrNull() ?: 0.0,
                    wpc = wpc.toDoubleOrNull() ?: 0.0
                ))
            }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.matches(Regex("\\d*\\.?\\d*"))) onChange(it) },
        label = { Text(label) },
        prefix = { Text("AED ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    entries: List<WorkEntry>,
    defaultSalary: Double,
    loadExtras: (String) -> MonthlyExtras,
    monthTitle: (String) -> String,
    onOpenMonth: (String) -> Unit
) {
    val months = entries.map { it.date.take(7) }.distinct().sortedDescending()
    Scaffold(topBar = {
        TopAppBar(title = { Text("History", color = Color.White) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy))
    }) { padding ->
        if (months.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No history yet") }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(months) { key ->
                    val list = entries.filter { it.date.startsWith(key) }
                    val ex = loadExtras(key)
                    val total = list.sumOf { it.dayTotal } + ex.salary + ex.ramadan + ex.ph + ex.split + ex.wpc
                    Card(Modifier.fillMaxWidth().clickable { onOpenMonth(key) }, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = RoyalBlue)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(monthTitle(key), fontWeight = FontWeight.Bold)
                                Text("${list.size} working days", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("AED ${money(total)}", fontWeight = FontWeight.Bold, color = Green)
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var ot by remember { mutableStateOf(settings.otRate.toString()) }
    var stay by remember { mutableStateOf(settings.staybackRate.toString()) }
    var allowance by remember { mutableStateOf(settings.dailyAllowance.toString()) }
    var salary by remember { mutableStateOf(settings.defaultSalary.toString()) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings", color = Color.White) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy))
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Rates", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy) }
            item { MoneyField("OT Rate (per hour)", ot) { ot = it } }
            item { MoneyField("Stayback Rate (per hour)", stay) { stay = it } }
            item { MoneyField("Daily Allowance", allowance) { allowance = it } }
            item { Text("Default Payment", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy) }
            item { MoneyField("Salary", salary) { salary = it } }
            item {
                Button(
                    onClick = {
                        onSave(AppSettings(
                            otRate = ot.toDoubleOrNull() ?: 17.64,
                            staybackRate = stay.toDoubleOrNull() ?: 14.70,
                            dailyAllowance = allowance.toDoubleOrNull() ?: 50.0,
                            defaultSalary = salary.toDoubleOrNull() ?: 2446.0
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Save Settings") }
            }
        }
    }
}

private fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

private fun prettyDate(value: String): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val output = SimpleDateFormat("dd MMM yyyy (EEE)", Locale.getDefault())
    output.format(input.parse(value) ?: return value)
} catch (_: Exception) { value }

private fun previousMonth(key: String): String = shiftMonth(key, -1)
private fun nextMonth(key: String): String = shiftMonth(key, 1)
private fun shiftMonth(key: String, amount: Int): String = try {
    val cal = Calendar.getInstance()
    cal.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: Date()
    cal.add(Calendar.MONTH, amount)
    SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
} catch (_: Exception) { key }
