# Диктофон (VoiceRecorder)

Android‑застосунок для запису голосу у фоновому режимі з таймером, індикатором амплітуди звуку та списком **«Мої записи»** з можливістю їх відтворення або видалення.

---

## Як зібрати та запустити

1. Клонуйте репозиторій:
   ```bash
   git clone https://github.com/r3alscript/VoiceRecorder.git
   cd VoiceRecorder
   ```

2. Відкрийте проєкт у **Android Studio**.

3. Переконайтеся, що у вас налаштовано:
   - **JDK:** 17+  
   - **Gradle:** 8.13  
   - **compileSdk:** 34  
   - **targetSdk:** 34  
   - **minSdk:** 24  
   - **Kotlin stdlib:** 1.9.24 (вирівнювання через `resolutionStrategy` у `build.gradle`)

4. Запустіть застосунок на емуляторі або фізичному пристрої.  
   Для емулятора: **Extended controls → Microphone → Virtual microphone uses host audio input**.

---

## Залежності

- `androidx.appcompat:appcompat:1.7.0`  
- `com.google.android.material:material:1.12.0`  
- `androidx.activity:activity:1.9.2`  
- `androidx.recyclerview:recyclerview:1.3.2`  
- `androidx.preference:preference:1.2.1`  
- `androidx.documentfile:documentfile:1.0.1`
- `org.jetbrains.kotlin:kotlin-bom:1.9.24`
