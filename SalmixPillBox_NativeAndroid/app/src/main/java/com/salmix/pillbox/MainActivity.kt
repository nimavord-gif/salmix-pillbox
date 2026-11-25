package com.salmix.pillbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmix.pillbox.data.Medicine
import com.salmix.pillbox.ui.theme.*
import com.salmix.pillbox.viewmodel.PillViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SalmixPillBoxTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    onCallEmergency = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:115"))
                        startActivity(intent)
                    },
                    onShareWhatsApp = { text ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(intent, "ارسال نسخه دارویی"))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: PillViewModel,
    onCallEmergency: () -> Unit,
    onShareWhatsApp: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Today, 1: Water, 2: Cabinet
    var showAddDialog by remember { mutableStateOf(false) }

    val medicines by viewModel.allMedicines.collectAsState()
    val waterCups by viewModel.waterCups.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardDark),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.salmix_logo),
                            contentDescription = "Salmix",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "دارویار سالمیکس",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Salmix Health • نسخه واقعی کاتلین",
                                color = LightBlue,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onCallEmergency,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                    ) {
                        Text(text = "🚑 ۱۱۵", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardDark,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Medication, contentDescription = "برنامه") },
                    label = { Text("برنامه امروز", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LightBlue,
                        selectedTextColor = LightBlue,
                        indicatorColor = SurfaceDark
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.WaterDrop, contentDescription = "آب") },
                    label = { Text("نوشیدن آب", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LightBlue,
                        selectedTextColor = LightBlue,
                        indicatorColor = SurfaceDark
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = "کمد") },
                    label = { Text("کمد داروها", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LightBlue,
                        selectedTextColor = LightBlue,
                        indicatorColor = SurfaceDark
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 2) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = PrimaryBlue,
                    contentColor = TextWhite,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن دارو", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TodayScheduleScreen(
                    medicines = medicines,
                    streakDays = streakDays,
                    onToggleDose = { med, taken -> viewModel.toggleDose(med, taken) }
                )
                1 -> WaterTrackerScreen(
                    waterCups = waterCups,
                    onAdd = { viewModel.addWaterCup() },
                    onSub = { viewModel.removeWaterCup() }
                )
                2 -> MedicineCabinetScreen(
                    medicines = medicines,
                    onRefill = { medId -> viewModel.refillStock(medId, 30) },
                    onDelete = { med -> viewModel.deleteMedicine(med) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddMedicineDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, color, dosage, slots, meal, stock, minStock, notes ->
                viewModel.addMedicine(name, type, color, dosage, slots, meal, stock, minStock, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TodayScheduleScreen(
    medicines: List<Medicine>,
    streakDays: Int,
    onToggleDose: (Medicine, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Mascot Card
            MascotCard()
        }

        item {
            // Today Progress Banner
            TodayProgressCard(medicines = medicines, streakDays = streakDays)
        }

        // Slots: Morning, Noon, Night, Bed
        val slots = listOf(
            Triple("morning", "🌅 نوبت صبح", "۰۸:۳۰"),
            Triple("noon", "☀️ نوبت ظهر", "۱۳:۳۰"),
            Triple("night", "🌙 نوبت شب", "۲۰:۳۰"),
            Triple("bed", "💤 قبل از خواب", "۲۳:۰۰")
        )

        slots.forEach { (slotKey, slotTitle, slotTime) ->
            val slotMeds = medicines.filter { it.timeSlots.contains(slotKey) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$slotTitle ($slotTime)",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${slotMeds.size} دارو",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (slotMeds.isEmpty()) {
                item {
                    Text(
                        text = "دارویی برای این نوبت ثبت نشده است.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                }
            } else {
                items(slotMeds) { med ->
                    NativeDoseCard(med = med, onToggle = { onToggleDose(med, !med.isTakenToday) })
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
fun MascotCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, LightBlue.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.salmix_mascot),
                contentDescription = "دکتر سالمیکس",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, LightBlue, RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "دکتر سالمیکس 🩺",
                    color = LightBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "سلام عزیزم! من در سیستم عامل اندروید مراقب ساعت دقیق داروهایت هستم 👋",
                    color = TextWhite,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun TodayProgressCard(medicines: List<Medicine>, streakDays: Int) {
    val totalDoses = medicines.size
    val takenDoses = medicines.count { it.isTakenToday }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "امروز در یک نگاه", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "🔥 $streakDays روز پایبندی متوالی", color = AccentGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PrimaryBlue.copy(alpha = 0.2f), shape = CircleShape)
                    .border(2.dp, PrimaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$takenDoses/$totalDoses", color = LightBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun NativeDoseCard(med: Medicine, onToggle: () -> Unit) {
    val cardBg = if (med.isTakenToday) SuccessGreen.copy(alpha = 0.08f) else CardDark
    val borderCol = if (med.isTakenToday) SuccessGreen.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                when (med.color) {
                                    "yellow" -> Color(0xFFFACC15)
                                    "pink" -> Color(0xFFF43F5E)
                                    "blue" -> Color(0xFF38BDF8)
                                    "green" -> Color(0xFF34D399)
                                    "orange" -> Color(0xFFFB923C)
                                    else -> Color.White
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = med.name,
                        color = if (med.isTakenToday) TextMuted else TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (med.isTakenToday) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = med.dosage, color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "•", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = med.mealInstruction,
                            color = LightBlue,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (med.isTakenToday) SuccessGreen.copy(alpha = 0.15f) else PrimaryBlue
                ),
                border = if (med.isTakenToday) BorderStroke(1.dp, SuccessGreen) else null
            ) {
                Text(
                    text = if (med.isTakenToday) "✓ مصرف شد" else "مصرف کردم",
                    color = if (med.isTakenToday) SuccessGreen else TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WaterTrackerScreen(waterCups: Int, onAdd: () -> Unit, onSub: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, LightBlue.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💧 رهگیر هوشمند نوشیدن آب",
                    color = LightBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "نوشیدن آب کافی به دفع سموم و اثربخشی داروها کمک می‌کند.",
                    color = TextMuted,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(26.dp))

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(PrimaryBlue.copy(alpha = 0.15f), shape = CircleShape)
                        .border(3.dp, LightBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${waterCups * 12.5.toInt()}%",
                            color = TextWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "$waterCups از ۸ لیوان",
                            color = LightBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSub,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text("- ۱ لیوان", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("+ نوشیدم (۱ لیوان)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MedicineCabinetScreen(
    medicines: List<Medicine>,
    onRefill: (Long) -> Unit,
    onDelete: (Medicine) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "📦 کمد داروها و مدیریت موجودی",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(medicines) { med ->
            val isLow = med.stock <= med.minStock
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = if (isLow) BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = med.name, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "موجودی: ${med.stock} عدد " + if (isLow) "⚠️ نیاز به خرید" else "",
                            color = if (isLow) DangerRed else SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onRefill(med.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                        ) {
                            Text("+ ۳۰ عدد", fontSize = 11.sp)
                        }
                        IconButton(onClick = { onDelete(med) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        type: String,
        color: String,
        dosage: String,
        slots: List<String>,
        meal: String,
        stock: Int,
        minStock: Int,
        notes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("۱ عدد") }
    var meal by remember { mutableStateOf("بعد از غذا") }
    var stockText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت داروی جدید 💊", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دارو (مثال: لوزارتان)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("مقدار مصرف (مثال: ۱ عدد)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meal,
                    onValueChange = { meal = it },
                    label = { Text("دستور مصرف (بعد از غذا)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text("موجودی جعبه (تعداد)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            "قرص",
                            "yellow",
                            dosage,
                            listOf("morning", "night"),
                            meal,
                            stockText.toIntOrNull() ?: 30,
                            5,
                            ""
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("ذخیره در اندروید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextMuted)
            }
        },
        containerColor = CardDark
    )
}
