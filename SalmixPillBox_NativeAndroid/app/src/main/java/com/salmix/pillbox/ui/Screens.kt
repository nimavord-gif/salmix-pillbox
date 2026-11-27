package com.salmix.pillbox.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmix.pillbox.R
import com.salmix.pillbox.data.Medicine
import com.salmix.pillbox.ui.theme.*
import com.salmix.pillbox.util.AlarmScheduler
import com.salmix.pillbox.util.AlarmSlotTimes
import com.salmix.pillbox.util.AppUtils
import java.util.Calendar

@Composable
fun TodayScheduleScreen(
    medicines: List<Medicine>,
    alarmTimes: AlarmSlotTimes,
    streakDays: Int,
    onToggleSlotDose: (Medicine, String, Boolean) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    val todayDate = remember { AppUtils.getTodayDateString() }
    val todayCode = remember { AppUtils.getTodayDayCode() }

    val activeTodayMeds = remember(medicines, todayCode) {
        medicines.filter { med ->
            med.isActive && when (med.frequencyType) {
                "WEEKLY" -> med.daysOfWeek.split(",").map { it.trim() }.contains(todayCode)
                "PRN" -> false
                else -> true
            }
        }
    }

    val (totalDoses, takenDoses) = remember(activeTodayMeds, todayDate) {
        var total = 0
        var taken = 0
        activeTodayMeds.forEach { med ->
            val slots = med.timeSlots.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            total += slots.size
            slots.forEach { slot ->
                if (AppUtils.isSlotTakenToday(med.lastTakenDate, med.takenSlotsToday, slot, todayDate)) {
                    taken++
                }
            }
        }
        total to taken
    }

    val lowStockMed = remember(medicines) {
        medicines.firstOrNull { it.isActive && it.stock <= it.minStock }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "mascot") {
            Spacer(modifier = Modifier.height(6.dp))
            MascotCard(
                isEmpty = medicines.isEmpty(),
                allDoneToday = totalDoses > 0 && takenDoses == totalDoses,
                lowStockMedName = lowStockMed?.name
            )
        }

        item(key = "progress") {
            TodayProgressCard(totalDoses = totalDoses, takenDoses = takenDoses, streakDays = streakDays)
        }

        if (medicines.isEmpty()) {
            item(key = "empty_state") {
                EmptyStateCard(
                    title = "برنامه دارویی شما خالی است",
                    subtitle = "برای شروع، اولین داروی خود را با ساعت دقیق مصرف اضافه کنید تا سر وقت هشدار داده شود.",
                    buttonText = "ثبت اولین دارو",
                    onClick = onOpenAddDialog
                )
            }
        } else {
            val slots = listOf(
                Triple("morning", "🌅 نوبت صبح", alarmTimes.morning),
                Triple("noon", "☀️ نوبت ظهر", alarmTimes.noon),
                Triple("night", "🌙 نوبت شب", alarmTimes.night),
                Triple("bed", "💤 قبل از خواب", alarmTimes.bed)
            )

            slots.forEach { (slotKey, slotTitle, slotTime) ->
                val slotMeds = activeTodayMeds.filter {
                    it.timeSlots.split(",").map { s -> s.trim() }.contains(slotKey)
                }
                if (slotMeds.isNotEmpty()) {
                    item(key = "header_$slotKey") {
                        SlotSectionHeader(title = "$slotTitle ($slotTime)", count = slotMeds.size)
                    }
                    items(items = slotMeds, key = { "${slotKey}_${it.id}" }) { med ->
                        val isTakenThisSlot = AppUtils.isSlotTakenToday(
                            med.lastTakenDate, med.takenSlotsToday, slotKey, todayDate
                        )
                        NativeDoseCard(
                            med = med,
                            slotTime = slotTime,
                            isTakenThisSlot = isTakenThisSlot,
                            onToggle = { onToggleSlotDose(med, slotKey, !isTakenThisSlot) }
                        )
                    }
                }
            }

            // Custom exact-time medicines
            val customMeds = activeTodayMeds.filter { it.timeSlots.contains("custom") }
            if (customMeds.isNotEmpty()) {
                item(key = "header_custom") {
                    SlotSectionHeader(title = "⏰ ساعت‌های اختصاصی", count = customMeds.size)
                }
                items(items = customMeds, key = { "custom_${it.id}" }) { med ->
                    val isTakenThisSlot = AppUtils.isSlotTakenToday(
                        med.lastTakenDate, med.takenSlotsToday, "custom", todayDate
                    )
                    NativeDoseCard(
                        med = med,
                        slotTime = med.customTime,
                        isTakenThisSlot = isTakenThisSlot,
                        onToggle = { onToggleSlotDose(med, "custom", !isTakenThisSlot) }
                    )
                }
            }

            // PRN / As-Needed active medicines
            val prnMeds = medicines.filter { it.isActive && it.frequencyType == "PRN" }
            if (prnMeds.isNotEmpty()) {
                item(key = "header_prn") {
                    SlotSectionHeader(title = "🩹 در صورت نیاز (PRN)", count = prnMeds.size)
                }
                items(items = prnMeds, key = { "prn_${it.id}" }) { med ->
                    val isTakenThisSlot = AppUtils.isSlotTakenToday(
                        med.lastTakenDate, med.takenSlotsToday, "prn", todayDate
                    )
                    NativeDoseCard(
                        med = med,
                        slotTime = "در صورت نیاز",
                        isTakenThisSlot = isTakenThisSlot,
                        onToggle = { onToggleSlotDose(med, "prn", !isTakenThisSlot) }
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(82.dp))
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, LightBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("💊", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SlotSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp)) {
            Text(
                text = "$count دارو",
                color = TextMuted,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun MascotCard(
    isEmpty: Boolean,
    allDoneToday: Boolean,
    lowStockMedName: String?
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val speechText = when {
        isEmpty -> "سلام! من دکتر سالمیکس هستم. داروهایت را ثبت کن تا سر ساعت خبرت کنم 👋"
        allDoneToday -> "آفرین! تمام نوبت‌های داروی امروزت را با موفقیت مصرف کردی 🌟"
        lowStockMedName != null -> "توجه! موجودی داروی «$lowStockMedName» رو به اتمام است. یادت نره تهیه کنی 📦"
        hour < 12 -> "صبح بخیر! نوبت صبح داروهایت یادت نره؛ با یک لیوان آب میل کن ☀️"
        hour >= 20 -> "شب بخیر! داروهای نوبت شب و قبل از خواب را با آرامش مصرف کن 🌙"
        else -> "سلام عزیزم! مراقب ساعت دقیق داروهایت هستم. اگر نوبتی گذشت، دوز را دو برابر نکنید 👋"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, (if (allDoneToday) SuccessGreen else LightBlue).copy(alpha = 0.35f))
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
                    .border(2.dp, if (allDoneToday) SuccessGreen else LightBlue, RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "دکتر سالمیکس 🩺",
                    color = if (allDoneToday) SuccessGreen else LightBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = speechText,
                    color = TextWhite,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun TodayProgressCard(totalDoses: Int, takenDoses: Int, streakDays: Int) {
    val allDone = totalDoses > 0 && takenDoses == totalDoses
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) SuccessGreen.copy(alpha = 0.14f) else CardDark
        ),
        border = if (allDone) BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allDone) "🎉 تمام دوزهای امروز تکمیل شد!" else "امروز در یک نگاه",
                    color = if (allDone) SuccessGreen else TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "🔥 $streakDays روز پایبندی متوالی", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
                    .background((if (allDone) SuccessGreen else PrimaryBlue).copy(alpha = 0.2f), shape = CircleShape)
                    .border(2.dp, if (allDone) SuccessGreen else PrimaryBlue, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$takenDoses/$totalDoses",
                    color = if (allDone) SuccessGreen else LightBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun NativeDoseCard(
    med: Medicine,
    slotTime: String,
    isTakenThisSlot: Boolean,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardBg = if (isTakenThisSlot) SuccessGreen.copy(alpha = 0.08f) else CardDark
    val borderCol = if (isTakenThisSlot) SuccessGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)

    val isMissed = remember(isTakenThisSlot, slotTime) {
        if (isTakenThisSlot || !slotTime.contains(":")) false
        else {
            val (h, m) = AppUtils.parseTime(slotTime)
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val slotMinutes = h * 60 + m
            currentMinutes - slotMinutes > 60
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isTakenThisSlot) 0.85f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (med.type) {
                                "کپسول" -> "💊"
                                "شربت" -> "🧪"
                                "قطره" -> "💧"
                                "آمپول" -> "💉"
                                else -> "⚪"
                            },
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = med.name,
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${med.dosage} ${med.dosageUnit}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "•", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = med.mealInstruction,
                                color = LightBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (med.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📝 ${med.notes}",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTakenThisSlot) SuccessGreen.copy(alpha = 0.18f) else PrimaryBlue
                    ),
                    border = if (isTakenThisSlot) BorderStroke(1.dp, SuccessGreen) else null
                ) {
                    Text(
                        text = if (isTakenThisSlot) "✓ ${med.lastTakenTime}" else "مصرف کردم",
                        color = if (isTakenThisSlot) SuccessGreen else TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isMissed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ زمان این نوبت گذشته است. اگر نزدیک نوبت بعدی هستید، دوز را دو برابر نکنید.",
                    color = AccentGold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
fun AlarmTimesScreen(
    alarmTimes: AlarmSlotTimes,
    onUpdateTime: (slot: String, hour: Int, minute: Int) -> Unit,
    onTestAlarm: () -> Unit,
    onStopTestAlarm: () -> Unit
) {
    val context = LocalContext.current
    val canExact = remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBattery = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                pm.isIgnoringBatteryOptimizations(context.packageName)
            else true
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "alarm_title") {
            Text(
                text = "⏰ تنظیم ساعت دقیق آلارم‌ها",
                color = TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ساعت هر وعده را به دلخواه تنظیم کنید تا دارویار رأس همان دقیقه زنگ بزند.",
                color = TextMuted,
                fontSize = 12.5.sp
            )
        }

        if (!canExact.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "exact_perm_banner") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, DangerRed),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("⚠️ مجوز آلارم دقیق خاموش است!", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("برای زنگ زدن دقیق سر ساعت در اندروید ۱۲ تا ۱۵، این مجوز را فعال کنید.", color = TextWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                        ) {
                            Text("فعال‌سازی آلارم دقیق")
                        }
                    }
                }
            }
        }

        if (!isIgnoringBattery.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            item(key = "battery_banner") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🔋 بهینه‌سازی باتری گوشی (شیائومی / سامسونگ / هواوی)", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("برای جلوگیری از بسته شدن برنامه در پس‌زمینه، برنامه را از محدودیت باتری خارج کنید.", color = TextWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("معافیت از بهینه‌سازی باتری", color = AccentGold)
                        }
                    }
                }
            }
        }

        val itemsList = listOf(
            Triple("morning", "🌅 وعده صبح", alarmTimes.morning),
            Triple("noon", "☀️ وعده ظهر", alarmTimes.noon),
            Triple("night", "🌙 وعده شب", alarmTimes.night),
            Triple("bed", "💤 قبل از خواب", alarmTimes.bed)
        )

        items(items = itemsList, key = { it.first }) { (slotKey, label, currentTime) ->
            val (h, m) = AppUtils.parseTime(currentTime)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, pickedHour, pickedMin -> onUpdateTime(slotKey, pickedHour, pickedMin) },
                            h, m, true
                        ).show()
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LightBlue.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = currentTime,
                            color = LightBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item(key = "test_alarm_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestAlarm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔊 تست صدای زنگ", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStopTestAlarm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔇 توقف تست", color = TextMuted)
                }
            }
        }

        item(key = "spacer_alarm") {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
fun WaterTrackerScreen(
    waterCups: Int,
    onAdd: () -> Unit,
    onSub: () -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val percent = AppUtils.calculateWaterPercent(waterCups, 8)
    val animatedFill by animateFloatAsState(
        targetValue = (waterCups.toFloat() / 8f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "water_fill"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, LightBlue.copy(alpha = 0.25f))
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
                Spacer(modifier = Modifier.height(24.dp))

                // Animated Water Glass Canvas
                Box(
                    modifier = Modifier.size(130.dp, 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val pad = 8f
                        val fillH = (h - pad * 2) * animatedFill

                        // Glass background
                        drawRoundRect(
                            color = SurfaceDark.copy(alpha = 0.5f),
                            topLeft = Offset(pad, pad),
                            size = Size(w - pad * 2, h - pad * 2),
                            cornerRadius = CornerRadius(24f, 24f)
                        )

                        // Water fill
                        if (fillH > 0f) {
                            drawRoundRect(
                                color = if (percent == 100) SuccessGreen.copy(alpha = 0.65f) else PrimaryBlue.copy(alpha = 0.7f),
                                topLeft = Offset(pad, h - pad - fillH),
                                size = Size(w - pad * 2, fillH),
                                cornerRadius = CornerRadius(18f, 18f)
                            )
                        }

                        // Glass border
                        drawRoundRect(
                            color = if (percent == 100) SuccessGreen else LightBlue,
                            topLeft = Offset(pad, pad),
                            size = Size(w - pad * 2, h - pad * 2),
                            cornerRadius = CornerRadius(24f, 24f),
                            style = Stroke(width = 5f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            color = TextWhite,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "$waterCups از ۸ لیوان",
                            color = TextWhite,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSub()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("- ۱", fontSize = 13.sp, color = TextMuted)
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAdd()
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("+ نوشیدم (۱ لیوان)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onReset) {
                    Text("بازنشانی شمارنده آب (۰)", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MedicineCabinetScreen(
    medicines: List<Medicine>,
    onRefill: (Long, Int) -> Unit,
    onToggleActive: (Medicine) -> Unit,
    onDelete: (Medicine) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    var medToDelete by remember { mutableStateOf<Medicine?>(null) }
    var medToRefill by remember { mutableStateOf<Medicine?>(null) }

    val lowCount = remember(medicines) { medicines.count { it.isActive && it.stock <= it.minStock } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "cabinet_header") {
            Text(
                text = "📦 کمد داروها و مدیریت موجودی",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("کل داروها", color = TextMuted, fontSize = 11.5.sp)
                        Text("${medicines.size} قلم", color = LightBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("نیاز به خرید", color = TextMuted, fontSize = 11.5.sp)
                        Text(
                            "$lowCount قلم",
                            color = if (lowCount > 0) DangerRed else SuccessGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        if (medicines.isEmpty()) {
            item(key = "cabinet_empty") {
                EmptyStateCard(
                    title = "کمد داروی شما خالی است",
                    subtitle = "داروهای خود را اضافه کنید تا موجودی، تاریخ انقضا و روزهای باقیمانده را محاسبه کنیم.",
                    buttonText = "افزودن دارو به کمد",
                    onClick = onOpenAddDialog
                )
            }
        } else {
            items(items = medicines, key = { it.id }) { med ->
                val isLow = med.isActive && med.stock <= med.minStock
                val daysLeft = AppUtils.estimateDaysRemaining(med.stock, med.timeSlots, med.frequencyType)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (med.isActive) 1f else 0.6f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = if (isLow) BorderStroke(1.dp, DangerRed.copy(alpha = 0.6f)) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "${med.name} (${med.type})",
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "موجودی: ${med.stock} ${med.dosageUnit}" +
                                        (if (daysLeft > 0) " (~$daysLeft روز)" else "") +
                                        (if (isLow) " ⚠️ نیاز به خرید" else ""),
                                    color = if (isLow) DangerRed else SuccessGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${med.dosage} ${med.dosageUnit} • ${med.mealInstruction}" +
                                        if (med.expiryDate.isNotBlank()) " • انقضا: ${med.expiryDate}" else "",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = med.isActive,
                                onCheckedChange = { onToggleActive(med) }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { medToRefill = med },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                            ) {
                                Text("+ شارژ موجودی", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { medToDelete = med },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف", color = DangerRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item(key = "cabinet_spacer") {
            Spacer(modifier = Modifier.height(82.dp))
        }
    }

    medToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { medToDelete = null },
            title = { Text("تایید حذف دارو", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف کامل «${target.name}» از برنامه مطمئن هستید؟", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target)
                        medToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("بله، حذف شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { medToDelete = null }) {
                    Text("انصراف", color = TextMuted)
                }
            },
            containerColor = CardDark
        )
    }

    medToRefill?.let { target ->
        var amountText by rememberSaveable { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { medToRefill = null },
            title = { Text("افزایش موجودی «${target.name}»", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30).forEach { preset ->
                            FilterChip(
                                selected = AppUtils.parseSafeInt(amountText, 0) == preset,
                                onClick = { amountText = preset.toString() },
                                label = { Text("+$preset") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.length <= 4) amountText = it },
                        label = { Text("تعداد دلخواه") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = AppUtils.parseSafeInt(amountText, defaultValue = 10, min = 1, max = 5000)
                        onRefill(target.id, qty)
                        medToRefill = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("افزودن به موجودی")
                }
            },
            dismissButton = {
                TextButton(onClick = { medToRefill = null }) {
                    Text("انصراف", color = TextMuted)
                }
            },
            containerColor = CardDark
        )
    }
}

@Composable
fun ProfileReportScreen(
    medicines: List<Medicine>,
    doctorInfo: Pair<String, String>,
    onSaveDoctor: (String, String) -> Unit,
    onShareWhatsApp: (String) -> Unit,
    onExportBackup: (String) -> Unit
) {
    var docName by rememberSaveable(doctorInfo.first) { mutableStateOf(doctorInfo.first) }
    var docPhone by rememberSaveable(doctorInfo.second) { mutableStateOf(doctorInfo.second) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "👤 پروفایل سلامت و گزارش پزشک",
            color = TextWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مشخصات پزشک معالج", color = LightBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = docName,
                    onValueChange = { if (it.length <= 60) docName = it },
                    label = { Text("نام پزشک") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = docPhone,
                    onValueChange = { if (it.length <= 20) docPhone = it },
                    label = { Text("شماره تماس مطب") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveDoctor(docName, docPhone) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    Text("ذخیره مشخصات پزشک")
                }
            }
        }

        Button(
            onClick = {
                val sb = StringBuilder()
                sb.append("📋 گزارش دارویی دارویار سالمیکس\n")
                if (docName.isNotBlank()) sb.append("👨‍⚕️ پزشک: $docName\n")
                sb.append("━━━━━━━━━━━━━━\n")
                if (medicines.isEmpty()) {
                    sb.append("هیچ دارویی ثبت نشده است.\n")
                } else {
                    medicines.forEachIndexed { i, m ->
                        sb.append("${i + 1}. ${m.name} (${m.type}) - ${m.dosage} ${m.dosageUnit} | ${m.mealInstruction} | موجودی: ${m.stock}\n")
                    }
                }
                onShareWhatsApp(sb.toString())
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ارسال لیست داروها در واتساپ / پیام‌رسان", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = {
                val backup = medicines.joinToString(separator = "\n") {
                    "${it.name}|${it.type}|${it.dosage}|${it.dosageUnit}|${it.timeSlots}|${it.customTime}|${it.frequencyType}|${it.mealInstruction}|${it.stock}"
                }
                onExportBackup("SalmixPillBox Backup:\n$backup")
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("📤 پشتیبان‌گیری از لیست داروها (Backup)", color = LightBlue)
        }

        Spacer(modifier = Modifier.height(60.dp))
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
        dosageUnit: String,
        slots: List<String>,
        customTime: String,
        frequencyType: String,
        daysOfWeek: String,
        meal: String,
        stock: Int,
        minStock: Int,
        expiryDate: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var name by rememberSaveable { mutableStateOf("") }
    var medType by rememberSaveable { mutableStateOf("قرص") }
    var dosage by rememberSaveable { mutableStateOf("۱") }
    var dosageUnit by rememberSaveable { mutableStateOf("عدد") }
    var meal by rememberSaveable { mutableStateOf("بعد از غذا") }
    var stockText by rememberSaveable { mutableStateOf("30") }
    var expiryText by rememberSaveable { mutableStateOf("") }
    var notesText by rememberSaveable { mutableStateOf("") }
    var frequencyType by rememberSaveable { mutableStateOf("DAILY") }
    var useCustomTime by rememberSaveable { mutableStateOf(false) }
    var customHour by rememberSaveable { mutableStateOf(9) }
    var customMinute by rememberSaveable { mutableStateOf(0) }

    var slotMorning by rememberSaveable { mutableStateOf(true) }
    var slotNoon by rememberSaveable { mutableStateOf(false) }
    var slotNight by rememberSaveable { mutableStateOf(false) }
    var slotBed by rememberSaveable { mutableStateOf(false) }

    var selectedDays by rememberSaveable { mutableStateOf(setOf("SAT")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت داروی جدید 💊", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("نام دارو *") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("شکل دارو:", color = LightBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("قرص", "کپسول", "شربت", "قطره", "آمپول").forEach { t ->
                        FilterChip(
                            selected = medType == t,
                            onClick = { medType = t },
                            label = { Text(t, fontSize = 10.5.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { if (it.length <= 12) dosage = it },
                        label = { Text("مقدار") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dosageUnit,
                        onValueChange = { if (it.length <= 12) dosageUnit = it },
                        label = { Text("واحد (mg/عدد)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("دوره مصرف:", color = LightBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DAILY" to "روزانه", "WEEKLY" to "هفتگی", "PRN" to "در صورت نیاز").forEach { (key, label) ->
                        FilterChip(
                            selected = frequencyType == key,
                            onClick = { frequencyType = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                if (frequencyType == "WEEKLY") {
                    Text("روزهای هفته:", color = TextMuted, fontSize = 11.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("SAT" to "ش", "SUN" to "ی", "MON" to "د", "TUE" to "س", "WED" to "چ", "THU" to "پ", "FRI" to "ج").forEach { (code, lbl) ->
                            val active = selectedDays.contains(code)
                            Surface(
                                color = if (active) PrimaryBlue else SurfaceDark,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        selectedDays = if (active && selectedDays.size > 1) selectedDays - code else selectedDays + code
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(lbl, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (frequencyType != "PRN") {
                    Text("زمان هشدار:", color = LightBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !useCustomTime,
                            onClick = { useCustomTime = false },
                            label = { Text("وعده‌ها", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = useCustomTime,
                            onClick = { useCustomTime = true },
                            label = { Text("ساعت دقیق (${AppUtils.formatTime(customHour, customMinute)})", fontSize = 11.sp) }
                        )
                    }

                    if (useCustomTime) {
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(context, { _, h, m ->
                                    customHour = h
                                    customMinute = m
                                }, customHour, customMinute, true).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تغییر ساعت آلارم: ${AppUtils.formatTime(customHour, customMinute)}")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = slotMorning, onClick = { slotMorning = !slotMorning }, label = { Text("صبح", fontSize = 11.sp) })
                            FilterChip(selected = slotNoon, onClick = { slotNoon = !slotNoon }, label = { Text("ظهر", fontSize = 11.sp) })
                            FilterChip(selected = slotNight, onClick = { slotNight = !slotNight }, label = { Text("شب", fontSize = 11.sp) })
                            FilterChip(selected = slotBed, onClick = { slotBed = !slotBed }, label = { Text("خواب", fontSize = 11.sp) })
                        }
                    }
                }

                Text("دستور نسبت به غذا:", color = LightBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("بعد از غذا", "قبل از غذا", "همراه غذا", "فرقی ندارد").forEach { option ->
                        FilterChip(
                            selected = meal == option,
                            onClick = { meal = option },
                            label = { Text(option, fontSize = 10.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { if (it.length <= 5) stockText = it },
                        label = { Text("موجودی") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expiryText,
                        onValueChange = { if (it.length <= 15) expiryText = it },
                        label = { Text("انقضا (۱۴۰۶/۰۵)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { if (it.length <= 120) notesText = it },
                    label = { Text("یادداشت پزشک (اختیاری)") },
                    trailingIcon = {
                        if (notesText.isNotEmpty()) {
                            IconButton(onClick = { notesText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val slots = if (useCustomTime) {
                            listOf("custom")
                        } else {
                            buildList {
                                if (slotMorning) add("morning")
                                if (slotNoon) add("noon")
                                if (slotNight) add("night")
                                if (slotBed) add("bed")
                                if (isEmpty()) add("morning")
                            }
                        }
                        val safeStock = AppUtils.parseSafeInt(stockText, defaultValue = 30, min = 0, max = 10000)
                        onConfirm(
                            name,
                            medType,
                            "blue",
                            dosage.ifBlank { "۱" },
                            dosageUnit.ifBlank { "عدد" },
                            slots,
                            AppUtils.formatTime(customHour, customMinute),
                            frequencyType,
                            selectedDays.joinToString(","),
                            meal,
                            safeStock,
                            5,
                            expiryText,
                            notesText
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("ذخیره دارو")
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
