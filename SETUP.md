# Quick Guard Pro — SETUP (Urdu + English)

Yeh guide 5 hisso me hai. Tarteeb se karein. Har step aik dafa hota hai.

- A. Firebase project banana
- B. Backend (Cloud Functions) deploy karna
- C. GitHub par code daal kar APK banana
- D. Signing key ke secrets add karna
- E. QR setup + pehla test

---

## A. FIREBASE PROJECT (aik dafa)

1. https://console.firebase.google.com par jaayein > **Add project**.
2. Project ka naam rakhein aur project ID **`quick-guard-pro`** rakhne ki koshish karein.
   (Agar yeh ID available na ho to jo mile woh le lein, aur `.firebaserc` file me
   `quick-guard-pro` ki jagah apni ID likh dein.)
3. **Billing — zaroori hai:** left menu neeche **Upgrade** / project settings se plan ko
   **Blaze (pay as you go)** par le jaayein. Cloud Functions Blaze ke baghair deploy nahi hote.
   (Choti dukaan ke istemaal par kharcha aam tor par bohat maamuli hota hai, lekin card add
   karna zaroori hai.)
4. **Authentication** > Get started > **Sign-in method**:
   - **Email/Password** ko **Enable** karein.
   - **Anonymous** ko bhi **Enable** karein (customer phone isi se connect hota hai).
5. **Authentication > Users > Add user**: apna OWNER email aur password banayein
   (misal: aap ka business email). Yeh aap ka maalik (OWNER) account hai.
6. **Firestore Database** > Create database > **Production mode** > location `asia-south1` (ya nazdeek).

### Do apps register karein (dono zaroori)
7. Project Overview (gear ke paas) > **Add app** > Android:
   - Package name: **`com.abbaskhanelectronics.quickguardpro.admin`** > Register >
     **google-services.json download** karein.
   - Dobara Add app > Android:
     Package name: **`com.abbaskhanelectronics.quickguardpro.device`** > Register >
     **google-services.json download** karein.
8. Pehli file ko project ke **`admin/`** folder me, doosri ko **`device/`** folder me rakhna hai
   (Step C me GitHub par).

---

## B. BACKEND DEPLOY (Google Cloud Shell — computer ki zaroorat nahi)

1. https://shell.cloud.google.com kholein (wohi Google account jis se Firebase banaya).
2. Upar apna project chunein ya yeh command chalayein:
   ```
   gcloud config set project quick-guard-pro
   ```
3. Is project ka ZIP (jo aap ko mila) Cloud Shell me **Upload** karein (teen dots menu > Upload),
   phir:
   ```
   unzip QuickGuardPro.zip -d qgpro
   cd qgpro/QuickGuardPro
   ```
4. OWNER email set karein — **`functions/.env`** file kholein aur `owner@example.com` ki jagah
   apna asli OWNER email likhein (jo Step A5 me banaya). Editor:
   ```
   cloudshell edit functions/.env
   ```
5. Deploy:
   ```
   npm --prefix functions install
   npx firebase-tools login --no-localhost
   npx firebase-tools deploy --only functions,firestore --project quick-guard-pro
   ```
   - `login` ek link dega — kholein, apne Google account se ijazat dein, code wapas paste karein.
   - Deploy me 3–5 minute lagte hain. Aakhir me "Deploy complete!" aana chahiye.

> Jab bhi backend me tabdeeli karni ho, sirf Step B4–B5 dobara chalayein.

---

## C. GITHUB PAR CODE + APK

1. github.com par **New repository** banayein. QR download ke liye ise **Public** rakhein.
2. Repo me **Add file > Upload files**: is project ki saari files/folders upload karein
   (`admin/`, `device/`, `functions/`, `.github/`, root ki gradle files, waghera).
   > **Signing folder (qgpro-release.jks waghera) yahan upload NA karein.** Woh sirf secrets me jaata hai (Step D).
3. Step A7 wali do **google-services.json** files:
   - `admin/google-services.json`
   - `device/google-services.json`
   ke tor par upload karein (agar upload me na aayein to Add file > Create new file se banayein
   aur content paste karein).
4. Upload hote hi **Actions** tab par build khud shuru ho jaata hai. 5–8 minute me:
   - Agar secrets (Step D) laga diye hain to **Releases** me `QuickGuardPro-Admin.apk` aur
     `QuickGuardPro-Device.apk` aa jaayenge.
   - Warna Actions > latest run > **Artifacts** se `QuickGuardPro-APKs` download hoti hai (test/debug).

---

## D. SIGNING KEY SECRETS (aik dafa — QR ke liye zaroori)

Aap ko aik alag **QuickGuardPro-Signing** folder mila hai jis me `GITHUB-SECRETS.txt` hai.
Us file me di gayi 4 values ko GitHub par add karein:

**Repo > Settings > Secrets and variables > Actions > New repository secret** — 4 dafa:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | `KEYSTORE_BASE64.txt` ka poora text |
| `SIGNING_STORE_PASSWORD` | file me diya password |
| `SIGNING_KEY_ALIAS` | `quickguard` |
| `SIGNING_KEY_PASSWORD` | wohi password |

Phir Actions tab > latest workflow > **Re-run** karein taake APK asli key se sign ho aur
Release me publish ho jaaye.

> ⚠️ Yeh key aur passwords sambhaal kar rakhein. Gum hone par enrolled phones par app **update**
> nahi ho sakegi (naya key purani installation ko update nahi karta).

---

## E. QR SETUP + PEHLA TEST

### 1. Admin app install + owner login
- Apne phone par **QuickGuardPro-Admin.apk** install karein.
- Step A5 wale OWNER email/password se **login** karein. (Pehli login par aap khud-ba-khud OWNER ban jaayenge.)

### 2. Device APK ka link + checksum
- Repo ke **Releases** me `QuickGuardPro-Device.apk` par right-click/long-press > link copy.
  Stable link aam tor par yeh hota hai:
  ```
  https://github.com/USERNAME/REPO/releases/latest/download/QuickGuardPro-Device.apk
  ```
  (USERNAME/REPO apna likhein.)
- Admin app > **Settings > QR enrollment setup**:
  - **Device APK URL**: upar wala link.
  - **Signature checksum**: signing folder ki `GITHUB-SECRETS.txt` me diya checksum.
  - Save.

### 3. Do tarah se test kar sakte hain

**(i) Aasān test — bina QR (ADB se, Device Owner):**
Naya/factory-reset phone jis me koi Google account add na ho, USB se laptop se:
```
adb install QuickGuardPro-Device.apk
adb shell dpm set-device-owner com.abbaskhanelectronics.quickguardpro.device/.admin.QgDeviceAdminReceiver
```
Phir Admin app me kisi agreement par **Enroll Device (QR)** khol kar **enrollment code** dekhen,
device app me woh code daal kar consent qubool karein. Ab lock/unlock/location test karein.

**(ii) Asli tareeqa — QR se (customer phone jaisa):**
1. Phone ko **factory reset** karein.
2. Sab se pehli **Welcome/Hello** screen par usi jagah **6 baar** tap karein — QR scanner khul jaayega.
3. Wi-Fi se connect karein (agar maange).
4. Admin app me agreement par **Enroll Device (QR)** se bana **QR code** scan karwayein.
5. App khud download ho kar Device Owner ban jaayegi; customer ko consent screen dikhe gi > Accept.

### 4. Test checklist
- [ ] Customer + agreement banana, down payment.
- [ ] Payment record karna, receipt number, balance kam hona.
- [ ] Enroll device (dono tareeqe me se koi).
- [ ] Admin se **Lock** > phone par restriction screen (emergency call chalti ho).
- [ ] **Unlock** > screen hat jaaye.
- [ ] **Request location** > Maps link.
- [ ] SIM nikaal kar dobara on karein > Urdu warning + admin alert.
- [ ] Poori adaigi > agreement **FULLY_PAID** > **Release** > phone azad.

---

### Roles (staff)
- **OWNER**: sab kuch (business settings, staff, QR setup).
- **MANAGER**: lock/unlock/location/release/reversal + sab staff kaam.
- **STAFF**: customers, agreements, payments, enrollment, messages.

Naya staff: Admin > Settings > **Staff & Roles** > + (email/password/role).
