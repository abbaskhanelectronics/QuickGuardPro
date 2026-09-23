# Quick Guard Pro — Zaroori baatein / Honest limits

Yeh sach-sach batana zaroori hai taake aap ka waqt zaya na ho.

## 1. Yeh code yahan compile/test NAHI hua
Mere paas Android SDK aur internet nahi tha, is liye main ne yeh **pehli baar GitHub par hi build**
hone ke liye likha hai. Mumkin hai pehli build par **kuch chhoti compile errors** aayein
(kisi import ya version ki wajah se). Ghabrayein nahi:
- GitHub **Actions > (failed run) > build step** kholein, laal error lines copy karein,
  aur mujhe paste kar dein — main theek kar ke dobara de dunga.
Yeh normal hai; do-teen dafa me saaf ho jaata hai.

## 2. Device Owner sirf QR/ADB se lagta hai
Lock, uninstall-protection aur release **Device Owner** ke baghair kaam nahi karte, aur Device Owner
sirf **factory-reset phone** par (QR ya `adb set-device-owner` se) lagta hai — jis me pehle se koi
Google account add na ho. Aam install (Play/APK) se sirf maloomat aur reminder chalte hain, lock nahi.

## 3. Har company ka phone thoda alag
Samsung, Xiaomi, Oppo, Infinix, Tecno waghera me DPM behaviour thoda mukhtalif hota hai
(battery optimization, background limits). Capabilities screen har phone par bata deti hai kya
supported hai. Kisi khaas model par koi cheez limited ho sakti hai.

## 4. Location
- Sirf **shop ki request par ek dafa** li jaati hai (musalsal tracking nahi).
- Phone **on + internet + location on** hona chahiye. Band phone ki live location mumkin nahi;
  aakhri known location tab bhi mil sakti hai.

## 5. SIM detection
SIM change ka pata subscription ID/operator se lagta hai. Yeh zyadatar phones par kaam karta hai,
lekin kuch dual-SIM/eSIM cases me warning der se aa sakti hai. IMEI Android 10+ par app ko poora
nahi milta (Google ki paabandi) — is liye SIM key operator + slot par mabni hai.

## 6. Legal / consent
- App enrollment par saaf **consent screen** (Urdu + English) dikhati hai; customer ke qubool karne
  par hi activate hoti hai.
- Yeh spyware nahi: koi messages/calls/photos/contacts/mic/camera access nahi. Sirf woh status jo
  disclose kiya gaya hai.
- Apne mulk/qanoon ke mutabiq installment agreement par customer ke dastkhat/consent zaroor lein.
  Yeh aap ki zimmedari hai.

## 7. Abhi jo shaamil NAHI (baad me ho sakta hai)
- PDF receipt / print, Excel export, backup.
- Fingerprint/biometric app-lock, offline-first admin cache.
- Push par foreground live-tracking, geofencing.
- Multi-language UI switch (abhi Urdu+English mila hua hai).
- Unit tests.

## 8. Kharcha
Firebase **Blaze** plan zaroori hai (Functions ke liye). Choti dukaan ke use par kharcha aam tor par
bohat kam hota hai, magar yeh aap ke istemaal par mabni hai — Firebase console me budget alert laga lein.

---
Kisi bhi error ya behaviour par mujhe bata dein — hum step by step theek karte jaayenge.
