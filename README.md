# NFC-FMGC-READER (RFIDSSCANNER)

Android application for reading NFC/RFID tags on FMCG packaging. Decodes compact JSON payloads into domain models to drive accessibility features (Text-to-Speech, haptics, Compose UI).

## Repository & Directory
* **GitHub Repository Name**: `NFC-FMGC-READER`
* **Local Root Directory**: `RFIDSSCANNER`

---

## Technical Specifications

### 1. Data Compression & NFC Payload Format
NFC tags have restricted byte capacity. Payloads use numeric ID references and bitmasks instead of strings.

Schema:
```json
{
  "b": "Nature's Best",
  "p": "Organic Oats & Honey Granola",
  "c": 3,
  "a": 0,
  "w": 0,
  "d": 65,
  "e": 2853,
  "n": [150, 4, 3, 28, 12, 50],
  "i": [11, 5]
}
```

* `b`: `String` - Brand name.
* `p`: `String` - Product description.
* `c`: `Int` - Category ID (`dictionary.json`).
* `a`: `Int` - Allergen bitmask (bit position sum).
* `w`: `Int` - Warning bitmask (bit position sum).
* `d`: `Int` - Dietary attributes bitmask (bit position sum).
* `e`: `Int` - Expiry epoch offset (days elapsed since `2020-01-01`).
* `n`: `List<Int>` - Nutrition values `[Calories, Protein, Fat, Carb, Sugar, Sodium]`.
* `i`: `List<Int>` - Ingredient IDs (`dictionary.json`).

### 2. O(1) Dictionary Lookup (`DictionaryStore.kt`)
* Initialized on `Dispatchers.IO` via `ScannerApplication.kt`.
* Parses `app/src/main/assets/dictionary.json` (1,500 ingredients, 20 allergens, 15 warnings, 10 dietary attributes, 30 categories).
* Generates fixed-size nullable string arrays (`Array<String?>`) indexed directly by ID/bit position for constant-time $O(1)$ retrieval.

### 3. Decoding Pipeline (`PayloadDecoder.kt`)
* Deserializes JSON via Gson into `ProductPayload`.
* Expiry calculation: `LocalDate.of(2020, 1, 1).plusDays(payload.e.toLong())`. Determines `SAFE` (>30 days), `WARNING` (0-30 days), or `EXPIRED` (<0 days) relative to `LocalDate.now()`.
* Bitwise decoding: Checks bits 0-31 against `DictionaryStore` lookup functions (`(bitmask and (1 shl i)) != 0`).

---

## Runtime Mechanism & Execution Flow

### 1. NFC Reader Mode Initialization (`MainActivity.kt`)
* **Lifecycle Hook**: In `MainActivity.onResume()`, if scanning is enabled (`AppState.ScanningEnabled`), the app registers an NFC reader callback with the system `NfcAdapter`:
  ```kotlin
  nfcAdapter?.enableReaderMode(
      this,
      this, // Implements NfcAdapter.ReaderCallback
      NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
      null
  )
  ```
* **Tag Interception**: When an NFC/RFID tag is brought within range, the Android NFC subsystem triggers `onTagDiscovered(tag: Tag)` on the main thread via `NfcAdapter.ReaderCallback`.

### 2. Payload Extraction
* **NDEF Extraction**: Reads raw NDEF records from the intercepted `Tag` object, extracting the payload byte array and converting it to a UTF-8 JSON `String`.
* **State Transition**: Passes the JSON string to `PayloadDecoder.decodePayload(jsonString)`.

### 3. Decoding Pipeline (`PayloadDecoder.kt`)
1. **Gson Deserialization**:
   ```kotlin
   val payload = gson.fromJson(jsonString, ProductPayload::class.java)
   ```
2. **Expiry Computation**:
   * Base epoch: `LocalDate.of(2020, 1, 1)`
   * Target date: `baseDate.plusDays(payload.e.toLong())`
   * Delta calculation: `ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)`
   * Status evaluation:
     * `daysRemaining < 0` -> `ExpiryStatus.EXPIRED`
     * `daysRemaining <= 30` -> `ExpiryStatus.WARNING`
     * `daysRemaining > 30` -> `ExpiryStatus.SAFE`
3. **Bitwise Decoding**:
   * Evaluates integer bitmasks (`a`, `w`, `d`).
   * Iterates through bit indices `0` to `31`:
     ```kotlin
     if ((bitmask and (1 shl i)) != 0) {
         DictionaryStore.getAllergenName(i)?.let { result.add(it) }
     }
     ```
4. **ID Resolution**:
   * Maps category ID (`payload.c`) and ingredient ID list (`payload.i`) via `DictionaryStore`.

### 4. Dictionary Resolution (`DictionaryStore.kt`)
* quotes, brackets, and key names (`"b"`, `"p"`, etc.).
* Queries prebuilt $O(1)$ lookup arrays:
  ```kotlin
  fun getIngredientName(id: Int): String? = ingredientNames.getOrNull(id)
  fun getAllergenName(bitPosition: Int): String? = allergenNames.getOrNull(bitPosition)
  ```
* Bypasses linear searches, returning string values instantly via direct array indexing.

### 5. Accessibility, Haptics, and UI Update (`MainActivity.kt`)
* **State Update**: `appState` transitions to `AppState.ProductView(product)`, triggering Jetpack Compose recomposition (`ProductScreen`).
* **Haptic Feedback**: Triggers device vibrator for success feedback (`triggerSuccessHaptic()`).
* **Text-to-Speech (TTS)**: Constructs a verbal summary of the product (brand, name, expiry status, allergens, warnings, nutrition, and ingredients) and passes it to `TextToSpeech.speak()`.

---

## Future Roadmap

* **Binary Serialization & Bit-Packing**: Transition from UTF-8 JSON text payloads to custom binary byte arrays (or CBOR/Protocol Buffers). By packing fields into fixed-bit widths (e.g., Category ID into 6 bits, Expiry days into 12 bits, Allergen/Warning bitmasks into 32-bit integers, and variable-length integer varints for Ingredient IDs), payload sizes can be reduced from ~150 bytes (JSON) down to 15-30 bytes. This enables storing complex product records even on ultra-low-capacity tags (such as NTAG203 / NTAG213 with 144 bytes).
* **Payload Cryptography & Signature Verification**: Implement HMAC or elliptic-curve digital signature verification on NFC tag payloads to prevent counterfeit or tampered FMCG packaging tags.
* **In-App NDEF Tag Writing Utility**: Provide a developer/manufacturer utility mode within the app to format and write compressed JSON payloads directly to NTAG215/NTAG216 tags.
* **Dynamic Dictionary Sync**: Add background worker tasks (`WorkManager`) to fetch delta updates for `dictionary.json` from a remote endpoint without requiring app version updates.
* **BLE Beacon / Barcode Fallback**: Integrate camera-based barcode scanning (ZXing/ML Kit) and Bluetooth Low Energy (BLE) beacon detection as fallback discovery mechanisms when NFC tags are damaged or absent.
* **Multi-Language Dictionary Localization**: Extend `dictionary.json` schema to support localized string bundles for ingredients, allergens, and warnings based on system locale settings.

---

## File Reference

* `ScannerApplication.kt`: Application subclass; triggers `DictionaryStore.initialize()`.
* `MainActivity.kt`: ComponentActivity; manages NFC `ReaderCallback`, `TextToSpeech`, vibration haptics, and Compose UI states (`AppState`).
* `PayloadDecoder.kt`: Stateless object; decodes JSON payload into `Product`.
* `Product.kt`: Domain data classes (`Product`, `ProductPayload`) and `ExpiryStatus` enum.
* `DictionaryStore.kt`: Singleton store maintaining $O(1)$ lookup arrays.
* `DictionaryModels.kt`: Gson DTOs for `dictionary.json`.
* `dictionary.json`: Master asset database containing 1,500 ingredients, allergens, warnings, categories, and nutrition fields.
* `ui/theme/`: Material 3 theme, colors, and typography.

---

## Build & Dependencies

Configured in `app/build.gradle.kts`:
* Compile/Target SDK: 36
* Min SDK: 29
* Compose BOM, Lifecycle KTX, Gson.

```bash
./gradlew assembleDebug
```
