# BillNexa — Offline billing studio

Android WebView app with an offline HTML/CSS/JavaScript interface and Java native bridges for printing, sharing text and backup. No internet permission or external backend. Browser version is also usable separately; print to PDF requires a browser that supports printing.

## Build an APK
1. Open this folder in Android Studio with JDK 17, Android SDK 35 and Gradle 8.9. The project does not include Gradle wrapper binaries. Use an installed Gradle 8.9 (`gradle :app:assembleDebug`) or generate a wrapper using `gradle wrapper --gradle-version 8.9`.
2. APK output: `app/build/outputs/apk/debug/app-debug.apk`.
3. Alternatively upload the **contents** of this folder (including `.github`) to a GitHub repository. Run Actions > Build BillNexa APK; download the artifact and extract the APK.
4. A Play release requires signing, release build, current target SDK and policy review; no release APK/AAB is included. The package ID can be changed before publication.

## Usage
Set your business details under More, add customers/products, create an invoice, enter payment, then Save & preview. Select Print / Save PDF and choose Save as PDF in the system print dialog. Share summary opens Android sharing (not a PDF attachment). Use Export backup and Restore backup to transfer your local data. Existing invoice snapshots are kept when customer/product details change. Data is local, unencrypted at application level, and can be lost on uninstall. Do not share unprotected backups.

GST mode is manual and tax-exclusive, with CGST/SGST or IGST on discounted item prices. Verify rates, tax jurisdiction, invoice fields, rounding and GST compliance independently before business use. No GST portal, e-invoice, inventory stock management, cloud sync, payment processing or subscriptions in this version. The Android app uses a bundled web interface, not fully native UI widgets. No Android build or device test has been performed in the creation environment.
