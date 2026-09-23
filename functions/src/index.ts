/**
 * Quick Guard Pro — backend (Firebase Cloud Functions v2).
 * All sensitive and financial writes happen here, never directly from the apps.
 */
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2";
import { defineString } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { randomBytes, randomInt } from "crypto";

admin.initializeApp();
const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;
const Timestamp = admin.firestore.Timestamp;
type Ts = admin.firestore.Timestamp;
type Tx = admin.firestore.Transaction;

setGlobalOptions({ region: "us-central1", maxInstances: 10 });
const OWNER_EMAIL = defineString("OWNER_EMAIL");

const PK_OFFSET = 5 * 3600 * 1000; // Pakistan time (UTC+5)
const DAY = 86400000;
const DEVICE_COMPONENT =
  "com.abbaskhanelectronics.quickguardpro.device/com.abbaskhanelectronics.quickguardpro.device.admin.QgDeviceAdminReceiver";

type Role = "OWNER" | "MANAGER" | "STAFF";
const ALL: Role[] = ["OWNER", "MANAGER", "STAFF"];
const MGR: Role[] = ["OWNER", "MANAGER"];
const OWN: Role[] = ["OWNER"];
interface Actor { uid: string; name: string; role: Role }

// ---------------------------------------------------------------- helpers

function fail(code: "invalid-argument" | "not-found" | "permission-denied" | "failed-precondition" | "already-exists" | "resource-exhausted" | "unauthenticated", msg: string): never {
  throw new HttpsError(code, msg);
}

async function requireStaff(req: CallableRequest<unknown>, roles: Role[]): Promise<Actor> {
  if (!req.auth) fail("unauthenticated", "Login required");
  const snap = await db.doc(`users/${req.auth.uid}`).get();
  const u = snap.data();
  if (!u || u.active !== true) fail("permission-denied", "Account not authorized");
  if (!roles.includes(u.role)) fail("permission-denied", "Your role cannot perform this action");
  return { uid: req.auth.uid, name: u.name || u.email || "Staff", role: u.role };
}

async function requireDevice(req: CallableRequest<unknown>, deviceId: string) {
  if (!req.auth) fail("unauthenticated", "Device not authenticated");
  if (!deviceId) fail("invalid-argument", "deviceId required");
  const snap = await db.doc(`devices/${deviceId}`).get();
  if (!snap.exists || snap.get("deviceUid") !== req.auth.uid) fail("permission-denied", "Unknown device");
  return snap;
}

function data(req: CallableRequest<unknown>): Record<string, unknown> {
  return (req.data && typeof req.data === "object" ? req.data : {}) as Record<string, unknown>;
}
function str(v: unknown, max = 200): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}
function int(v: unknown, name: string): number {
  const n = Number(v);
  if (!Number.isFinite(n)) fail("invalid-argument", `Invalid ${name}`);
  return Math.round(n);
}

async function audit(actor: string, actorName: string, action: string, target: string, meta: Record<string, unknown> = {}, result = "SUCCESS") {
  await db.collection("auditLogs").add({ actor, actorName, action, target, meta, result, at: FieldValue.serverTimestamp() });
}

function pkDay(ms: number): number { return Math.floor((ms + PK_OFFSET) / DAY); }
function pkDateStr(ms: number): string { return new Date(ms + PK_OFFSET).toISOString().slice(0, 10); }
function parsePkDate(s: string): Date {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) fail("invalid-argument", "Date must be YYYY-MM-DD");
  return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]) - PK_OFFSET);
}
function addPeriods(first: Date, n: number, freq: string): Date {
  const d = new Date(first.getTime() + PK_OFFSET);
  if (freq === "MONTHLY") {
    const day = d.getUTCDate();
    d.setUTCDate(1);
    d.setUTCMonth(d.getUTCMonth() + n);
    const dim = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
    d.setUTCDate(Math.min(day, dim));
  } else {
    d.setUTCDate(d.getUTCDate() + n * (freq === "WEEKLY" ? 7 : 14));
  }
  return new Date(d.getTime() - PK_OFFSET);
}

interface AgreementData {
  agreementNo: string; customerId: string; customerName: string; customerPhone: string; productName: string;
  deviceId: string; totalPrice: number; downPayment: number; installmentCount: number; installmentAmount: number;
  frequency: string; firstDueDate: Ts; nextDueDate: Ts | null; graceDays: number; paidInstallments: number;
  totalCollected: number; remainingBalance: number; status: string; lastReminderKey?: string;
}

/** Derives installment progress from the remaining balance — single source of truth. */
function progress(a: AgreementData, remaining: number) {
  const financed = a.totalPrice - a.downPayment;
  const paidToward = financed - remaining;
  const paidInstallments = remaining <= 0
    ? a.installmentCount
    : Math.min(a.installmentCount, Math.floor(paidToward / Math.max(1, a.installmentAmount)));
  const nextDueDate = remaining <= 0 ? null : Timestamp.fromDate(addPeriods(a.firstDueDate.toDate(), paidInstallments, a.frequency));
  return { remainingBalance: remaining, totalCollected: a.totalPrice - remaining, paidInstallments, nextDueDate };
}

function computeStatus(a: { status: string; remainingBalance: number; nextDueDate: Ts | null }, now = Date.now()): string {
  if (a.status === "RELEASED" || a.status === "CANCELLED") return a.status;
  if (a.remainingBalance <= 0) return "FULLY_PAID";
  if (a.status === "TEMPORARILY_RESTRICTED") return a.status;
  if (!a.nextDueDate) return "ACTIVE";
  const diff = pkDay(a.nextDueDate.toMillis()) - pkDay(now);
  if (diff < 0) return "OVERDUE";
  if (diff === 0) return "DUE_TODAY";
  if (diff <= 3) return "DUE_SOON";
  return "ACTIVE";
}

function dueAmount(a: AgreementData): number {
  if (a.remainingBalance <= 0) return 0;
  const paidToward = a.totalPrice - a.downPayment - a.remainingBalance;
  return Math.min(a.remainingBalance, Math.max(0, (a.paidInstallments + 1) * a.installmentAmount - paidToward));
}

async function notifyAdmins(title: string, body: string, roles: Role[] = MGR) {
  const users = await db.collection("users").where("active", "==", true).get();
  const tokens = users.docs
    .filter((u) => roles.includes(u.get("role")))
    .flatMap((u) => (u.get("fcmTokens") as string[] | undefined) ?? []);
  if (tokens.length === 0) return;
  await admin.messaging().sendEachForMulticast({
    tokens: tokens.slice(0, 500),
    data: { title, body },
    android: { priority: "high" },
  }).catch(() => undefined);
}

async function pushToDevice(fcmToken: unknown, payload: Record<string, string>) {
  if (typeof fcmToken !== "string" || !fcmToken) return;
  await admin.messaging().send({ token: fcmToken, data: payload, android: { priority: "high" } }).catch(() => undefined);
}

async function business() {
  const b = (await db.doc("appConfig/business").get()).data() ?? {};
  return { name: (b.name as string) || "Abbas Khan Electronics", supportPhone: (b.supportPhone as string) || "03098026981" };
}

// ---------------------------------------------------------------- staff & settings

export const bootstrapOwner = onCall(async (req) => {
  if (!req.auth) fail("unauthenticated", "Login required");
  const email = String(req.auth.token.email ?? "").toLowerCase();
  const owner = OWNER_EMAIL.value().trim().toLowerCase();
  if (!owner || owner === "owner@example.com" || email !== owner) fail("permission-denied", "Not the configured owner account");
  const ref = db.doc(`users/${req.auth.uid}`);
  if (!(await ref.get()).exists) {
    await ref.set({ name: "Owner", email, role: "OWNER", active: true, createdAt: FieldValue.serverTimestamp() });
    await audit(req.auth.uid, email, "BOOTSTRAP_OWNER", req.auth.uid);
  }
  return { ok: true };
});

export const createStaff = onCall(async (req) => {
  const actor = await requireStaff(req, OWN);
  const d = data(req);
  const email = str(d.email).toLowerCase();
  const name = str(d.name, 80);
  const password = typeof d.password === "string" ? d.password : "";
  const role = str(d.role) as Role;
  if (!email.includes("@") || !name || password.length < 8 || !ALL.includes(role)) fail("invalid-argument", "Check name, email, password (8+) and role");
  let uid: string;
  try {
    uid = (await admin.auth().createUser({ email, password, displayName: name })).uid;
  } catch (e: unknown) {
    const code = (e as { code?: string }).code ?? "";
    if (code.includes("email-already-exists")) fail("already-exists", "This email already has an account");
    throw new HttpsError("internal", "Could not create account");
  }
  await db.doc(`users/${uid}`).set({ name, email, role, active: true, createdAt: FieldValue.serverTimestamp(), createdBy: actor.uid });
  await audit(actor.uid, actor.name, "STAFF_CREATE", uid, { email, role });
  return { uid };
});

export const updateStaff = onCall(async (req) => {
  const actor = await requireStaff(req, OWN);
  const d = data(req);
  const uid = str(d.uid);
  if (!uid || uid === actor.uid) fail("invalid-argument", "You cannot change your own account here");
  const role = str(d.role) as Role;
  if (!ALL.includes(role)) fail("invalid-argument", "Invalid role");
  const active = d.active === true;
  const ref = db.doc(`users/${uid}`);
  if (!(await ref.get()).exists) fail("not-found", "User not found");
  await ref.update({ role, active });
  if (!active) await admin.auth().revokeRefreshTokens(uid).catch(() => undefined);
  await audit(actor.uid, actor.name, "ROLE_CHANGE", uid, { role, active });
  return { ok: true };
});

export const registerAdminToken = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const token = str(data(req).token, 500);
  if (!token) return { ok: false };
  await db.doc(`users/${actor.uid}`).update({ fcmTokens: FieldValue.arrayUnion(token) });
  return { ok: true };
});

export const updateSettings = onCall(async (req) => {
  const actor = await requireStaff(req, OWN);
  const d = data(req);
  const b = d.business as Record<string, unknown> | undefined;
  const p = d.provisioning as Record<string, unknown> | undefined;
  if (b) {
    await db.doc("appConfig/business").set({
      name: str(b.name, 80) || "Abbas Khan Electronics",
      supportPhone: str(b.supportPhone, 20) || "03098026981",
      address: str(b.address, 300),
    }, { merge: true });
  }
  if (p) {
    const apkUrl = str(p.apkUrl, 500);
    if (apkUrl && !apkUrl.startsWith("https://")) fail("invalid-argument", "APK URL must start with https://");
    await db.doc("appConfig/provisioning").set({ apkUrl, checksum: str(p.checksum, 100) }, { merge: true });
  }
  await audit(actor.uid, actor.name, "SETTINGS_UPDATE", "appConfig", { business: !!b, provisioning: !!p });
  return { ok: true };
});

// ---------------------------------------------------------------- customers & agreements

export const createCustomer = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const d = data(req);
  const name = str(d.name, 80);
  const phone = str(d.phone, 20).replace(/[^\d+]/g, "");
  if (!name || phone.length < 10) fail("invalid-argument", "Name and a valid phone are required");
  const ref = db.collection("customers").doc();
  await ref.set({
    name, phone,
    fatherName: str(d.fatherName, 80), altPhone: str(d.altPhone, 20), cnic: str(d.cnic, 20),
    address: str(d.address, 300), reference: str(d.reference, 120), notes: str(d.notes, 500),
    status: "ACTIVE", createdAt: FieldValue.serverTimestamp(), createdBy: actor.uid, createdByName: actor.name,
  });
  await audit(actor.uid, actor.name, "CUSTOMER_CREATE", ref.id, { name });
  return { id: ref.id };
});

export const createAgreement = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const d = data(req);
  const customerId = str(d.customerId);
  const productName = str(d.productName, 100);
  const cashPrice = int(d.cashPrice ?? 0, "cash price");
  const totalPrice = int(d.totalPrice, "total price");
  const downPayment = int(d.downPayment ?? 0, "down payment");
  const installmentCount = int(d.installmentCount, "installment count");
  const graceDays = Math.max(0, Math.min(30, int(d.graceDays ?? 0, "grace days")));
  const frequency = ["MONTHLY", "FORTNIGHTLY", "WEEKLY"].includes(str(d.frequency)) ? str(d.frequency) : "MONTHLY";
  const firstDue = parsePkDate(str(d.firstDueDate));
  if (!productName || totalPrice <= 0 || downPayment < 0 || downPayment >= totalPrice || installmentCount < 1 || installmentCount > 60) {
    fail("invalid-argument", "Check product, prices and installment count");
  }
  const customer = await db.doc(`customers/${customerId}`).get();
  if (!customer.exists) fail("not-found", "Customer not found");
  const financed = totalPrice - downPayment;
  const installmentAmount = Math.ceil(financed / installmentCount);
  const agrRef = db.collection("agreements").doc();
  const counters = db.doc("appConfig/counters");

  const agreementNo = await db.runTransaction(async (tx) => {
    const c = await tx.get(counters);
    const agrN = (c.get("agreement") ?? 0) + 1;
    const rcN = (c.get("receipt") ?? 0) + 1;
    const no = `AKE-${String(agrN).padStart(5, "0")}`;
    tx.set(counters, { agreement: agrN, ...(downPayment > 0 ? { receipt: rcN } : {}) }, { merge: true });
    tx.set(agrRef, {
      agreementNo: no, customerId, customerName: customer.get("name"), customerPhone: customer.get("phone"),
      productName, imei: str(d.imei, 20), deviceId: "",
      cashPrice, totalPrice, downPayment, installmentCount, installmentAmount, frequency,
      firstDueDate: Timestamp.fromDate(firstDue), nextDueDate: Timestamp.fromDate(firstDue), graceDays,
      paidInstallments: 0, totalCollected: downPayment, remainingBalance: financed,
      status: computeStatus({ status: "ACTIVE", remainingBalance: financed, nextDueDate: Timestamp.fromDate(firstDue) }),
      createdAt: FieldValue.serverTimestamp(), createdBy: actor.uid,
    });
    if (downPayment > 0) {
      tx.set(db.collection("payments").doc(), {
        agreementId: agrRef.id, customerId, amount: downPayment, method: "CASH", reference: "", notes: "Down payment",
        receiptNo: `R-${String(rcN).padStart(6, "0")}`, previousBalance: totalPrice, newBalance: financed,
        staffUid: actor.uid, staffName: actor.name, kind: "DOWN_PAYMENT", reversed: false,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    return no;
  });
  await audit(actor.uid, actor.name, "AGREEMENT_CREATE", agrRef.id, { agreementNo, totalPrice, downPayment, installmentCount });
  return { id: agrRef.id, agreementNo };
});

// ---------------------------------------------------------------- payments

export const recordPayment = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const d = data(req);
  const agreementId = str(d.agreementId);
  const amount = int(d.amount, "amount");
  const requestId = str(d.requestId, 64).replace(/[^A-Za-z0-9-]/g, "");
  if (amount <= 0 || requestId.length < 16) fail("invalid-argument", "Invalid payment");
  const payRef = db.doc(`payments/${requestId}`); // idempotency: same request can never be saved twice
  const agrRef = db.doc(`agreements/${agreementId}`);
  const counters = db.doc("appConfig/counters");

  const result = await db.runTransaction(async (tx: Tx) => {
    const [pay, agr, c] = await Promise.all([tx.get(payRef), tx.get(agrRef), tx.get(counters)]);
    if (pay.exists) return { duplicate: true, receiptNo: pay.get("receiptNo"), newBalance: pay.get("newBalance"), a: null };
    if (!agr.exists) fail("not-found", "Agreement not found");
    const a = agr.data() as AgreementData;
    if (["RELEASED", "CANCELLED", "FULLY_PAID"].includes(a.status)) fail("failed-precondition", "Agreement is closed");
    if (amount > a.remainingBalance) fail("invalid-argument", "Amount is more than the remaining balance");
    const p = progress(a, a.remainingBalance - amount);
    const status = computeStatus({ status: a.status, ...p });
    const rcN = (c.get("receipt") ?? 0) + 1;
    const receiptNo = `R-${String(rcN).padStart(6, "0")}`;
    tx.set(counters, { receipt: rcN }, { merge: true });
    tx.set(payRef, {
      agreementId, customerId: a.customerId, amount, method: str(d.method, 20) || "CASH",
      reference: str(d.reference, 60), notes: str(d.notes, 300), receiptNo,
      previousBalance: a.remainingBalance, newBalance: p.remainingBalance,
      staffUid: actor.uid, staffName: actor.name, kind: "INSTALLMENT", reversed: false,
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.update(agrRef, { ...p, status });
    return { duplicate: false, receiptNo, newBalance: p.remainingBalance, a: { ...a, status } };
  });

  if (!result.duplicate && result.a) {
    await audit(actor.uid, actor.name, "PAYMENT", agreementId, { amount, receiptNo: result.receiptNo });
    const a = result.a;
    if (a.deviceId) {
      const dev = await db.doc(`devices/${a.deviceId}`).get();
      const full = result.newBalance <= 0;
      await pushToDevice(dev.get("fcmToken"), {
        type: "REMINDER",
        title: "Quick Guard Pro — ادائیگی موصول",
        body: full
          ? `Rs ${amount} موصول ہوئے۔ آپ کی تمام اقساط مکمل ہو گئی ہیں۔ شکریہ!`
          : `Rs ${amount} موصول ہوئے۔ بقایا رقم: Rs ${result.newBalance}`,
      });
    }
    await notifyAdmins("Payment received", `${a.customerName}: Rs ${amount} (${result.receiptNo})`, OWN);
  }
  return { ok: true, receiptNo: result.receiptNo, newBalance: result.newBalance, duplicate: result.duplicate };
});

export const reversePayment = onCall(async (req) => {
  const actor = await requireStaff(req, MGR);
  const paymentId = str(data(req).paymentId);
  const payRef = db.doc(`payments/${paymentId}`);
  const counters = db.doc("appConfig/counters");
  await db.runTransaction(async (tx) => {
    const pay = await tx.get(payRef);
    if (!pay.exists) fail("not-found", "Payment not found");
    if (pay.get("kind") !== "INSTALLMENT" || pay.get("reversed") === true) fail("failed-precondition", "This payment cannot be reversed");
    const agrRef = db.doc(`agreements/${pay.get("agreementId")}`);
    const [agr, c] = await Promise.all([tx.get(agrRef), tx.get(counters)]);
    const a = agr.data() as AgreementData;
    if (!a || a.status === "RELEASED") fail("failed-precondition", "Agreement already released");
    const amount = pay.get("amount") as number;
    const p = progress(a, a.remainingBalance + amount);
    const baseStatus = a.status === "FULLY_PAID" ? "ACTIVE" : a.status;
    const rcN = (c.get("receipt") ?? 0) + 1;
    tx.set(counters, { receipt: rcN }, { merge: true });
    tx.set(db.collection("payments").doc(), {
      agreementId: agrRef.id, customerId: a.customerId, amount: -amount, method: pay.get("method"),
      reference: pay.get("receiptNo"), notes: `Reversal of ${pay.get("receiptNo")}`,
      receiptNo: `R-${String(rcN).padStart(6, "0")}`, previousBalance: a.remainingBalance, newBalance: p.remainingBalance,
      staffUid: actor.uid, staffName: actor.name, kind: "REVERSAL", reversalOf: paymentId, reversed: false,
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.update(payRef, { reversed: true, reversedBy: actor.uid, reversedAt: FieldValue.serverTimestamp() });
    tx.update(agrRef, { ...p, status: computeStatus({ status: baseStatus, ...p }) });
  });
  await audit(actor.uid, actor.name, "PAYMENT_REVERSAL", paymentId);
  return { ok: true };
});

// ---------------------------------------------------------------- enrollment

const TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export const createEnrollmentToken = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const agreementId = str(data(req).agreementId);
  const agr = await db.doc(`agreements/${agreementId}`).get();
  if (!agr.exists) fail("not-found", "Agreement not found");
  if (agr.get("deviceId")) fail("failed-precondition", "A device is already enrolled on this agreement");
  if (["RELEASED", "CANCELLED"].includes(agr.get("status"))) fail("failed-precondition", "Agreement is closed");

  // Revoke older unused codes for this agreement.
  const old = await db.collection("enrollmentTokens").where("agreementId", "==", agreementId).get();
  const batch = db.batch();
  old.docs.filter((t) => !t.get("used") && !t.get("revoked")).forEach((t) => batch.update(t.ref, { revoked: true }));
  await batch.commit();

  let token = "";
  for (let i = 0; i < 10; i++) token += TOKEN_ALPHABET[randomInt(TOKEN_ALPHABET.length)];
  const expiresAt = Date.now() + 30 * 60 * 1000;
  await db.doc(`enrollmentTokens/${token}`).set({
    agreementId, customerId: agr.get("customerId"), createdBy: actor.uid, createdByName: actor.name,
    createdAt: FieldValue.serverTimestamp(), expiresAt: Timestamp.fromMillis(expiresAt), used: false, revoked: false,
  });
  await audit(actor.uid, actor.name, "ENROLLMENT_TOKEN", agreementId);

  const prov = (await db.doc("appConfig/provisioning").get()).data() ?? {};
  let qrPayload: string | null = null;
  let warning: string | null = null;
  if (prov.apkUrl && prov.checksum) {
    qrPayload = JSON.stringify({
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": DEVICE_COMPONENT,
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": prov.apkUrl,
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": prov.checksum,
      "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
      "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": { qg_token: token },
    });
  } else {
    warning = "QR setup incomplete: add the Device APK URL and checksum in Settings. Until then, use the code manually.";
  }
  return { token, expiresAt, qrPayload, warning };
});

export const revokeEnrollmentToken = onCall(async (req) => {
  const actor = await requireStaff(req, ALL);
  const token = str(data(req).token, 20);
  const ref = db.doc(`enrollmentTokens/${token}`);
  const snap = await ref.get();
  if (snap.exists && !snap.get("used")) await ref.update({ revoked: true });
  await audit(actor.uid, actor.name, "ENROLLMENT_TOKEN_REVOKE", token);
  return { ok: true };
});

export const redeemEnrollment = onCall(async (req) => {
  if (!req.auth) fail("unauthenticated", "Device not authenticated");
  const uid = req.auth.uid;
  const d = data(req);
  const token = str(d.token, 20).toUpperCase();
  const info = (d.info ?? {}) as Record<string, unknown>;
  const consentVersion = str(d.consentVersion, 40);
  if (!token || !consentVersion) fail("invalid-argument", "Enrollment code and consent are required");
  const tokRef = db.doc(`enrollmentTokens/${token}`);
  const devRef = db.collection("devices").doc();

  const out = await db.runTransaction(async (tx) => {
    const tok = await tx.get(tokRef);
    if (!tok.exists) fail("not-found", "Invalid enrollment code");
    if (tok.get("used")) {
      if (tok.get("deviceUid") === uid) return { deviceId: tok.get("deviceId") as string, again: true, a: null as AgreementData | null, by: "" };
      fail("failed-precondition", "This code was already used");
    }
    if (tok.get("revoked")) fail("failed-precondition", "This code was revoked");
    if ((tok.get("expiresAt") as Ts).toMillis() < Date.now()) fail("failed-precondition", "This code has expired. Generate a new one.");
    const agrRef = db.doc(`agreements/${tok.get("agreementId")}`);
    const agr = await tx.get(agrRef);
    const a = agr.data() as AgreementData | undefined;
    if (!a || a.deviceId || ["RELEASED", "CANCELLED"].includes(a.status)) fail("failed-precondition", "Agreement is not available for enrollment");
    tx.set(devRef, {
      deviceUid: uid, agreementId: agrRef.id, customerId: a.customerId, customerName: a.customerName,
      brand: str(info.brand, 40), manufacturer: str(info.manufacturer, 40), model: str(info.model, 60),
      androidVersion: str(info.androidVersion, 20), sdk: Number(info.sdk) || 0, appVersion: str(info.appVersion, 20),
      isDeviceOwner: info.isDeviceOwner === true, imei: str(info.imei, 20), serial: str(info.serial, 40),
      managementStatus: "ACTIVE", lockState: "UNLOCKED", simAlert: false, simInfo: "",
      battery: 0, charging: false, capabilities: {},
      consent: { version: consentVersion, acceptedAt: FieldValue.serverTimestamp() },
      enrolledBy: tok.get("createdBy"), enrolledAt: FieldValue.serverTimestamp(), lastSeen: FieldValue.serverTimestamp(),
    });
    tx.update(tokRef, { used: true, usedAt: FieldValue.serverTimestamp(), deviceId: devRef.id, deviceUid: uid });
    tx.update(agrRef, { deviceId: devRef.id });
    return { deviceId: devRef.id, again: false, a, by: tok.get("createdBy") as string };
  });

  if (!out.again && out.a) {
    await audit(out.by, "device", "DEVICE_ENROLLED", out.deviceId, { agreementNo: out.a.agreementNo, consentVersion });
    await notifyAdmins("Enrollment completed", `${out.a.customerName} — ${str(info.brand, 40)} ${str(info.model, 60)}`);
  }
  return { deviceId: out.deviceId };
});

// ---------------------------------------------------------------- device sync & commands

export const deviceSync = onCall(async (req) => {
  const d = data(req);
  const deviceId = str(d.deviceId, 60);
  const dev = await requireDevice(req, deviceId);
  const s = (d.status ?? {}) as Record<string, unknown>;
  const caps: Record<string, string> = {};
  Object.entries((s.capabilities ?? {}) as Record<string, unknown>).slice(0, 20).forEach(([k, v]) => { caps[str(k, 40)] = str(v, 40); });

  const updates: Record<string, unknown> = {
    battery: Math.max(0, Math.min(100, Number(s.battery) || 0)),
    charging: s.charging === true,
    simInfo: str(s.simInfo, 80),
    isDeviceOwner: s.isDeviceOwner === true,
    lockState: s.lockState === "LOCKED" ? "LOCKED" : "UNLOCKED",
    appVersion: str(s.appVersion, 20),
    capabilities: caps,
    lastSeen: FieldValue.serverTimestamp(),
    offlineAlerted: false,
  };
  if (typeof s.fcmToken === "string" && s.fcmToken) updates.fcmToken = str(s.fcmToken, 500);
  if (s.simChanged === true) {
    updates.simAlert = true;
    updates.simAlertAt = FieldValue.serverTimestamp();
    await db.collection("deviceEvents").add({ deviceId, type: "SIM_CHANGE", simInfo: updates.simInfo, at: FieldValue.serverTimestamp() });
    await notifyAdmins("SIM change alert", `${dev.get("customerName")}: SIM changed/removed (${updates.simInfo})`);
  }
  await dev.ref.update(updates);

  const biz = await business();
  if (dev.get("managementStatus") === "RELEASED") {
    return { managementStatus: "RELEASED", commands: [], supportPhone: biz.supportPhone, businessName: biz.name };
  }

  const now = Date.now();
  const cmds = await db.collection("commands").where("deviceId", "==", deviceId).get();
  const batch = db.batch();
  const pending: Record<string, unknown>[] = [];
  cmds.docs.forEach((c) => {
    const status = c.get("status");
    if (status !== "PENDING" && status !== "DELIVERED") return;
    if ((c.get("expiresAt") as Ts).toMillis() < now) {
      batch.update(c.ref, { status: "EXPIRED" });
      return;
    }
    if (status === "PENDING") batch.update(c.ref, { status: "DELIVERED", receivedAt: FieldValue.serverTimestamp() });
    pending.push({
      id: c.id, action: c.get("action"), message: c.get("message") ?? "",
      createdAt: (c.get("createdAt") as Ts | undefined)?.toMillis() ?? 0,
    });
  });
  await batch.commit();

  let summary: Record<string, unknown> | null = null;
  const agr = await db.doc(`agreements/${dev.get("agreementId")}`).get();
  if (agr.exists) {
    const a = agr.data() as AgreementData;
    summary = {
      customerName: a.customerName, agreementNo: a.agreementNo, productName: a.productName,
      installmentCount: a.installmentCount, paidInstallments: a.paidInstallments,
      totalCollected: a.totalCollected, remainingBalance: a.remainingBalance, installmentAmount: a.installmentAmount,
      nextDueDate: a.nextDueDate ? a.nextDueDate.toMillis() : 0, status: a.status, dueAmount: dueAmount(a),
    };
  }
  return { managementStatus: "ACTIVE", commands: pending, summary, supportPhone: biz.supportPhone, businessName: biz.name };
});

export const ackCommand = onCall(async (req) => {
  const d = data(req);
  const deviceId = str(d.deviceId, 60);
  const dev = await requireDevice(req, deviceId);
  const cmdRef = db.doc(`commands/${str(d.commandId, 60)}`);
  const cmd = await cmdRef.get();
  if (!cmd.exists || cmd.get("deviceId") !== deviceId) fail("not-found", "Command not found");
  const status = cmd.get("status");
  if (status !== "PENDING" && status !== "DELIVERED") return { ok: true }; // idempotent
  const success = d.success === true;
  const action = cmd.get("action") as string;
  await cmdRef.update({
    status: success ? "EXECUTED" : "FAILED",
    executedAt: FieldValue.serverTimestamp(),
    result: str(d.result, 300),
  });

  if (success) {
    const agrRef = db.doc(`agreements/${dev.get("agreementId")}`);
    const agr = await agrRef.get();
    const a = agr.data() as AgreementData | undefined;
    if (action === "LOCK") {
      await dev.ref.update({ lockState: "LOCKED" });
      if (a && !["FULLY_PAID", "RELEASED", "CANCELLED"].includes(a.status)) await agrRef.update({ status: "TEMPORARILY_RESTRICTED" });
    } else if (action === "UNLOCK") {
      await dev.ref.update({ lockState: "UNLOCKED" });
      if (a && a.status === "TEMPORARILY_RESTRICTED") await agrRef.update({ status: computeStatus({ ...a, status: "ACTIVE" }) });
    } else if (action === "LOCATION") {
      const loc = (d.location ?? {}) as Record<string, unknown>;
      const lat = Number(loc.lat), lng = Number(loc.lng);
      if (Number.isFinite(lat) && Number.isFinite(lng)) {
        await dev.ref.update({
          lastLocation: {
            lat, lng, accuracy: Number(loc.accuracy) || 0,
            time: Timestamp.fromMillis(Number(loc.time) || Date.now()), lastKnown: loc.lastKnown === true,
          },
        });
      }
    } else if (action === "RELEASE") {
      await dev.ref.update({ managementStatus: "RELEASED", lockState: "UNLOCKED", releasedAt: FieldValue.serverTimestamp(), fcmToken: FieldValue.delete() });
      if (a) await agrRef.update({ status: "RELEASED" });
    }
  }
  await audit(cmd.get("createdBy"), cmd.get("createdByName"), `${action}_RESULT`, deviceId, { commandId: cmdRef.id }, success ? "SUCCESS" : "FAILED");
  if (action !== "MESSAGE") {
    await notifyAdmins(`${action} ${success ? "done" : "failed"}`, `${dev.get("customerName")}: ${str(d.result, 120)}`);
  }
  return { ok: true };
});

const COMMAND_TTL: Record<string, number> = {
  LOCK: 14 * DAY, UNLOCK: 14 * DAY, RELEASE: 30 * DAY, LOCATION: 3600 * 1000, MESSAGE: 3 * DAY,
};

export const sendCommand = onCall(async (req) => {
  const d = data(req);
  const action = str(d.action);
  if (!(action in COMMAND_TTL)) fail("invalid-argument", "Unknown action");
  const actor = await requireStaff(req, action === "MESSAGE" ? ALL : MGR);
  const deviceId = str(d.deviceId, 60);
  const dev = await db.doc(`devices/${deviceId}`).get();
  if (!dev.exists) fail("not-found", "Device not found");
  if (dev.get("managementStatus") === "RELEASED") fail("failed-precondition", "This device has been released");
  const message = str(d.message, 300);
  if (action === "MESSAGE" && !message) fail("invalid-argument", "Message is empty");
  if (action === "RELEASE") {
    const agr = await db.doc(`agreements/${dev.get("agreementId")}`).get();
    if (agr.get("status") !== "FULLY_PAID") fail("failed-precondition", "Release is allowed only after full payment");
  }

  // Rate limit: max 20 commands per staff member per minute.
  const rl = db.doc(`rateLimits/${actor.uid}`);
  await db.runTransaction(async (tx) => {
    const r = await tx.get(rl);
    const now = Date.now();
    const start = (r.get("windowStart") as number | undefined) ?? 0;
    const count = now - start > 60000 ? 0 : ((r.get("count") as number | undefined) ?? 0);
    if (count >= 20) fail("resource-exhausted", "Too many commands. Wait a minute.");
    tx.set(rl, { windowStart: count === 0 ? now : start, count: count + 1 });
  });

  // A newer LOCK/UNLOCK always supersedes older pending ones (no stale overrides).
  if (action === "LOCK" || action === "UNLOCK" || action === "RELEASE") {
    const old = await db.collection("commands").where("deviceId", "==", deviceId).get();
    const batch = db.batch();
    old.docs
      .filter((c) => ["LOCK", "UNLOCK"].includes(c.get("action")) && ["PENDING", "DELIVERED"].includes(c.get("status")))
      .forEach((c) => batch.update(c.ref, { status: "SUPERSEDED" }));
    await batch.commit();
  }

  const ref = db.collection("commands").doc();
  await ref.set({
    deviceId, action, message, status: "PENDING", nonce: randomBytes(16).toString("hex"),
    createdBy: actor.uid, createdByName: actor.name, createdAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + COMMAND_TTL[action]),
  });
  await pushToDevice(dev.get("fcmToken"), { type: "SYNC", commandId: ref.id });
  await audit(actor.uid, actor.name, `${action}_REQUEST`, deviceId, { commandId: ref.id });
  return { id: ref.id };
});

export const clearSimAlert = onCall(async (req) => {
  const actor = await requireStaff(req, MGR);
  const deviceId = str(data(req).deviceId, 60);
  await db.doc(`devices/${deviceId}`).update({ simAlert: false });
  await audit(actor.uid, actor.name, "SIM_ALERT_CLEAR", deviceId);
  return { ok: true };
});

// ---------------------------------------------------------------- daily job

export const dailyStatus = onSchedule({ schedule: "0 9 * * *", timeZone: "Asia/Karachi" }, async () => {
  const biz = await business();
  const snap = await db.collection("agreements")
    .where("status", "in", ["ACTIVE", "DUE_SOON", "DUE_TODAY", "OVERDUE", "TEMPORARILY_RESTRICTED"]).get();
  let overdue = 0;
  for (const doc of snap.docs) {
    const a = doc.data() as AgreementData;
    const status = computeStatus(a);
    const updates: Record<string, unknown> = {};
    if (status !== a.status) updates.status = status;
    if (status === "OVERDUE" || status === "TEMPORARILY_RESTRICTED") overdue++;

    if (a.deviceId && a.nextDueDate) {
      const dueMs = a.nextDueDate.toMillis();
      const amount = dueAmount(a);
      let key = "";
      let body = "";
      if (status === "DUE_SOON") {
        key = `${pkDay(dueMs)}:SOON`;
        body = `آپ کی قسط Rs ${amount} کی آخری تاریخ ${pkDateStr(dueMs)} ہے۔`;
      } else if (status === "DUE_TODAY") {
        key = `${pkDay(dueMs)}:TODAY`;
        body = `آج آپ کی قسط Rs ${amount} کی آخری تاریخ ہے۔`;
      } else if (status === "OVERDUE") {
        const late = pkDay(Date.now()) - pkDay(dueMs);
        key = `${pkDay(dueMs)}:LATE:${Math.floor(late / 7)}`;
        body = late <= a.graceDays
          ? `آپ کی قسط Rs ${amount} کی تاریخ گزر چکی ہے۔ رعایتی مدت ${a.graceDays - late} دن باقی ہے۔`
          : `آپ کی قسط Rs ${amount} کی تاریخ گزر چکی ہے۔ براہ کرم فوری ادائیگی کریں۔ رابطہ: ${biz.supportPhone}`;
      }
      if (key && a.lastReminderKey !== key) {
        const dev = await db.doc(`devices/${a.deviceId}`).get();
        if (dev.get("managementStatus") === "ACTIVE") {
          await pushToDevice(dev.get("fcmToken"), { type: "REMINDER", title: `Quick Guard Pro — ${biz.name}`, body });
        }
        updates.lastReminderKey = key;
      }
    }
    if (Object.keys(updates).length > 0) await doc.ref.update(updates);
  }

  const devs = await db.collection("devices").where("managementStatus", "==", "ACTIVE").get();
  const cutoff = Date.now() - 3 * DAY;
  const offline = devs.docs.filter((x) => !x.get("offlineAlerted") && ((x.get("lastSeen") as Ts | undefined)?.toMillis() ?? 0) < cutoff);
  for (const x of offline) await x.ref.update({ offlineAlerted: true });
  if (overdue > 0 || offline.length > 0) {
    await notifyAdmins("Daily summary", `${overdue} overdue agreement(s), ${offline.length} device(s) offline for 3+ days`);
  }
});
