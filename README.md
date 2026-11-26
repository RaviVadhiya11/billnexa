# BillNexa — Offline billing studio v2.1

Package: `in.pixelfire.billnexa`. Android 16 target (API 36), minimum API 23. JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1. No account, analytics, ad SDK or Internet permission. Native PDF/JPG output, offline catalogue, invoices and backups.

## Build
GitHub Actions → Build BillNexa APK. Pushes build a debug APK for device tests and an unsigned release AAB for compilation verification. Do NOT upload the unsigned AAB or debug APK to Play Console.
For Play upload, generate a dedicated upload keystore locally, protect its backup, and add these repository Actions secrets:
- BILLNEXA_KEYSTORE_BASE64 (base64 of the upload keystore)
- BILLNEXA_STORE_PASSWORD
- BILLNEXA_KEY_ALIAS
- BILLNEXA_KEY_PASSWORD

Never commit passwords or keystore files. Run the workflow manually with signed_release enabled. Download BillNexa-signed-Play-AAB. Keep the same upload key for future versions and enroll in Play App Signing. Android Studio can also generate a signed release bundle locally.

## Privacy
Privacy policy is included in the app (More → Privacy policy) and in docs/privacy.html. Terms and support pages are in docs/terms.html and docs/support.html. Enable GitHub Pages for main /docs (or host the document on your own domain). Verify the public non-PDF URL opens without login before using it in Play Console. Confirm the listed support email is active.

## Before production
See PLAY-STORE.md. Test real devices and the Play internal/closed track. Verify GST requirements independently. The app does not process payments or integrate with GST/e-invoicing services.

Development by Ravi Vadhiya

## Business Suite
Inventory is opt-in per product. Catalogue-linked invoices adjust tracked quantities; edits and deletion reverse recorded stock moves. Existing invoices are not deducted retroactively. Quotations are excluded from sales and inventory. Reports use invoice dates and cumulative received amounts, not cash-flow dates. Customer ledger groups by invoice contact name, phone and GSTIN.

## Handwritten signatures
Save an optional reusable handwritten signature under Settings; apply explicitly to a saved bill. Per-bill strokes are included in native PDF/JPG/print and backups. Editing, duplication or recording payment does not silently reuse the old signature. No DSC or cryptographic PDF signing.

## Shop branding
Enter a shop name in the bill editor or use a saved business profile. Optional JPG/PNG/WebP logo is resized locally and copied to each saved bill. Normal non-GST bills omit the invoice title; GST and quotation document labels remain. Older bills are not silently modified.
