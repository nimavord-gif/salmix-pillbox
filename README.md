# دارویار سالمیکس (Salmix PillBox) - پروژه نیتیو اندروید
**نسخه رسمی و ۱۰۰٪ بومی اندروید توسعه داده شده با Kotlin و Jetpack Compose**

---

## 🏗️ معماری و فناوری‌های نیتیو استفاده‌شده:

1. **زبان رسمی:** Kotlin 2.x
2. **رابط کاربری بومی:** Android Jetpack Compose + Material Design 3
3. **دیتابیس آفلاین محلی:** Android Room Database (SQLite بومی)
4. **سیستم آلارم واقعی اندروید:**
   - استفاده از `AlarmManager.setExactAndAllowWhileIdle()`
   - اجرای `ForegroundService` همراه با `WakeLock` برای بیدار کردن گوشی حتی در حالت قفل یا Doze Mode
   - باز شدن اکتیویتی تمام‌صفحه زنگ (`AlarmActivity`) با متدهای `setShowWhenLocked(true)` و `setTurnScreenOn(true)`
5. **پخش صدای بومی:** Android `MediaPlayer` با فایل صوتی اختصاصی سالمیکس (`res/raw/salmix_alarm.mp3`)
6. **بیدارباش پس از ریستارت گوشی:** `BootReceiver` جهت بازیابی تمام هشدارهای تقویم به محض روشن شدن گوشی

---

## 📂 ساختار فایل‌های پروژه:

```text
SalmixPillBox_NativeAndroid/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml (مانیفست دسترسی‌ها و سرویس‌ها)
│   │   ├── res/
│   │   │   ├── raw/salmix_alarm.mp3 (صدای زنگ اختصاصی سالمیکس)
│   │   │   ├── drawable/ (آیکون و مسکات دکتر سالمیکس)
│   │   │   └── values/ (رنگ‌ها، تم‌ها و رشته‌ها)
│   │   └── java/com/salmix/pillbox/
│   │       ├── data/ (دیتابیس Room: Medicine, MedicineDao, AppDatabase)
│   │       ├── receiver/ (AlarmReceiver و BootReceiver)
│   │       ├── service/ (AlarmSoundService - سرویس پیش‌زمینه پخش صدا)
│   │       ├── viewmodel/ (PillViewModel - مدیریت استیت و بیزنس لاجیک)
│   │       ├── ui/ (AlarmActivity - صفحه بومی زنگ ساعت)
│   │       └── MainActivity.kt (صفحه اصلی با Jetpack Compose)
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 نحوه اجرا در اندروید استودیو (Android Studio):

1. نرم‌افزار **Android Studio** را باز کنید.
2. گزینه **Open** را بزنید و این پوشه را انتخاب کنید:
   ```text
   C:\Users\SEPEHR\Desktop\SalmixPillBox_NativeAndroid
   ```
3. اجازه دهید گریدل یک‌بار پروژه را Sync کند.
4. گوشی موبایل خود را وصل کنید (یا شبیه‌ساز را روشن کنید) و دکمه **Run (مثلث سبز)** را بزنید!
