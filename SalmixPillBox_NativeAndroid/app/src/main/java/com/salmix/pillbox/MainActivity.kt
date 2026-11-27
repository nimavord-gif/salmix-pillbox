package com.salmix.pillbox

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.salmix.pillbox.ui.*
import com.salmix.pillbox.ui.theme.*
import com.salmix.pillbox.viewmodel.PillViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PillViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SalmixPillBoxTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppScreen(
                        viewModel = viewModel,
                        onCallEmergency = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:115"))
                                startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(this, "شماره‌گیر تلفن روی این دستگاه در دسترس نیست (۱۱۵)", Toast.LENGTH_LONG).show()
                            }
                        },
                        onShareText = { text, chooserTitle ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            startActivity(Intent.createChooser(intent, chooserTitle))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: PillViewModel,
    onCallEmergency: () -> Unit,
    onShareText: (String, String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Today, 1: Alarms, 2: Water, 3: Cabinet, 4: Profile
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val medicines by viewModel.allMedicines.collectAsState()
    val alarmTimes by viewModel.alarmTimes.collectAsState()
    val waterCups by viewModel.waterCups.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()
    val doctorInfo by viewModel.doctorInfo.collectAsState()

    val lowStockCount = remember(medicines) {
        medicines.count { it.isActive && it.stock <= it.minStock }
    }

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
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "دستیار هوشمند سلامت خانواده",
                                color = LightBlue,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onCallEmergency,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "اورژانس ۱۱۵", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardDark,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, Icons.Default.Home, "امروز"),
                    Triple(1, Icons.Default.Notifications, "آلارم‌ها"),
                    Triple(2, Icons.Default.Favorite, "آب"),
                    Triple(3, Icons.Default.List, "کمد"),
                    Triple(4, Icons.Default.Person, "گزارش")
                )
                navItems.forEach { (idx, icon, label) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = {
                            if (idx == 3 && lowStockCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = DangerRed, contentColor = Color.White) {
                                            Text("$lowStockCount")
                                        }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LightBlue,
                            selectedTextColor = LightBlue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = SurfaceDark
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 3) {
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
                    alarmTimes = alarmTimes,
                    streakDays = streakDays,
                    onToggleSlotDose = { med, slotKey, taken -> viewModel.toggleSlotDose(med, slotKey, taken) },
                    onOpenAddDialog = { showAddDialog = true }
                )
                1 -> AlarmTimesScreen(
                    alarmTimes = alarmTimes,
                    onUpdateTime = { slot, h, m -> viewModel.updateAlarmTime(slot, h, m) },
                    onTestAlarm = { viewModel.triggerTestAlarm() },
                    onStopTestAlarm = { viewModel.stopTestAlarm() }
                )
                2 -> WaterTrackerScreen(
                    waterCups = waterCups,
                    onAdd = { viewModel.addWaterCup() },
                    onSub = { viewModel.removeWaterCup() },
                    onReset = { viewModel.resetWaterCups() }
                )
                3 -> MedicineCabinetScreen(
                    medicines = medicines,
                    onRefill = { medId, amount -> viewModel.refillStock(medId, amount) },
                    onToggleActive = { med -> viewModel.toggleMedicineActive(med) },
                    onDelete = { med -> viewModel.deleteMedicine(med) },
                    onOpenAddDialog = { showAddDialog = true }
                )
                4 -> ProfileReportScreen(
                    medicines = medicines,
                    doctorInfo = doctorInfo,
                    onSaveDoctor = { n, p -> viewModel.saveDoctorInfo(n, p) },
                    onShareWhatsApp = { text -> onShareText(text, "ارسال گزارش دارویی") },
                    onExportBackup = { backupText -> onShareText(backupText, "ذخیره نسخه پشتیبان") }
                )
            }
        }
    }

    if (showAddDialog) {
        AddMedicineDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, color, dosage, unit, slots, customTime, freq, days, meal, stock, minStock, expiry, notes ->
                viewModel.addMedicine(
                    name, type, color, dosage, unit, slots, customTime, freq, days, meal, stock, minStock, expiry, notes
                )
                showAddDialog = false
            }
        )
    }
}
