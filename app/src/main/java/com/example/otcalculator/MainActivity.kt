package com.example.otcalculator
 
import android.app.DatePickerDialog
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.activity.compose.rememberLauncherForActivityResult
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
import kotlin.math.roundToInt
 
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
    val defaultSalary: Double = 2446.0,
    val monthlyTarget: Double = 2000.0
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
            defaultSalary = prefs.getString("defaultSalary", "2446.00")?.toDoubleOrNull() ?: 2446.0,
            monthlyTarget = prefs.getString("monthlyTarget", "2000.00")?.toDoubleOrNull() ?: 2000.0
        )
    }
 
    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("otRate", s.otRate.toString())
            .putString("staybackRate", s.staybackRate.toString())
            .putString("dailyAllowance", s.dailyAllowance.toString())
            .putString("defaultSalary", s.defaultSalary.toString())
            .putString("monthlyTarget", s.monthlyTarget.toString())
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
                        existingEntries = entries,
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
                        monthlyTarget = settings.monthlyTarget,
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
                        },
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
                    Page.SETTINGS -> SettingsScreen(
                        settings = settings,
                        entries = entries,
                        onRestoreEntries = {
                            entries = it.sortedWith(compareByDescending<WorkEntry> { e -> e.date }.thenByDescending { e -> e.id })
                            saveEntries(entries)
                        },
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
    existingEntries: List<WorkEntry>,
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
    var pendingDuplicate by remember { mutableStateOf<WorkEntry?>(null) }
 
    if (pendingDuplicate != null) {
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            title = { Text("Duplicate Entry Warning") },
            text = { Text("An entry already exists for $date. Save another $type entry for the same date?") },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pendingDuplicate
                    pendingDuplicate = null
                    if (entry != null) onSave(entry)
                }) { Text("SAVE ANYWAY") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuplicate = null }) { Text("CANCEL") }
            }
        )
    }
 
 
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Date") },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null) }
                    )
 
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                val cal = Calendar.getInstance()
                                try {
                                    val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                                    if (d != null) cal.time = d
                                } catch (_: Exception) {}
 
                                DatePickerDialog(
                                    context,
                                    { _, y, m, day ->
                                        date = String.format(
                                            Locale.getDefault(),
                                            "%04d-%02d-%02d",
                                            y,
                                            m + 1,
                                            day
                                        )
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    )
                }
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
                            val newEntry = WorkEntry(
                                id = editing?.id ?: System.currentTimeMillis(),
                                date = date,
                                type = type,
                                hours = hours,
                                rate = rate,
                                allowance = settings.dailyAllowance,
                                notes = notes.trim()
                            )
                            val duplicate = existingEntries.any {
                                it.date == date && it.id != editing?.id
                            }
                            if (duplicate) pendingDuplicate = newEntry else onSave(newEntry)
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
    monthlyTarget: Double,
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
                        val earned = otAmount + stayAmount + allowance
                        val progress = if (monthlyTarget > 0) (earned / monthlyTarget).coerceIn(0.0, 1.0).toFloat() else 0f
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Monthly Target", fontWeight = FontWeight.Bold, color = Navy)
                                Text("AED ${money(earned)} / AED ${money(monthlyTarget)}", fontWeight = FontWeight.SemiBold)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text("${(progress * 100).roundToInt()}% completed", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                    item {
                        CalendarMonthCard(monthKey = monthKey, entries = entries)
                    }
                    item {
                        MonthlyPdfButton(monthTitle, entries, extras)
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
    onOpenMonth: (String) -> Unit,
    onEditEntry: (WorkEntry) -> Unit,
    onDeleteEntry: (WorkEntry) -> Unit
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(Modifier.fillMaxWidth().clickable { onOpenMonth(key) }, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = RoyalBlue)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(monthTitle(key), fontWeight = FontWeight.Bold)
                                    Text("${list.size} entries", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text("AED ${money(total)}", fontWeight = FontWeight.Bold, color = Green)
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                        list.sortedByDescending { it.date }.forEach { e ->
                            EntryCard(e, onEdit = { onEditEntry(e) }, onDelete = { onDeleteEntry(e) })
                        }
                    }
                }
            }
        }
    }
}
 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    entries: List<WorkEntry>,
    onRestoreEntries: (List<WorkEntry>) -> Unit,
    onSave: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var ot by remember { mutableStateOf(settings.otRate.toString()) }
    var stay by remember { mutableStateOf(settings.staybackRate.toString()) }
    var allowance by remember { mutableStateOf(settings.dailyAllowance.toString()) }
    var salary by remember { mutableStateOf(settings.defaultSalary.toString()) }
    var target by remember { mutableStateOf(settings.monthlyTarget.toString()) }
 
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val array = JSONArray()
                entries.forEach { e ->
                    array.put(JSONObject().apply {
                        put("id", e.id); put("date", e.date); put("type", e.type)
                        put("hours", e.hours); put("rate", e.rate); put("allowance", e.allowance)
                        put("notes", e.notes)
                    })
                }
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(array.toString(2).toByteArray())
                }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
 
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "[]"
                val array = JSONArray(raw)
                val restored = mutableListOf<WorkEntry>()
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    restored += WorkEntry(
                        id = o.optLong("id", System.currentTimeMillis() + i),
                        date = o.optString("date"),
                        type = o.optString("type", "OT"),
                        hours = o.optDouble("hours", 0.0),
                        rate = o.optDouble("rate", 0.0),
                        allowance = o.optDouble("allowance", settings.dailyAllowance),
                        notes = o.optString("notes", "")
                    )
                }
                onRestoreEntries(restored)
                Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }
 
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings", color = Color.White) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy))
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { Text("Rates", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy) }
            item { MoneyField("OT Rate (per hour)", ot) { ot = it } }
            item { MoneyField("Stayback Rate (per hour)", stay) { stay = it } }
            item { MoneyField("Daily Allowance", allowance) { allowance = it } }
            item { Text("Default Payment & Target", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy) }
            item { MoneyField("Salary", salary) { salary = it } }
            item { MoneyField("Monthly OT / Stayback Target", target) { target = it } }
            item {
                Button(
                    onClick = {
                        onSave(AppSettings(
                            otRate = ot.toDoubleOrNull() ?: 17.64,
                            staybackRate = stay.toDoubleOrNull() ?: 14.70,
                            dailyAllowance = allowance.toDoubleOrNull() ?: 50.0,
                            defaultSalary = salary.toDoubleOrNull() ?: 2446.0,
                            monthlyTarget = target.toDoubleOrNull() ?: 2000.0
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Save Settings") }
            }
            item { HorizontalDivider() }
            item { Text("Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy) }
            item {
                OutlinedButton(
                    onClick = { backupLauncher.launch("OT_Calculator_Backup_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Export Backup") }
            }
            item {
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore Backup") }
            }
        }
    }
}
 
@Composable
private fun CalendarMonthCard(monthKey: String, entries: List<WorkEntry>) {
    val cal = Calendar.getInstance()
    try { cal.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey) ?: Date() } catch (_: Exception) {}
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val byDay = entries.groupBy { it.date.takeLast(2).toIntOrNull() ?: -1 }
 
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calendar View", fontWeight = FontWeight.Bold, color = Navy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M","T","W","T","F","S","S").forEach { Text(it, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray) }
            }
            var day = 1
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val pos = week * 7 + col
                        if (pos < firstDay || day > days) {
                            Box(Modifier.weight(1f).height(38.dp))
                        } else {
                            val d = day
                            val list = byDay[d].orEmpty()
                            val bg = when {
                                list.any { it.type == "OT" } && list.any { it.type == "Stayback" } -> SoftGreen
                                list.any { it.type == "OT" } -> Color(0xFFE8F5E9)
                                list.any { it.type == "Stayback" } -> Color(0xFFE8EEFF)
                                else -> Color.Transparent
                            }
                            Box(
                                Modifier.weight(1f).height(38.dp).padding(2.dp).background(bg, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(d.toString(), fontSize = 12.sp, fontWeight = if (list.isNotEmpty()) FontWeight.Bold else FontWeight.Normal) }
                            day++
                        }
                    }
                }
                if (day > days) break
            }
            Text("Highlighted days contain OT / Stayback entries.", fontSize = 11.sp, color = Color.Gray)
        }
    }
}
 
@Composable
private fun MonthlyPdfButton(monthTitle: String, entries: List<WorkEntry>, extras: MonthlyExtras) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                val pdf = PdfDocument()

                val navy = android.graphics.Color.rgb(8, 38, 95)
                val royal = android.graphics.Color.rgb(18, 63, 175)
                val green = android.graphics.Color.rgb(10, 143, 99)
                val orange = android.graphics.Color.rgb(244, 124, 32)
                val lightBlue = android.graphics.Color.rgb(243, 247, 255)
                val lightGreen = android.graphics.Color.rgb(240, 250, 245)
                val lightOrange = android.graphics.Color.rgb(255, 247, 240)
                val border = android.graphics.Color.rgb(210, 218, 232)
                val darkText = android.graphics.Color.rgb(35, 43, 58)
                val grayText = android.graphics.Color.rgb(105, 112, 125)
                val white = android.graphics.Color.WHITE

                val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9f
                    color = darkText
                }
                val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 7.5f
                    color = grayText
                }
                val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9f
                    color = darkText
                    isFakeBoldText = true
                }
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 24f
                    color = white
                    isFakeBoldText = true
                }
                val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 15f
                    color = white
                }
                val sectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10f
                    color = navy
                    isFakeBoldText = true
                }
                val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8f
                    color = white
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8f
                    color = darkText
                    textAlign = Paint.Align.CENTER
                }
                val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8f
                    color = darkText
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                val moneyGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 18f
                    color = green
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }

                val ot = entries.filter { it.type == "OT" }
                val stay = entries.filter { it.type == "Stayback" }
                val otHours = ot.sumOf { it.hours }
                val stayHours = stay.sumOf { it.hours }
                val otAmount = ot.sumOf { it.workAmount }
                val stayAmount = stay.sumOf { it.workAmount }
                val allowance = entries.sumOf { it.allowance }
                val entriesTotal = entries.sumOf { it.dayTotal }
                val grand = entriesTotal + extras.salary + extras.ramadan + extras.ph + extras.split + extras.wpc

                val sortedEntries = entries.sortedBy { it.date }
                val rowsPerPage = 14
                val chunks = if (sortedEntries.isEmpty()) listOf(emptyList()) else sortedEntries.chunked(rowsPerPage)
                val totalPages = chunks.size

                fun rect(canvas: android.graphics.Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, stroke: Boolean = false) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
                        strokeWidth = 1f
                    }
                    canvas.drawRect(left, top, right, bottom, p)
                }

                fun text(canvas: android.graphics.Canvas, value: String, x: Float, y: Float, p: Paint) {
                    canvas.drawText(value, x, y, p)
                }

                fun header(canvas: android.graphics.Canvas, pageNo: Int) {
                    rect(canvas, 0f, 0f, 595f, 92f, navy)
                    rect(canvas, 0f, 92f, 595f, 95f, green)
                    text(canvas, "OT CALCULATOR", 32f, 42f, title)
                    text(canvas, "Monthly Report", 32f, 68f, subtitle)
                    val generated = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
                    val hp = Paint(small).apply { color = white; textAlign = Paint.Align.RIGHT }
                    text(canvas, "Generated: $generated", 560f, 42f, hp)
                    text(canvas, "Page $pageNo of $totalPages", 560f, 61f, hp)
                }

                fun section(canvas: android.graphics.Canvas, label: String, y: Float) {
                    text(canvas, label, 32f, y, sectionTitle)
                    rect(canvas, 32f, y + 5f, 563f, y + 6f, royal)
                }

                fun summaryCard(
                    canvas: android.graphics.Canvas,
                    left: Float,
                    top: Float,
                    right: Float,
                    bottom: Float,
                    bg: Int,
                    accent: Int,
                    heading: String,
                    line1: String,
                    value1: String,
                    line2: String,
                    value2: String
                ) {
                    rect(canvas, left, top, right, bottom, bg)
                    rect(canvas, left, top, right, top + 3f, accent)
                    val hp = Paint(bold).apply { color = accent; textAlign = Paint.Align.CENTER; textSize = 10f }
                    val lp = Paint(small).apply { textAlign = Paint.Align.CENTER }
                    val vp = Paint(bold).apply { color = accent; textAlign = Paint.Align.CENTER; textSize = 13f }
                    val cx = (left + right) / 2f
                    text(canvas, heading, cx, top + 23f, hp)
                    text(canvas, line1, cx, top + 43f, lp)
                    text(canvas, value1, cx, top + 60f, vp)
                    text(canvas, line2, cx, top + 79f, lp)
                    text(canvas, value2, cx, top + 96f, vp)
                    rect(canvas, left, top, right, bottom, border, true)
                }

                fun footer(canvas: android.graphics.Canvas, pageNo: Int) {
                    rect(canvas, 32f, 813f, 563f, 814f, navy)
                    val fp = Paint(small).apply { color = navy }
                    val fr = Paint(small).apply { color = grayText; textAlign = Paint.Align.RIGHT }
                    text(canvas, "OT Calculator", 32f, 829f, fp)
                    text(canvas, "System generated monthly report", 297.5f, 829f, Paint(small).apply { textAlign = Paint.Align.CENTER; color = grayText })
                    text(canvas, "Page $pageNo of $totalPages", 563f, 829f, fr)
                }

                chunks.forEachIndexed { pageIndex, pageEntries ->
                    val pageNo = pageIndex + 1
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                    val canvas = page.canvas
                    header(canvas, pageNo)

                    var y = 120f
                    if (pageIndex == 0) {
                        section(canvas, "REPORT PERIOD", y)
                        y += 18f
                        rect(canvas, 32f, y, 563f, y + 46f, lightBlue)
                        text(canvas, "Month", 48f, y + 18f, small)
                        text(canvas, monthTitle, 48f, y + 35f, Paint(bold).apply { color = navy })
                        text(canvas, "Total Working Days", 225f, y + 18f, small)
                        text(canvas, entries.size.toString(), 225f, y + 35f, Paint(bold).apply { color = navy })
                        text(canvas, "Currency", 420f, y + 18f, small)
                        text(canvas, "AED", 420f, y + 35f, Paint(bold).apply { color = navy })
                        rect(canvas, 32f, y, 563f, y + 46f, border, true)
                        y += 66f

                        val gap = 10f
                        val cardW = (531f - gap * 2f) / 3f
                        summaryCard(canvas, 32f, y, 32f + cardW, y + 112f, lightBlue, royal,
                            "OVERTIME (OT)", "Total Hours", "${money(otHours)} h", "Total Amount", "AED ${money(otAmount)}")
                        summaryCard(canvas, 32f + cardW + gap, y, 32f + cardW * 2f + gap, y + 112f, lightGreen, green,
                            "STAYBACK", "Total Hours", "${money(stayHours)} h", "Total Amount", "AED ${money(stayAmount)}")
                        summaryCard(canvas, 32f + cardW * 2f + gap * 2f, y, 563f, y + 112f, lightOrange, orange,
                            "DAILY ALLOWANCE", "Total Days", entries.size.toString(), "Total Amount", "AED ${money(allowance)}")
                        y += 132f

                        section(canvas, "MONTHLY EARNINGS SUMMARY", y)
                        y += 18f
                        val earningsTop = y
                        val boxRight = 430f
                        rect(canvas, 32f, earningsTop, boxRight, earningsTop + 74f, android.graphics.Color.WHITE)
                        rect(canvas, 32f, earningsTop, boxRight, earningsTop + 74f, border, true)
                        val labels = listOf("SALARY", "RAMADAN", "PH", "SPLIT", "WPC")
                        val values = listOf(extras.salary, extras.ramadan, extras.ph, extras.split, extras.wpc)
                        val cellW = (boxRight - 32f) / 5f
                        labels.forEachIndexed { i, label ->
                            val cx = 32f + cellW * i + cellW / 2f
                            if (i > 0) rect(canvas, 32f + cellW * i, earningsTop, 33f + cellW * i, earningsTop + 74f, border)
                            text(canvas, label, cx, earningsTop + 24f, Paint(small).apply { textAlign = Paint.Align.CENTER; color = navy; isFakeBoldText = true })
                            text(canvas, money(values[i]), cx, earningsTop + 51f, Paint(bold).apply { textAlign = Paint.Align.CENTER; color = darkText; textSize = 10f })
                        }
                        rect(canvas, 442f, earningsTop, 563f, earningsTop + 74f, lightGreen)
                        rect(canvas, 442f, earningsTop, 563f, earningsTop + 22f, navy)
                        text(canvas, "GRAND TOTAL", 502.5f, earningsTop + 15f, whiteBold)
                        text(canvas, money(grand), 502.5f, earningsTop + 50f, moneyGreen)
                        text(canvas, "AED", 502.5f, earningsTop + 65f, Paint(bold).apply { color = green; textAlign = Paint.Align.CENTER })
                        rect(canvas, 442f, earningsTop, 563f, earningsTop + 74f, border, true)
                        y += 96f
                    }

                    section(canvas, if (pageIndex == 0) "DETAILED ENTRIES" else "DETAILED ENTRIES - CONTINUED", y)
                    y += 16f

                    val x = floatArrayOf(32f, 55f, 145f, 205f, 250f, 310f, 390f, 470f, 563f)
                    val headers = listOf("#", "DATE", "TYPE", "HOURS", "RATE", "ALLOW.", "WORK AMT", "DAY TOTAL")
                    rect(canvas, 32f, y, 563f, y + 24f, navy)
                    for (i in headers.indices) {
                        val cx = (x[i] + x[i + 1]) / 2f
                        text(canvas, headers[i], cx, y + 16f, whiteBold)
                    }
                    y += 24f

                    pageEntries.forEachIndexed { idx, e ->
                        val rowTop = y
                        val rowBottom = y + 27f
                        val absoluteIndex = pageIndex * rowsPerPage + idx + 1
                        if (absoluteIndex % 2 == 0) rect(canvas, 32f, rowTop, 563f, rowBottom, lightBlue)
                        rect(canvas, 32f, rowTop, 563f, rowBottom, border, true)
                        for (i in 1 until x.size - 1) rect(canvas, x[i], rowTop, x[i] + 0.5f, rowBottom, border)

                        val values = listOf(
                            absoluteIndex.toString(),
                            prettyDate(e.date).substringBefore(" ("),
                            e.type,
                            money(e.hours),
                            money(e.rate),
                            money(e.allowance),
                            money(e.workAmount),
                            money(e.dayTotal)
                        )
                        values.forEachIndexed { i, v ->
                            val p = if (i == 7) Paint(centerBold).apply { color = green } else center
                            text(canvas, v, (x[i] + x[i + 1]) / 2f, rowTop + 17f, p)
                        }
                        y = rowBottom

                        if (e.notes.isNotBlank()) {
                            rect(canvas, 32f, y, 563f, y + 18f, android.graphics.Color.rgb(250, 250, 250))
                            text(canvas, "Note: ${e.notes.take(90)}", 42f, y + 12f, small)
                            y += 18f
                        }
                    }

                    if (pageIndex == totalPages - 1) {
                        y += 12f
                        if (y < 690f) {
                            section(canvas, "SUMMARY CHECK", y)
                            y += 17f
                            rect(canvas, 32f, y, 563f, y + 82f, android.graphics.Color.WHITE)
                            rect(canvas, 32f, y, 563f, y + 82f, border, true)
                            text(canvas, "OT Amount", 48f, y + 20f, normal)
                            text(canvas, "AED ${money(otAmount)}", 200f, y + 20f, Paint(bold).apply { textAlign = Paint.Align.RIGHT })
                            text(canvas, "Stayback Amount", 48f, y + 39f, normal)
                            text(canvas, "AED ${money(stayAmount)}", 200f, y + 39f, Paint(bold).apply { textAlign = Paint.Align.RIGHT })
                            text(canvas, "Daily Allowance", 48f, y + 58f, normal)
                            text(canvas, "AED ${money(allowance)}", 200f, y + 58f, Paint(bold).apply { textAlign = Paint.Align.RIGHT })
                            text(canvas, "Entries Total", 48f, y + 76f, bold)
                            text(canvas, "AED ${money(entriesTotal)}", 200f, y + 76f, Paint(bold).apply { color = green; textAlign = Paint.Align.RIGHT })

                            text(canvas, "Monthly Grand Total", 340f, y + 30f, Paint(bold).apply { color = navy; textAlign = Paint.Align.CENTER })
                            text(canvas, "AED ${money(grand)}", 450f, y + 59f, Paint(moneyGreen))
                        }
                    }

                    footer(canvas, pageNo)
                    pdf.finishPage(page)
                }

                context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                pdf.close()
                Toast.makeText(context, "Professional monthly PDF saved", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "PDF creation failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    OutlinedButton(
        onClick = { launcher.launch("OT_Report_${monthTitle.replace(" ", "_")}.pdf") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.PictureAsPdf, null)
        Spacer(Modifier.width(8.dp))
        Text("Generate Monthly PDF")
    }
}

private fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)
 
private fun prettyDate(value: String): String {
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("dd MMM yyyy (EEE)", Locale.getDefault())
        val parsedDate = input.parse(value)
        if (parsedDate != null) output.format(parsedDate) else value
    } catch (_: Exception) {
        value
    }
}
 
private fun previousMonth(key: String): String = shiftMonth(key, -1)
private fun nextMonth(key: String): String = shiftMonth(key, 1)
private fun shiftMonth(key: String, amount: Int): String = try {
    val cal = Calendar.getInstance()
    cal.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: Date()
    cal.add(Calendar.MONTH, amount)
    SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
} catch (_: Exception) { key }
